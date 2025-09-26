package space.kscience.simulation

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Instant


public class MergedTimeline<E : Any>(
    private val timelines: List<Timeline<E>>,
    private val timeOf: E.() -> Instant,
    coroutineContext: CoroutineContext = EmptyCoroutineContext
) : Timeline<E> {

    private val timelineScope: CoroutineScope = CoroutineScope(
        coroutineContext +
                SupervisorJob(coroutineContext[Job]) +
                CoroutineExceptionHandler { _, throwable -> throwable.printStackTrace() } +
                CoroutineName("MergedTimeline")
    )

    override fun timeOf(event: E): Instant = event.timeOf()

    override val time: StateFlow<Instant> = combine(timelines.map { it.time }) { array ->
        array.max()
    }.stateIn(timelineScope, SharingStarted.Lazily, timelines.maxOf { it.time.value })

    override suspend fun advance(toTime: Instant) {
        observers.forEach {
            it.collect(toTime)
        }
    }

    private val observers: MutableSet<TimelineObserver> = mutableSetOf()

    override suspend fun observe(collector: suspend Flow<E>.() -> Unit): TimelineObserver {
        val context = currentCoroutineContext()
        val buffer = mutableListOf<E>()

        val timelineObservers = timelines.map {
            it.observeEach { event ->
                buffer.add(event)
            }
        }

        val observer = object : TimelineObserver {

            private val channel = Channel<E>()

            override val time = MutableStateFlow(this@MergedTimeline.time.value)

            private val collectJob = timelineScope.launch(context) {
                channel.consumeAsFlow().onEach {
                    time.emit(timeOf(it))
                }.collector()
            }

            private val mutex = Mutex()

            override suspend fun collect(upTo: Instant) = mutex.withLock {
                timelineObservers.forEach {
                    it.collect(upTo)
                }
                buffer.sortedBy { timeOf(it) }.forEach {
                    channel.send(it)
                    buffer.remove(it)
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

}