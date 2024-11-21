package space.kscience.simulation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * @param lookaheadInterval an interval for generated events ahead of the last observed event.
 */
public class GeneratingTimeline<E : TimelineEvent>(
    private val generationScope: CoroutineScope,
    private val initialEvent: E,
    private val lookaheadInterval: Duration,
    private val generatorChain: suspend (E) -> E
) : Timeline<E>, AutoCloseable {

    // push to this channel to trigger event generation
    private val wakeupChannel = Channel<Unit>(onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private suspend fun kickGenerator() {
        wakeupChannel.send(Unit)
    }

    private val mutex = Mutex()

    private val history = ArrayDeque<E>()

    private val lastEvent = MutableSharedFlow<E>(replay = Int.MAX_VALUE)

    private val updateHistoryJob = generationScope.launch {
        lastEvent.onEach {
            mutex.withLock {
                history.add(it)
                //cleanup old events
                val threshold = observedTime ?: return@withLock
                while (history.isNotEmpty() && history.last().time > threshold) {
                    history.removeFirst()
                }

            }
        }
    }

    private val observers: MutableSet<TimelineObserver> = mutableSetOf()

    override val time: Instant
        get() = history.lastOrNull()?.time ?: initialEvent.time

    override val observedTime: Instant?
        get() = observers.minOfNotNullOrNull { it.time }

    override fun flowUnobservedEvents(): Flow<E> = flow {
        history.forEach { e ->
            emit(e)
        }
        emitAll(lastEvent)
    }

    override suspend fun advance(toTime: Instant) {
        observers.forEach {
            it.collect(toTime)
        }
    }

    private var generatorJob: Job = launchGenerator(initialEvent)

    private fun launchGenerator(event: E): Job = generationScope.launch {
        kickGenerator()
        var currentEvent = event
        // for each wakeup generate all events in lookaheadInterval
        for (u in wakeupChannel) {
            while (currentEvent.time < (observedTime ?: event.time) + lookaheadInterval) {
                val nextEvent = generatorChain(currentEvent)
                lastEvent.emit(nextEvent)
                currentEvent = nextEvent
            }
        }
    }


    public suspend fun interrupt(newStart: E) {
        check(newStart.time > (observedTime ?: Instant.DISTANT_FUTURE)) {
            "Can't interrupt generating timeline after observed event"
        }
        mutex.withLock {
            while (history.isNotEmpty() && history.last().time > newStart.time) {
                history.removeLast()
            }
            generatorJob.cancel()
            generatorJob = launchGenerator(newStart)

        }
        kickGenerator()
    }

    override fun close() {
        updateHistoryJob.cancel()
        generatorJob.cancel()
    }

    override suspend fun observe(collector: suspend Flow<E>.() -> Unit): TimelineObserver {
        val observer = object : TimelineObserver {
            override var time: Instant = this@GeneratingTimeline.time

            override suspend fun collect(upTo: Instant) {
                flowUnobservedEvents().takeWhile {
                    it.time <= upTo
                }.onEach {
                    time = it.time
                    kickGenerator()
                }.collector()
            }

            override fun close() {
                observers.remove(this)
                if(observers.isEmpty()){
                    this@GeneratingTimeline.close()
                }
            }

        }
        observers.add(observer)
        return observer
    }
}