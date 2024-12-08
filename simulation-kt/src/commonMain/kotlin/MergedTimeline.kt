package space.kscience.simulation

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


public class MergedTimeline<E : TimelineEvent>(
    private val timelines: List<Timeline<E>>,
    coroutineContext: CoroutineContext = EmptyCoroutineContext
) : Timeline<E> {

    protected val timelineScope: CoroutineScope = CoroutineScope(
        coroutineContext +
                SupervisorJob(coroutineContext[Job]) +
                CoroutineExceptionHandler{ _, throwable -> throwable.printStackTrace() } +
                CoroutineName("MergedTimeline")
    )

    override val time: StateFlow<Instant> = combine(timelines.map { it.time }){ array->
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
                    time.emit(it.time)
                }.collector()
            }

            private val mutex = Mutex()

            override suspend fun collect(upTo: Instant) = mutex.withLock{
                timelineObservers.forEach {
                    it.collect(upTo)
                }
                buffer.sortedBy { it.time }.forEach {
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