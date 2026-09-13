package space.kscience.simulation

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/** A producer with shared unread history and independently advancing observers. */
public abstract class ProducerTimeline<E : Any>(
    protected var startTime: Instant,
    private val timeOf: E.() -> Instant,
    coroutineContext: CoroutineContext,
    bufferSize: Int = Channel.UNLIMITED,
) : Timeline<E>, AutoCloseable {
    private val ownerJob = SupervisorJob(coroutineContext[Job])
    protected val timelineScope: CoroutineScope = CoroutineScope(
        coroutineContext + ownerJob + CoroutineName("Timeline[${hashCode().toString(16)}]")
    )
    internal val state = TimelineState(startTime, timeOf, bufferSize)
    private val observers = MutableStateFlow<List<Observer>>(emptyList())

    init {
        ownerJob.invokeOnCompletion { state.close() }
    }

    override fun timeOf(event: E): Instant = event.timeOf()
    override val time: StateFlow<Instant> get() = state.time

    /** A cold source collected once per generation; null denotes direct publication. */
    protected open fun events(): Flow<E>? = null

    private val source = timelineScope.launch(start = CoroutineStart.LAZY) {
        state.generations.collectLatest { generation ->
            try {
                if (!state.awaitGeneration(generation)) return@collectLatest
                val sourceFlow = events() ?: return@collectLatest
                sourceFlow.collect { state.publish(it, generation, generated = true) }
                state.end(generation)
            } catch (error: CancellationException) {
                withContext(NonCancellable) { state.fail(error, generation) }
                throw error
            } catch (error: Throwable) {
                state.fail(error, generation)
            }
        }
    }

    /**
     * Request the observers registered at entry concurrently. Failure cancels the other requests,
     * not their registrations. With no observers this is a no-op.
     */
    override suspend fun advance(toTime: Instant): Unit = coroutineScope {
        observers.value.filterNot { it.reader.closed.value }.forEach { observer -> launch { observer.collect(toTime) } }
    }

    /** The collector runs outside the timeline lock. Its completion or failure closes the observer. */
    override suspend fun observe(collector: suspend Flow<E>.() -> Unit): TimelineObserver {
        val caller = currentCoroutineContext()
        val observer = Observer(state.register())
        observers.update { it + observer }
        val handle = caller[Job]?.invokeOnCompletion { cause -> if (cause != null) observer.close() }
        observer.job = timelineScope.launch(caller.minusKey(Job), start = CoroutineStart.UNDISPATCHED) {
            var failure: Throwable? = null
            try {
                flow {
                    while (currentCoroutineContext().isActive) {
                        when (val step = state.next(observer.reader)) {
                            is TimelineState.Step.Event -> emit(step.value)
                            is TimelineState.Step.Completed -> {
                                if (step.failure == null) step.request.result.complete(Unit)
                                else step.request.result.completeExceptionally(step.failure)
                            }
                            is TimelineState.Step.Wait -> state.waitForChange(step.version)
                        }
                    }
                }.collector()
            } catch (error: Throwable) {
                failure = error
            } finally {
                handle?.dispose()
                withContext(NonCancellable) { state.unregister(observer.reader, failure) }
                observers.update { it - observer }
            }
        }
        if (observer.reader.closed.value) observer.job?.cancel()
        return observer
    }

    private inner class Observer(val reader: TimelineState.Reader) : TimelineObserver {
        var job: Job? = null
        private val requestMutex = Mutex()
        override val time: StateFlow<Instant> get() = reader.time

        override suspend fun collect(upTo: Instant): Unit = requestMutex.withLock {
            val request = state.startRequest(reader, upTo)
            try {
                source.start()
                request.result.await()
            } finally {
                withContext(NonCancellable) { state.cancelRequest(reader, request) }
            }
        }

        override fun close() {
            state.close(reader)
            job?.cancel()
        }
    }

    override fun close() {
        state.close()
        ownerJob.cancel()
    }
}
