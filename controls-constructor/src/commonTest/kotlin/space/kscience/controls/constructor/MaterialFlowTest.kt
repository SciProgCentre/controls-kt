package space.kscience.controls.constructor

import kotlinx.coroutines.test.runTest
import space.kscience.controls.constructor.models.flow.MaterialFlowConsumer
import space.kscience.controls.constructor.models.flow.MaterialFlowProducer
import space.kscience.controls.constructor.units.Kilograms
import space.kscience.controls.constructor.units.NV
import space.kscience.dataforge.context.Global
import kotlin.test.Test
import kotlin.test.assertEquals

class MaterialFlowTest {
    @Test
    fun producerConsumer() = runTest {

        val productionCapacity = MutableDeviceState(NV<Kilograms>(4.0))
        val consumationCapacity = MutableDeviceState(NV<Kilograms>(1.0))

        val consumer = MaterialFlowConsumer(Global, consumationCapacity, productionCapacity)

        val producer = MaterialFlowProducer(consumer, productionCapacity)


        assertEquals(1.0, producer.production.value.value)
        assertEquals(1.0, consumer.consumation.value.value)

        consumationCapacity.value = NV(2.0)

        assertEquals(2.0, producer.production.value.value)
        assertEquals(2.0, consumer.consumation.value.value)

        consumationCapacity.value = NV(5.0)

        assertEquals(4.0, producer.production.value.value)
        assertEquals(4.0, consumer.consumation.value.value)

    }
}