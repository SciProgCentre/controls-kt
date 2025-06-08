package space.kscience.controls.constructor

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import space.kscience.controls.constructor.models.flow.DiscreateProducer
import space.kscience.controls.constructor.models.flow.DiscreteConsumer
import space.kscience.controls.constructor.models.flow.DiscreteFlowModel
import space.kscience.controls.constructor.units.Kilograms
import space.kscience.controls.constructor.units.NV
import space.kscience.controls.time.coroutineDispatcher
import space.kscience.dataforge.context.Context
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class DiscreteFlowTest {

    val context = Context("test") {
//        withVirtualTime()
    }

    @Test
    fun pipe() = runTest {
        val model = object : ModelConstructor(context), DiscreteFlowModel {
            val production = MutableDeviceState(NV<Kilograms>(4.0))
            val consumation = MutableDeviceState(NV<Kilograms>(1.0))
            val consumer = DiscreteConsumer(this, consumation)
            val producer = DiscreateProducer(this, production, consumer)
        }

        withContext(context.coroutineDispatcher) {
            delay(2.seconds)

            assertEquals(1.0, model.producer.production.value.value, 1e-1)
            assertEquals(1.0, model.consumer.consumation.value.value, 1e-1)
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
            val joinABC = DiscreteConsumer<Kilograms>(this, abc)
            val c = DiscreateProducer(this, c, joinABC)
            val joinAB = DiscreteConsumer<Kilograms>(this, ab, joinABC)
            val b = DiscreateProducer(this, b, joinAB)
            val a = DiscreateProducer(this, a, joinAB)
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