package space.kscience.simulation

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext

/**
 * A timeline that could be forked. The events from the fork appear in parent timeline events, but not vise versa.
 */
public interface ForkingTimeline<E : Any> : CollectingTimeline<E> {
    public suspend fun fork(): ForkingTimeline<E>
}

public class TreeTimeline<E : Any>(
    private val startTime: Instant,
    private val timeOf: E.() -> Instant,
    coroutineContext: CoroutineContext,
) : ForkingTimeline<E>, AutoCloseable {

    private val timelineScope: CoroutineScope = CoroutineScope(
        coroutineContext +
                SupervisorJob(coroutineContext[Job]) +
                CoroutineExceptionHandler { _, throwable -> throwable.printStackTrace() } +
                CoroutineName("TreeTimeline[${hashCode().toString(16)}]")
    )

    override fun timeOf(event: E): Instant = timeOf(event)

    private val _time = MutableStateFlow<Instant>(startTime)

    override val time: StateFlow<Instant> get() = _time

    override suspend fun advance(toTime: Instant) {
        coroutineScope {
            observers.forEach {
                launch {
                    it.collect(toTime)
                }
            }
        }
    }

    private val mutex = Mutex()

    private val buffer = mutableListOf<E>()

    private val branches: MutableSet<TimelineObserver> = mutableSetOf()

    private val events = MutableSharedFlow<E>(1)

    override val lastEvent: E? get() = events.replayCache.lastOrNull()

    override suspend fun emit(value: E) {
        mutex.withLock {
            buffer.add(value)
        }
    }

    private val observers: MutableSet<TimelineObserver> = mutableSetOf()

    /**
     * Update time on this channel event
     */
    private val feedbackChannel = Channel<Unit>(onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override suspend fun observe(collector: suspend Flow<E>.() -> Unit): TimelineObserver {
        val context = currentCoroutineContext()
        val observer = object : TimelineObserver {
            // observed time
            override val time = MutableStateFlow(startTime)

            private val channel = Channel<E>()

            private val collectJob = timelineScope.launch(context) {
                channel.consumeAsFlow().onEach {
                    time.emit(timeOf(it))
                    feedbackChannel.send(Unit)
                }.collector()
            }

            private val mutex = Mutex()

            override suspend fun collect(upTo: Instant) = mutex.withLock {
                require(upTo >= time.value) { "Requested time $upTo is lower than observed ${time.value}" }
                TODO("Not yet implemented")
//                events().takeWhile {
//                    timeOf(it) <= upTo
//                }.collect {
//                    channel.send(it)
//                }
            }

            override fun close() {
                collectJob.cancel()
                observers.remove(this)
            }

        }
        observers.add(observer)
        return observer
    }

    override suspend fun fork(): TreeTimeline<E> {
        val theFork = TreeTimeline(time.value, timeOf, timelineScope.coroutineContext)
        branches.add(theFork.observeEach {
            emit(it)
        })
        return theFork
    }

    override fun close() {
        observers.forEach { it.close() }
        branches.forEach { it.close() }
        timelineScope.cancel()
    }
}