package space.kscience.controls.constructor

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import space.kscience.controls.constructor.models.flow.ContinuousConsumer
import space.kscience.controls.constructor.models.flow.ContinuousFlowJoin
import space.kscience.controls.constructor.models.flow.ContinuousProducer
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

        val consumer = ContinuousConsumer(Global, consumationCapacity, productionCapacity)

        val producer = ContinuousProducer.fromConsumer(consumer, productionCapacity)


        assertEquals(1.0, producer.production.value.value)
        assertEquals(1.0, consumer.consumation.value.value)

        consumationCapacity.value = NV(2.0)

        assertEquals(2.0, producer.production.value.value)
        assertEquals(2.0, consumer.consumation.value.value)

        consumationCapacity.value = NV(5.0)

        assertEquals(4.0, producer.production.value.value)
        assertEquals(4.0, consumer.consumation.value.value)

    }

    fun DeviceState<*>.printEach(scope: CoroutineScope, stateName: String) {
        fun printOne(value: Any?) {
            println("$stateName: $value")
        }

        printOne(value)

        valueFlow.onEach {
            printOne(it)
        }.launchIn(scope)
    }


    fun <T : Comparable<T>> StateContainer.bindToMin(
        sourceState1: DeviceState<T>,
        sourceState2: DeviceState<T>,
        targetState: MutableDeviceState<T>,
    ): Job = bindCombinedState(sourceState1, sourceState2, targetState) { a, b ->
//        println("Min of $a and $b is ${minOf(a, b)}")
        minOf(a, b)
    }

    /**
     * a  b
     * |  |
     * (ab) c
     *  |   |
     *  (abc)
     */
    @Test
    fun join() = runTest {

        launch {
            val a = MutableDeviceState(NV<Kilograms>(1.0))
            val b = MutableDeviceState(NV<Kilograms>(2.0))
            val c = MutableDeviceState(NV<Kilograms>(3.0))
            val ab = MutableDeviceState(NV<Kilograms>(Double.POSITIVE_INFINITY)).apply { printEach(this@launch, "ab") }
            val abc = MutableDeviceState(NV<Kilograms>(8.0)).apply { printEach(this@launch, "abc") }


            val joinAB = ContinuousFlowJoin(
                context = Global,
                consumerRequest = ab,
                supplyRequest = mapOf("a" to a, "b" to b),
            )

            joinAB.production.printEach(this, "joinAB.production")

            val joinABC = ContinuousFlowJoin(
                context = Global,
                consumerRequest = abc,
                supplyRequest = mapOf("ab" to joinAB.maximumProduction, "c" to c),
            )

            joinABC.production.printEach(this, "joinABC.production")

            joinABC.consumation.printEach(this, "joinABC.consumation")

            joinABC.bindState(joinABC.partialConsumation["ab"]!!, ab)

            delay(10)

            assertEquals(NV(3.0), ab.value)

            assertEquals(NV(6.0), joinABC.production.value)

            assertEquals(1.0, joinAB.partialConsumation["a"]?.value?.value)

            abc.value = NV(3.0)

            delay(10)

            assertEquals(NV(1.5), joinAB.production.value)

            assertEquals(0.5, joinAB.partialConsumation["a"]?.value?.value)


            abc.value = NV(4.0)
            a.value = NV(7.0)

            delay(10)

            assertEquals(3.0, joinAB.production.value.value, 1e-5)
            assertEquals(2.33333, joinAB.partialConsumation["a"]!!.value.value, 1e-3)

            abc.value = NV(15.0)

            delay(10)

            assertEquals(9.0, joinAB.production.value.value, 1e-5)
            assertEquals(7.0, joinAB.partialConsumation["a"]?.value?.value)

            cancel()
        }
    }
}