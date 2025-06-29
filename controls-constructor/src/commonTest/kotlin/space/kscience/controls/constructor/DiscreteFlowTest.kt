package space.kscience.controls.constructor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import space.kscience.controls.constructor.models.flow.DiscreteFlowModel
import space.kscience.controls.constructor.models.flow.registerConsumer
import space.kscience.controls.constructor.models.flow.registerProducer
import space.kscience.controls.constructor.models.flow.runSimulation
import space.kscience.controls.constructor.units.Kilograms
import space.kscience.controls.constructor.units.NV
import space.kscience.controls.time.coroutineDispatcher
import space.kscience.controls.time.withVirtualTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DiscreteFlowTest {

    val epoch = Instant.fromEpochMilliseconds(0L)

    val context = Context("test") {
        withVirtualTime(epoch)
    }

    @Test
    fun pipe() = runTest(timeout = 200.milliseconds) {

        object : ModelConstructor(context), DiscreteFlowModel {
            override val name: Name = "test".asName()

            val production = MutableDeviceState(NV<Kilograms>(4.0))
            val consumation = MutableDeviceState(NV<Kilograms>(1.0))

            val consumer = registerConsumer(consumation)
            val producer = registerProducer(production, consumer, 0.05.seconds)
        }.runSimulation {

            producer.production.valueFlow.onEach {
                println("production: $it (${clock.now() - epoch})")
            }.launchIn(backgroundScope)

            consumer.consumation.valueFlow.onEach {
                println("consumation: $it (${clock.now() - epoch})")
            }.launchIn(backgroundScope)


            delay(2.seconds)

            assertEquals(1.0, producer.production.value.value, 5e-2)
            assertEquals(1.0, consumer.consumation.value.value, 5e-2)
        }

    }


    @Test
    fun join() = runTest {

        val a = MutableDeviceState(NV<Kilograms>(1.0))
        val b = MutableDeviceState(NV<Kilograms>(2.0))
        val c = MutableDeviceState(NV<Kilograms>(3.0))
        val ab = MutableDeviceState(NV<Kilograms>(Double.POSITIVE_INFINITY))
        val abc = MutableDeviceState(NV<Kilograms>(8.0))

        val model = object : ModelConstructor(context), DiscreteFlowModel {
            val joinABC = registerConsumer(abc)
            val c = registerProducer(c, joinABC)
            val joinAB = registerConsumer(ab, joinABC)
            val b = registerProducer(b, joinAB)
            val a = registerProducer(a, joinAB)
        }

        withContext(context.coroutineDispatcher) {

            delay(2.seconds)

            assertEquals(3.0, model.joinAB.consumation.value.value, 0.1)
            assertEquals(6.0, model.joinABC.consumation.value.value, 0.1)
            assertEquals(3.0, model.c.production.value.value, 0.1)
            assertEquals(2.0, model.b.production.value.value, 0.1)
            assertEquals(1.0, model.a.production.value.value, 0.1)
        }
    }
}