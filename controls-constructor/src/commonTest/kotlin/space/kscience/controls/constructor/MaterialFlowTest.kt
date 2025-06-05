package space.kscience.controls.constructor

import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import space.kscience.controls.constructor.models.flow.MaterialFlowConsumer
import space.kscience.controls.constructor.models.flow.MaterialFlowJoin
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

        val producer = MaterialFlowProducer.fromConsumer(consumer, productionCapacity)


        assertEquals(1.0, producer.production.value.value)
        assertEquals(1.0, consumer.consumation.value.value)

        consumationCapacity.value = NV(2.0)

        assertEquals(2.0, producer.production.value.value)
        assertEquals(2.0, consumer.consumation.value.value)

        consumationCapacity.value = NV(5.0)

        assertEquals(4.0, producer.production.value.value)
        assertEquals(4.0, consumer.consumation.value.value)

    }

    fun <T : Comparable<T>> StateContainer.combineStateToMin(
        sourceState1: DeviceState<T>,
        sourceState2: DeviceState<T>,
        targetState: MutableDeviceState<T>,
    ): Job = bindCombinedState(sourceState1, sourceState2, targetState) { a, b -> minOf(a, b) }

    @Test
    fun join() = runTest {

        val a = MutableDeviceState(NV<Kilograms>(1.0))
        val b = MutableDeviceState(NV<Kilograms>(2.0))
        val c = MutableDeviceState(NV<Kilograms>(3.0))
        val ab = MutableDeviceState(NV<Kilograms>(100.0))
        val abc = MutableDeviceState(NV<Kilograms>(100.0))


        val joinAB = MaterialFlowJoin(
            context = Global,
            consumerRequest = ab,
            supplyRequest = mapOf("a" to a, "b" to b),
        )

        val joinABC = MaterialFlowJoin(
            context = Global,
            consumerRequest = abc,
            supplyRequest = mapOf("ab" to ab, "c" to c),
        )


    }
}