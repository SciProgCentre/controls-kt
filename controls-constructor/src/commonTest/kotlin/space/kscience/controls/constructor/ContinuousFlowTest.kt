package space.kscience.controls.constructor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import space.kscience.controls.constructor.models.continuous.*
import space.kscience.controls.constructor.units.CubicMeters
import space.kscience.controls.constructor.units.Kilograms
import space.kscience.controls.constructor.units.Numeric
import space.kscience.controls.constructor.units.NumericAmountAlgebra
import space.kscience.controls.time.withVirtualTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Global
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ContinuousFlowTest {

    val epoch = Instant.fromEpochMilliseconds(0L)

    val context = Context("test") {
        withVirtualTime(epoch)
    }

    fun DeviceState<*>.printEach(scope: TestScope, stateName: String) {
        fun printOne(value: Any?) {
            println("$stateName: $value")
        }

        printOne(value)

        valueFlow.onEach {
            printOne(it)
        }.launchIn(scope.backgroundScope)
    }

    @Test
    fun producerConsumer() = runTest {

        val productionCapacity = MutableDeviceState(Numeric<Kilograms>(4.0))
        val consumationCapacity = MutableDeviceState(Numeric<Kilograms>(1.0))

        val consumer = ContinuousConsumer(Global, consumationCapacity)

        val producer = ContinuousProducer.fromConsumer(consumer, productionCapacity)


        assertEquals(1.0, producer.production.value.value)
        assertEquals(1.0, consumer.consumation.value.value)

        consumationCapacity.value = Numeric(2.0)

        assertEquals(2.0, producer.production.value.value)
        assertEquals(2.0, consumer.consumation.value.value)

        consumationCapacity.value = Numeric(5.0)

        assertEquals(4.0, producer.production.value.value)
        assertEquals(4.0, consumer.consumation.value.value)

    }

    /**
     * a  b
     * |  |
     * (ab) c
     *  |   |
     *  (abc)
     */
    @Test
    fun mix() = runTest {
        val aProduction = MutableDeviceState(Numeric<Kilograms>(1.0))
        val bProduction = MutableDeviceState(Numeric<Kilograms>(2.0))
        val cProduction = MutableDeviceState(Numeric<Kilograms>(3.0))
        val abcConsumation = MutableDeviceState(Numeric<Kilograms>(8.0))

        val joinAB = ContinuousMix<Kilograms>(context = Global, listOf("a", "b"))

        joinAB.connectProducer("a", aProduction)
        joinAB.connectProducer("b", bProduction)

        joinAB.production.printEach(this, "joinAB.production")

        val joinABC = ContinuousMix<Kilograms>(context = Global, listOf("ab", "c"))

        joinABC.connectProducer("ab", joinAB)
        joinABC.connectProducer("c", cProduction)
        joinABC.connectConsumer(abcConsumation)

        joinABC.production.printEach(this, "joinABC.production")
        joinABC.consumation.printEach(this, "joinABC.consumation")

        assertEquals(Numeric(6.0), joinABC.production.value)

        assertEquals(1.0, joinAB.individualConsumation["a"]?.value?.value)

        abcConsumation.value = Numeric(3.0)

        assertEquals(Numeric(1.5), joinAB.production.value)

        assertEquals(0.5, joinAB.individualConsumation["a"]?.value?.value)

        abcConsumation.value = Numeric(4.0)
        aProduction.value = Numeric(7.0)

        assertEquals(3.0, joinAB.production.value.value, 1e-5)
        assertEquals(2.33333, joinAB.individualConsumation["a"]!!.value.value, 1e-3)

        abcConsumation.value = Numeric(15.0)

        assertEquals(9.0, joinAB.production.value.value, 1e-5)
        assertEquals(7.0, joinAB.individualConsumation["a"]?.value?.value)
    }


    @Test
    fun buffer() = runTest {

        val model = object : ContinuousFlowModel(context) {
            val algebra = NumericAmountAlgebra<Kilograms>()
            val bufferCapacity = Numeric<Kilograms>(10.0)

            val productionCapacity = MutableDeviceState(Numeric<Kilograms>(2.0))
            val consumationCapacity = MutableDeviceState(Numeric<Kilograms>(1.0))

            val producer = producer(productionCapacity)

            val buffer = buffer(algebra, bufferCapacity)

            val consumer = consumer(consumationCapacity)

            init {
                connect(producer = producer, consumer = buffer)
                connect(producer = buffer, consumer = consumer)
            }
        }.runSimulation {

            buffer.content.valueFlow.onEach {
                println("content: $it (${clock.now() - epoch})")
            }.launchIn(backgroundScope)


            assertEquals(2.0, producer.production.value.value)
            assertEquals(1.0, consumer.consumation.value.value)

            delay(11.seconds)

            assertEquals(1.0, producer.production.value.value)
            assertEquals(1.0, consumer.consumation.value.value)

            productionCapacity.value = Numeric(1.0)
            consumationCapacity.value = Numeric(2.0)

            assertEquals(1.0, producer.production.value.value)
            assertEquals(2.0, consumer.consumation.value.value)

            delay(11.seconds)

            assertEquals(1.0, producer.production.value.value)
            assertEquals(1.0, consumer.consumation.value.value)
        }
    }

    @Test
    fun reaction() = runTest {
        val model = object : ContinuousFlowModel(context) {
            val algebra = NumericAmountAlgebra<Kilograms>()

            val aProductionCapacity = MutableDeviceState(Numeric<Kilograms>(6.0))
            val bProductionCapacity = MutableDeviceState(Numeric<Kilograms>(1.0))
            val consumationCapacity = MutableDeviceState(Numeric<Kilograms>(1.0))

            val aProducer = producer(aProductionCapacity)
            val bProducer = producer(bProductionCapacity)

            val reactor = reaction(algebra, formula = mapOf("a" to algebra.one, "b" to algebra.one))

            val consumer = consumer(consumationCapacity)

            init {
                reactor.connectProducer("a", aProducer)
                reactor.connectProducer("b", bProducer)
                reactor.connectConsumer(consumer)
            }

        }.runSimulation {

            assertEquals(1.0, aProducer.production.value.value)
            assertEquals(1.0, bProducer.production.value.value)
            assertEquals(1.0, consumer.consumation.value.value)

            aProductionCapacity.value = Numeric(0.5)

            assertEquals(0.5, aProducer.production.value.value)
            assertEquals(0.5, bProducer.production.value.value)
            assertEquals(0.5, consumer.consumation.value.value)
        }
    }

    @Test
    fun transformation() = runTest {
        val model = object : ContinuousFlowModel(context) {
            val output = MutableDeviceState(Numeric<Kilograms>(2.0))
            val input = MutableDeviceState(Numeric<CubicMeters>(1.0))

            val producer = producer(input)
            val consumer = consumer(output)

            val transformer = linearTransformer(
                supplyAlgebra = NumericAmountAlgebra<CubicMeters>(),
                productionAlgebra = NumericAmountAlgebra<Kilograms>(),
                production = Numeric<Kilograms>(0.1)
            )

            init {
                transformer.connectProducer(producer)
                transformer.connectConsumer(consumer)
            }
        }.runSimulation {
            assertEquals(0.1, transformer.production.value.value)
            assertEquals(0.1, consumer.consumation.value.value)
            assertEquals(1.0, producer.production.value.value)

            input.value = Numeric(30.0)

            assertEquals(2.0, transformer.production.value.value)
            assertEquals(2.0, consumer.consumation.value.value)
            assertEquals(20.0, producer.production.value.value)

        }
    }
}