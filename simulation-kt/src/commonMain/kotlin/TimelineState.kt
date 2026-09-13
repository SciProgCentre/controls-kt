package space.kscience.simulation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Instant

/** Storage and cursors shared by a producer and its readers. */
internal class TimelineState<E : Any>(
    private val baseline: Instant,
    private val timeOf: (E) -> Instant,
    private val capacity: Int,
) {
    init {
        require(capacity >= 0 || capacity == Channel.UNLIMITED) { "Unsupported timeline buffer size: $capacity" }
    }

    internal class Request(val upTo: Instant) {
        val result = CompletableDeferred<Unit>()
    }

    internal class Reader(var cursor: Long, initialTime: Instant) {
        val time = MutableStateFlow(initialTime)
        val closed = MutableStateFlow(false)
        var deliveredTime = initialTime
        var request: Request? = null
    }

    internal sealed interface Step<out E> {
        class Event<E>(val value: E, val time: Instant) : Step<E>
        class Completed(val request: Request, val failure: Throwable? = null) : Step<Nothing>
        class Wait(val version: Long) : Step<Nothing>
    }

    private class Entry<E>(val id: Long, val value: E, val time: Instant, val generation: Long) {
        var recipients: MutableSet<Reader>? = null
    }

    private class LastValue<E>(val event: E?)

    private val mutex = Mutex()
    private val publication = Mutex()
    private val closed = MutableStateFlow(false)
    private val version = MutableStateFlow(0L)
    val changes: StateFlow<Long> get() = version
    val generations = MutableStateFlow(0L)
    val time = MutableStateFlow(baseline)
    private val readers = mutableSetOf<Reader>()
    private val entries = ArrayDeque<Entry<E>>()
    private var pending: Entry<E>? = null
    private var nextId = 0L
    private var deliveredId = -1L
    private var deliveredTime = baseline
    private var completedThrough: Instant? = null
    private var sealedThrough: Instant? = null
    private var finished = false
    private var failure: Throwable? = null
    private var generation = 0L
    private var originTime = baseline
    private var originValue: E? = null
    private var runningOrigin: E? = null
    private var lookahead: Duration? = null
    private var acceptedTime = baseline
    private val lastValue = MutableStateFlow(LastValue<E>(null))
    val lastEvent: E? get() = lastValue.value.event

    fun initializeOrigin(origin: E, lookahead: Duration) {
        require(lookahead >= Duration.ZERO) { "Lookahead must not be negative" }
        originValue = origin
        this.lookahead = lookahead
        lastValue.value = LastValue(origin)
    }

    private fun signal() { version.update { it + 1 } }

    private suspend fun <T> locked(notify: Boolean = true, action: () -> T): T {
        try {
            return mutex.withLock(action = action)
        } finally {
            if (notify) signal()
            if (closed.value) withContext(NonCancellable) { mutex.withLock { clear() } }
        }
    }

    private fun checkOpen() = check(!closed.value) { "Timeline is closed" }

    suspend fun waitForChange(previous: Long) {
        version.first { it != previous || closed.value }
        if (closed.value) throw CancellationException("Timeline is closed")
    }

    suspend fun origin(): E = locked(notify = false) { checkNotNull(runningOrigin) }

    suspend fun awaitGeneration(epoch: Long): Boolean {
        while (true) {
            val previous = version.value
            val ready = locked(notify = false) {
                checkOpen()
                when {
                    epoch != generation -> false
                    readers.any { reader ->
                        !reader.closed.value && reader.request?.upTo?.let { upTo ->
                            upTo >= originTime && upTo >= deliveredTime &&
                                completedThrough?.let { upTo <= it } != true
                        } == true
                    } -> { runningOrigin = originValue; true }
                    else -> null
                }
            }
            if (ready != null) return ready
            waitForChange(previous)
        }
    }

    suspend fun register(): Reader = locked {
        checkOpen()
        Reader(deliveredId + 1, deliveredTime).also { readers.add(it) }
    }

    suspend fun startRequest(reader: Reader, upTo: Instant): Request = locked {
        checkOpen()
        check(!reader.closed.value) { "Timeline observer is closed" }
        require(upTo >= reader.deliveredTime) { "Cannot collect backwards from ${reader.deliveredTime} to $upTo" }
        Request(upTo).also { reader.request = it }
    }

    suspend fun cancelRequest(reader: Reader, request: Request) {
        val delivered = locked {
            if (reader.request === request) reader.request = null
            reader.deliveredTime
        }
        reader.time.update { maxOf(it, delivered) }
    }

    fun close(reader: Reader) {
        reader.closed.value = true
        signal()
    }

    suspend fun unregister(reader: Reader, cause: Throwable?) {
        val request = locked {
            reader.closed.value = true
            readers.remove(reader)
            reader.request.also { reader.request = null; reclaim() }
        }
        request?.result?.completeExceptionally(cause ?: CancellationException("Timeline observer is closed"))
    }

    private fun reclaim() {
        var firstUnread = deliveredId + 1
        for (reader in readers) {
            if (!reader.closed.value) firstUnread = minOf(firstUnread, reader.cursor)
        }
        while (entries.firstOrNull()?.id?.let { it < firstUnread } == true) entries.removeFirst()
        pending?.let { event ->
            event.recipients?.removeAll { it.closed.value || it.cursor > event.id }
            if (event.recipients?.isEmpty() == true) pending = null
        }
    }

    private fun entryAtOrAfter(cursor: Long): Entry<E>? {
        val first = entries.firstOrNull() ?: return null
        if (first.id >= cursor) return first
        if (entries.last().id < cursor) return null
        val index = entries.binarySearch { it.id.compareTo(cursor) }
        return entries.getOrNull(if (index >= 0) index else -(index + 1))
    }

    private fun validate(event: Entry<E>) {
        checkOpen()
        if (event.generation != generation) throw CancellationException("Timeline generation changed")
        check(!finished) { "Timeline source is finished" }
        check(event.time >= originTime && event.time >= acceptedTime) {
            "Event time ${event.time} precedes the accepted time $acceptedTime or origin $originTime"
        }
        check(completedThrough?.let { event.time <= it } != true) {
            "Event time ${event.time} is inside the completed interval $completedThrough"
        }
        check(sealedThrough?.let { event.time <= it } != true) {
            "Event time ${event.time} is inside the sealed interval $sealedThrough"
        }
    }

    private fun accept(event: Entry<E>) { acceptedTime = event.time; lastValue.value = LastValue(event.value) }

    private fun needed(event: Entry<E>, generated: Boolean): Boolean {
        if (!generated || lookahead == null) return true
        return readers.any { reader ->
            !reader.closed.value && reader.request?.upTo?.let { event.time <= it } == true
        } || event.time <= deliveredTime + lookahead!!
    }

    suspend fun publish(value: E, generation: Long? = null, generated: Boolean = false) {
        val eventTime = timeOf(value)
        publication.withLock {
            var event: Entry<E>? = null
            try {
                while (true) {
                    val previous = version.value
                    var changed = false
                    val done = locked(notify = false) {
                        checkOpen()
                        reclaim()
                        val current = event
                        if (current != null && current.id <= deliveredId && pending !== current) true
                        else if (pending != null && pending !== current) false
                        else {
                            val candidate = current ?: Entry(nextId++, value, eventTime, generation ?: this.generation)
                                .also { validate(it); pending = it; event = it; changed = true }
                            if (candidate.recipients == null) validate(candidate)
                            if (capacity != 0 && needed(candidate, generated) && entries.size < capacity) {
                                entries.addLast(candidate)
                                pending = null
                                accept(candidate)
                                changed = true
                                true
                            } else false
                        }
                    }
                    if (changed) signal()
                    if (done) return
                    waitForChange(previous)
                }
            } finally {
                withContext(NonCancellable) {
                    locked {
                        if (pending === event && event?.recipients == null) pending = null
                        reclaim()
                    }
                }
            }
        }
    }

    suspend fun next(reader: Reader): Step<E> {
        val previous = version.value
        val step = locked(notify = false) {
            checkOpen()
            if (reader.closed.value) throw CancellationException("Timeline observer is closed")
            val request = reader.request ?: return@locked Step.Wait(previous)
            reclaim()
            val event = entryAtOrAfter(reader.cursor)
                ?: pending?.takeIf { capacity == 0 && it.id >= reader.cursor }
            if (event != null && event.time <= request.upTo) {
                if (event.recipients == null && event === pending) {
                    validate(event)
                    event.recipients = readers.filterNot { it.closed.value }.toMutableSet()
                    accept(event)
                }
                reader.cursor = event.id + 1
                reader.deliveredTime = event.time
                deliveredId = maxOf(deliveredId, event.id)
                deliveredTime = maxOf(deliveredTime, event.time)
                event.recipients?.remove(reader)
                reclaim()
                Step.Event(event.value, event.time)
            } else {
                val nextTime = event?.time ?: pending?.time
                val proven = completedThrough?.let { request.upTo <= it } == true ||
                    request.upTo < deliveredTime || request.upTo < originTime ||
                    nextTime?.let { request.upTo < it } == true || finished ||
                    sealedThrough?.let { request.upTo <= it } == true
                when {
                    proven -> {
                        completedThrough = maxOf(completedThrough ?: request.upTo, request.upTo)
                        reader.request = null
                        Step.Completed(request)
                    }
                    failure != null -> { reader.request = null; Step.Completed(request, failure) }
                    else -> Step.Wait(previous)
                }
            }
        }
        if (step !is Step.Wait) signal()
        if (step is Step.Event) {
            time.update { maxOf(it, step.time) }
            reader.time.update { maxOf(it, step.time) }
        }
        return step
    }

    suspend fun completeThrough(upTo: Instant) = locked {
        checkOpen()
        sealedThrough = maxOf(sealedThrough ?: upTo, upTo)
        if (pending?.let { it.recipients == null && it.time <= upTo } == true) pending = null
    }

    suspend fun finish() = locked {
        checkOpen()
        finished = true
        if (pending?.recipients == null) pending = null
    }
    suspend fun end(generation: Long) = locked {
        if (!closed.value && this.generation == generation) finished = true
    }
    suspend fun fail(error: Throwable, generation: Long? = null) = locked {
        if (!closed.value && (generation == null || generation == this.generation)) failure = error
    }

    suspend fun restart(origin: E) {
        val newTime = timeOf(origin)
        val updated = locked {
            checkOpen()
            check(newTime >= baseline && newTime >= deliveredTime && completedThrough?.let { newTime < it } != true) {
                "Cannot interrupt at $newTime: observed through $deliveredTime, completed through $completedThrough"
            }
            while (entries.lastOrNull()?.id?.let { it > deliveredId } == true) entries.removeLast()
            if (pending?.recipients == null) pending = null
            originTime = newTime
            originValue = origin
            acceptedTime = newTime
            lastValue.value = LastValue(origin)
            finished = false
            failure = null
            generation += 1
            generation
        }
        generations.update { maxOf(it, updated) }
    }

    private fun clear() {
        entries.clear()
        pending = null
        originValue = null
        runningOrigin = null
        lastValue.value = LastValue(null)
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        signal()
        if (mutex.tryLock()) {
            try { clear() } finally { mutex.unlock() }
        }
    }
}
