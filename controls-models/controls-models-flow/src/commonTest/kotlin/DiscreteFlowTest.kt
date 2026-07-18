package space.kscience.controls.models


import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.ExperimentalControlsApi
import space.kscience.controls.constructor.MutableValueState
import space.kscience.controls.constructor.runSimulation
import space.kscience.controls.constructor.units.Kilograms
import space.kscience.controls.constructor.units.NumericAmount
import space.kscience.controls.models.discrete.DiscreteFlowModel
import space.kscience.controls.models.discrete.registerConsumer
import space.kscience.controls.models.discrete.registerProducer
import space.kscience.controls.time.withVirtualTime
import space.kscience.dataforge.context.Context
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalControlsApi::class)
class DiscreteFlowTest {

    val epoch = Instant.fromEpochMilliseconds(0L)

    val context = Context("test") {
        withVirtualTime(epoch)
    }

    @Test
    fun pipe() = runTest {

        val production = MutableValueState(NumericAmount<Kilograms>(4.0))
        val consumation = MutableValueState(NumericAmount<Kilograms>(1.0))

        object : DiscreteFlowModel(context) {

            val consumer = registerConsumer(consumation)

            //            {
//                println("Packet: created ${it.creationTime - epoch} received ${clock.now() - epoch} value ${it.amount.value}")
//            }
            val producer = registerProducer(production, consumer, 0.02.seconds)
        }.runSimulation {

            producer.production.subscribe().onEach {
                println("production: $it (${clock.now() - epoch})")
            }.launchIn(backgroundScope)

            consumer.consumation.subscribe().onEach {
                println("consumation: $it (${clock.now() - epoch})")
            }.launchIn(backgroundScope)


            delay(2.seconds)

            assertEquals(1.0, producer.production.value.value, 1e-4)
            assertEquals(1.0, consumer.consumation.value.value, 1e-4)

            consumation.value = NumericAmount(4.0)
            delay(1.seconds)

            assertEquals(4.0, producer.production.value.value, 1e-4)
            assertEquals(4.0, consumer.consumation.value.value, 1e-4)

            consumation.value = NumericAmount(6.0)
            delay(1.seconds)

            assertEquals(4.0, producer.production.value.value, 1e-4)
            assertEquals(4.0, consumer.consumation.value.value, 1e-4)

        }

    }


    @Test
    @Ignore
    fun join() = runTest {

        val a = MutableValueState(NumericAmount<Kilograms>(1.0))
        val b = MutableValueState(NumericAmount<Kilograms>(2.0))
        val c = MutableValueState(NumericAmount<Kilograms>(3.0))
        val ab = MutableValueState(NumericAmount<Kilograms>(Double.POSITIVE_INFINITY))
        val abc = MutableValueState(NumericAmount<Kilograms>(8.0))

        val model = object : DiscreteFlowModel(context) {
            val joinABC = registerConsumer(abc) {
                println("Packet from ${it.source}: created ${it.creationTime - epoch} received ${clock.now() - epoch} value ${it.amount.value}")
            }
            val cProducer = registerProducer(c, joinABC, 0.02.seconds)
            val joinAB = registerConsumer(ab, joinABC)
            val bProducer = registerProducer(b, joinAB, 0.02.seconds)
            val aProducer = registerProducer(a, joinAB, 0.02.seconds)
        }.runSimulation {

            joinABC.consumation.subscribe().onEach {
                println("consumation: $it (${clock.now() - epoch})")
            }.launchIn(backgroundScope)

            delay(2.seconds)

            //assertEquals(3.0, joinAB.consumation.value.value, 1e-4)
            assertEquals(6.0, joinABC.consumation.value.value, 1e-4)
            assertEquals(3.0, cProducer.production.value.value, 1e-4)
            assertEquals(2.0, bProducer.production.value.value, 1e-4)
            assertEquals(1.0, aProducer.production.value.value, 1e-4)

            abc.value = NumericAmount(3.0)
            delay(2.seconds)

            assertEquals(3.0, joinABC.consumation.value.value, 1e-1)
            assertEquals(1.5, cProducer.production.value.value, 1e-1)
            assertEquals(1.0, bProducer.production.value.value, 1e-1)
            assertEquals(0.5, aProducer.production.value.value, 1e-1)

        }
    }
}