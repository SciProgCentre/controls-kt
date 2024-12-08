package space.kscience.simulation

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext

public abstract class ProducerTimeline<E : TimelineEvent>(
    protected var startTime: Instant,
    coroutineContext: CoroutineContext
) : Timeline<E>, AutoCloseable {

    protected val timelineScope: CoroutineScope = CoroutineScope(
        coroutineContext +
        SupervisorJob(coroutineContext[Job]) +
        CoroutineExceptionHandler{ _, throwable -> throwable.printStackTrace() } +
        CoroutineName("Timeline")
    )

    private val observers: MutableSet<TimelineObserver> = mutableSetOf()

    private val feedbackChannel = Channel<Unit>(onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val time: StateFlow<Instant> = feedbackChannel.consumeAsFlow().map {
        maxOf(startTime,observers.maxOfOrNull { it.time.value } ?: startTime)
    }.stateIn(timelineScope, SharingStarted.Lazily, startTime)

    override suspend fun advance(toTime: Instant) {
        observers.forEach {
            it.collect(toTime)
        }
    }

    /**
     * Flow unobserved events starting at [time]. The flow could be interrupted if timeline changes
     */
    protected abstract fun events(): Flow<E>

    override suspend fun observe(collector: suspend Flow<E>.() -> Unit): TimelineObserver {
        val context = currentCoroutineContext()
        val observer = object : TimelineObserver {
            // observed time
            override val time = MutableStateFlow(startTime)

            private val channel = Channel<E>()

            private val collectJob = timelineScope.launch(context) {
                channel.consumeAsFlow().onEach {
                    time.emit(it.time)
                    feedbackChannel.send(Unit)
                }.collector()
            }

            private val mutex = Mutex()

            override suspend fun collect(upTo: Instant) = mutex.withLock {
                require(upTo >= time.value) { "Requested time $upTo is lower than observed ${time.value}" }
                events().takeWhile {
                    it.time <= upTo
                }.collect {
                    channel.send(it)
                }
            }

            override fun close() {
                collectJob.cancel()
                observers.remove(this)
            }

        }
        observers.add(observer)
        return observer
    }

    override fun close() {
        observers.forEach { it.close() }
        timelineScope.cancel()
    }
}