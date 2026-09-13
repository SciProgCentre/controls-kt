package space.kscience.controls.constructor

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.time.ValueWithTime
import space.kscience.controls.time.clock
import space.kscience.controls.time.deviceDispatcher
import space.kscience.controls.time.withVirtualTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.double
import space.kscience.simulation.GeneratingTimeline
import space.kscience.simulation.ProducerTimeline
import space.kscience.simulation.SharedTimeline
import space.kscience.simulation.observeEach
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TimelineDeviceTest {
    private data class Sample(val value: Double, val time: Instant)

    private class Samples(
        startTime: Instant,
        private val samples: List<Sample>,
        coroutineContext: CoroutineContext,
    ) : ProducerTimeline<Sample>(startTime, { time }, coroutineContext, bufferSize = 1) {
        override fun events(): Flow<Sample> = samples.asFlow()
    }

    private class Sensor(context: Context, start: Instant) : DeviceConstructor(context) {
        val samples = MutableStateFlow(ValueWithTime(0.0, start))
        val reading by property(MetaConverter.double, object : ValueState<Double> {
            override val valueWithTime: ValueWithTime<Double> get() = samples.value
            override fun subscribeWithTime(): Flow<ValueWithTime<Double>> = samples
            override fun toString(): String = "SensorState($valueWithTime)"
        })
    }

    private class Display(context: Context) : DeviceConstructor(context), BoundStateHolder {
        private val input = LateBindValueState<Meta>(Meta(0.0))

        init {
            registerProperty("reading", MetaConverter.meta, input)
        }

        override fun bind(state: ValueState<Meta>, inputName: String) {
            require(inputName == "sensor")
            input.bind(state)
        }
    }

    @Test
    fun testTimelineUpdatesBoundDeviceProperties() = runTest(timeout = 10.seconds) {
        val start = Instant.fromEpochSeconds(1_000)
        val context = Context("timeline-device") {
            coroutineContext(backgroundScope.coroutineContext)
            withVirtualTime(start + 100.seconds)
        }
        val samples = listOf(10.0, 15.0, 12.0).mapIndexed { index, value ->
            Sample(value, start + (index + 1).seconds)
        }
        val timeline = Samples(start, samples, context.coroutineContext + context.deviceDispatcher)
        val sensor = Sensor(context, start)
        val display = Display(context)
        val sensorMessages = Channel<PropertyChangedMessage>(Channel.UNLIMITED)
        val displayMessages = Channel<PropertyChangedMessage>(Channel.UNLIMITED)
        val messageJobs = listOf(sensor to sensorMessages, display to displayMessages).map { (device, messages) ->
            device.launch(start = CoroutineStart.UNDISPATCHED) {
                device.messageFlow.filterIsInstance<PropertyChangedMessage>()
                    .filter { it.property == "reading" && it.time > start }
                    .collect { messages.send(it) }
            }
        }

        try {
            display.bind(sensor.propertyAsState("reading", MetaConverter.meta), "sensor")
            sensor.start()
            display.start()
            withContext(context.deviceDispatcher) {
                val received = mutableListOf<Sample>()
                val observer = timeline.observeEach { sample ->
                    assertNotEquals(sample.time, context.clock.now())
                    sensor.samples.value = ValueWithTime(sample.value, sample.time)
                    for (messages in listOf(sensorMessages, displayMessages)) {
                        val message = messages.receive()
                        assertEquals(sample.value, message.value.double)
                        assertEquals(sample.time, message.time)
                    }
                    received += sample
                }
                try {
                    observer.collect(start + 2.seconds)
                    assertEquals(samples.take(2), received)
                    observer.collect(start + 3.seconds)
                    assertEquals(samples, received)
                    observer.collect(start + 4.seconds)
                    assertEquals(samples, received)
                    assertEquals(samples.last().time, observer.time.value)
                    assertEquals(samples.last().value, display.readProperty("reading").double)
                    assertEquals(samples.last().time, display.propertyAsState("reading", MetaConverter.meta).time)
                } finally {
                    observer.close()
                }
            }
        } finally {
            timeline.close()
            display.stop()
            sensor.stop()
            messageJobs.forEach { it.join() }
            context.close()
            sensorMessages.close()
            displayMessages.close()
        }
        assertTrue(messageJobs.all { it.isCancelled })
    }

    @Test
    fun testPublicTimelineConstructors() = runTest(timeout = 10.seconds) {
        val start = Instant.fromEpochSeconds(1_000)
        val origin = Sample(0.0, start)
        val sample = Sample(1.0, start + 1.seconds)
        val owner = Job(backgroundScope.coroutineContext[Job])
        val constructorContext = backgroundScope.coroutineContext + owner
        val named = GeneratingTimeline(
            origin = origin,
            lookaheadInterval = Duration.ZERO,
            timeOf = Sample::time,
            coroutineContext = constructorContext,
            bufferSize = 1,
            generator = { emit(sample) },
        )
        val trailing = GeneratingTimeline(origin, Duration.ZERO, Sample::time, constructorContext) {
            emit(sample)
        }
        val shared = SharedTimeline(start, Sample::time, 1, constructorContext)
        try {
            shared.emit(sample)
            shared.finish()
            for (timeline in listOf(named, trailing, shared)) {
                val received = mutableListOf<Sample>()
                val observer = timeline.observeEach { received += it }
                try {
                    observer.collect(sample.time)
                    assertEquals(listOf(sample), received)
                } finally {
                    observer.close()
                }
            }
        } finally {
            named.close()
            trailing.close()
            shared.close()
            owner.cancelAndJoin()
        }
    }
}
