package space.kscience.controls.constructor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import space.kscience.controls.constructor.models.continuous.*
import space.kscience.controls.constructor.units.*
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

        subscribe().onEach {
            printOne(it)
        }.launchIn(scope.backgroundScope)
    }

    @Test
    fun producerConsumer() = runTest {

        val productionCapacity = MutableDeviceState(AmountPerSecond<Kilograms>(4.0))
        val consumationCapacity = MutableDeviceState(AmountPerSecond<Kilograms>(1.0))

        val consumer = ContinuousConsumer(Global, Kilograms, consumationCapacity)

        val producer = ContinuousProducer(Global, Kilograms, productionCapacity).apply {
            connectConsumer(consumer)
        }

        assertEquals(1.0, producer.production.value.value)
        assertEquals(1.0, consumer.consumation.value.value)

        consumationCapacity.value = AmountPerSecond(2.0)

        assertEquals(2.0, producer.production.value.value)
        assertEquals(2.0, consumer.consumation.value.value)

        consumationCapacity.value = AmountPerSecond(5.0)

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

        val model = object : ContinuousFlowModel(context) {
            val aProduction = MutableDeviceState(AmountPerSecond<Kilograms>(1.0))
            val aProducer = producer(Kilograms, aProduction)

            val bProduction = MutableDeviceState(AmountPerSecond<Kilograms>(2.0))
            val bProducer = producer(Kilograms, bProduction)

            val cProduction = MutableDeviceState(AmountPerSecond<Kilograms>(3.0))
            val cProducer = producer(Kilograms, cProduction)

            val abcConsumation = MutableDeviceState(AmountPerSecond<Kilograms>(8.0))
            val consumer = consumer(Kilograms, abcConsumation)

            val joinAB = ContinuousMix(context = Global, Kilograms, listOf("a", "b"))

            init {
                joinAB.connectProducer("a", aProducer)
                joinAB.connectProducer("b", bProducer)
            }

            val joinABC = ContinuousMix(context = Global, Kilograms, listOf("ab", "c"))

            init {
                joinABC.connectProducer("ab", joinAB)
                joinABC.connectProducer("c", cProducer)
                joinABC.connectConsumer(consumer)
            }
        }.runSimulation {
            joinAB.production.printEach(this@runTest, "joinAB.production")

            joinABC.production.printEach(this@runTest, "joinABC.production")
            joinABC.consumation.printEach(this@runTest, "joinABC.consumation")

            assertEquals(AmountPerSecond(6.0), joinABC.production.value)
            assertEquals(2.0, bProducer.production.value.value)
            assertEquals(1.0, joinAB.individualConsumation["a"]?.value?.value)

            abcConsumation.value = AmountPerSecond(3.0)

            assertEquals(AmountPerSecond(1.5), joinAB.production.value)
            assertEquals(1.0, bProducer.production.value.value)
            assertEquals(0.5, joinAB.individualConsumation["a"]?.value?.value)

            abcConsumation.value = AmountPerSecond(4.0)
            aProduction.value = AmountPerSecond(7.0)

            assertEquals(3.0, joinAB.production.value.value, 1e-5)
            assertEquals(2.33333, joinAB.individualConsumation["a"]!!.value.value, 1e-3)

            abcConsumation.value = AmountPerSecond(15.0)

            assertEquals(9.0, joinAB.production.value.value, 1e-5)
            assertEquals(7.0, joinAB.individualConsumation["a"]?.value?.value)
        }
    }


    @Test
    fun buffer() = runTest {

        val model = object : ContinuousFlowModel(context) {
            val bufferCapacity = NumericAmount<Kilograms>(10.0)

            val productionCapacity = MutableDeviceState(AmountPerSecond<Kilograms>(2.0))
            val consumationCapacity = MutableDeviceState(AmountPerSecond<Kilograms>(1.0))

            val producer = producer(Kilograms, productionCapacity)

            val buffer = buffer(Kilograms, bufferCapacity).apply {
                connectProducer(producer)
            }

            val consumer = consumer(Kilograms, consumationCapacity).apply {
                connectProducer(buffer)
            }
        }.runSimulation {

            buffer.content.subscribe().onEach {
                println("content: $it (${clock.now() - epoch})")
            }.launchIn(backgroundScope)


            assertEquals(2.0, producer.production.value.value)
            assertEquals(1.0, consumer.consumation.value.value)

            delay(11.seconds)

            assertEquals(1.0, producer.production.value.value)
            assertEquals(1.0, consumer.consumation.value.value)

            productionCapacity.value = AmountPerSecond(1.0)
            consumationCapacity.value = AmountPerSecond(2.0)

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

            val aProductionCapacity = MutableDeviceState(AmountPerSecond<Kilograms>(6.0))
            val bProductionCapacity = MutableDeviceState(AmountPerSecond<Kilograms>(1.0))
            val consumationCapacity = MutableDeviceState(AmountPerSecond<Kilograms>(1.0))

            val aProducer = producer(Kilograms, aProductionCapacity)
            val bProducer = producer(Kilograms, bProductionCapacity)

            val reactor = reaction(
                algebra = Kilograms,
                formula = mapOf("a" to 1, "b" to 1),
                production = 1.kilograms.perSecond
            ).apply {
                connectProducer("a", aProducer)
                connectProducer("b", bProducer)
            }

            val consumer = consumer(Kilograms, consumationCapacity).apply {
                connectProducer(reactor)
            }
        }.runSimulation {

            assertEquals(1.0, aProducer.production.value.value)
            assertEquals(1.0, bProducer.production.value.value)
            assertEquals(1.0, consumer.consumation.value.value)

            aProductionCapacity.value = AmountPerSecond(0.5)

            assertEquals(0.5, aProducer.production.value.value)
            assertEquals(0.5, bProducer.production.value.value)
            assertEquals(0.5, consumer.consumation.value.value)
        }
    }

    @Test
    fun transformation() = runTest {
        val model = object : ContinuousFlowModel(context) {
            val output = MutableDeviceState(AmountPerSecond<Kilograms>(2.0))
            val input = MutableDeviceState(AmountPerSecond<CubicMeters>(1.0))

            val producer = producer(CubicMeters, input)
            val consumer = consumer(Kilograms, output)

            val transformer = linearTransformer(
                consumerAlgebra = CubicMeters,
                producerAlgebra = Kilograms,
                production = AmountPerSecond(0.1)
            )

            init {
                transformer.connectProducer(producer)
                transformer.connectConsumer(consumer)
            }

        }.runSimulation {
            assertEquals(0.1, transformer.production.value.value)
            assertEquals(0.1, consumer.consumation.value.value)
            assertEquals(1.0, producer.production.value.value)

            input.value = AmountPerSecond(30.0)

            assertEquals(2.0, transformer.production.value.value)
            assertEquals(2.0, consumer.consumation.value.value)
            assertEquals(20.0, producer.production.value.value)
        }
    }

    @Test
    fun separation() = runTest {
        val model = object : ContinuousFlowModel(context) {
            val production = MutableDeviceState(AmountPerSecond<Kilograms>(4.0))
            val aConsumation = MutableDeviceState(AmountPerSecond<Kilograms>(2.0))
            val bConsumation = MutableDeviceState(AmountPerSecond<Kilograms>(2.0))
            val cConsumation = MutableDeviceState(AmountPerSecond<Kilograms>(1.0))

            val producer = producer(Kilograms, production)

            val splitter1 = separator(
                algebra = Kilograms,
                separationRule = SeparationRule.proportional(Kilograms, mapOf("a" to 1.0, "bc" to 1.0))
            ).apply {
                connectProducer(producer)
            }

            val aConsumer = consumer(Kilograms, aConsumation).apply {
                connectProducer(splitter1.asProducer("a"))
            }

            val splitter2 = separator(
                algebra = Kilograms,
                separationRule = SeparationRule.proportional(Kilograms, mapOf("b" to 1.0, "c" to 1.0))
            ).apply {
                connectProducer(splitter1.asProducer("bc"))
            }

            val bConsumer = consumer(Kilograms, bConsumation).apply {
                connectProducer(splitter2.asProducer("b"))
            }

            val cConsumer = consumer(Kilograms, cConsumation).apply {
                connectProducer(splitter2.asProducer("c"))
            }
        }.runSimulation {

            assertEquals(4.0, producer.production.value.value)
            assertEquals(2.0, aConsumer.consumation.value.value)
            assertEquals(1.0, bConsumer.consumation.value.value)
            assertEquals(1.0, cConsumer.consumation.value.value)

            aConsumation.value = AmountPerSecond(1.0)

            assertEquals(2.0, producer.production.value.value)
            assertEquals(1.0, aConsumer.consumation.value.value)
            assertEquals(0.5, bConsumer.consumation.value.value)
            assertEquals(0.5, cConsumer.consumation.value.value)

        }
    }
}