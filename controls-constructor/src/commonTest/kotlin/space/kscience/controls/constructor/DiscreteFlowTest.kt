package space.kscience.controls.constructor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.ExperimentalControlsApi
import space.kscience.controls.constructor.models.discrete.DiscreteFlowModel
import space.kscience.controls.constructor.models.discrete.registerConsumer
import space.kscience.controls.constructor.models.discrete.registerProducer
import space.kscience.controls.constructor.units.Kilograms
import space.kscience.controls.constructor.units.Numeric
import space.kscience.controls.time.withVirtualTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
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

        val production = MutableDeviceState(Numeric<Kilograms>(4.0))
        val consumation = MutableDeviceState(Numeric<Kilograms>(1.0))

        object : DiscreteFlowModel(context) {
            override val name: Name = "test".asName()

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

            consumation.value = Numeric(4.0)
            delay(1.seconds)

            assertEquals(4.0, producer.production.value.value, 1e-4)
            assertEquals(4.0, consumer.consumation.value.value, 1e-4)

            consumation.value = Numeric(6.0)
            delay(1.seconds)

            assertEquals(4.0, producer.production.value.value, 1e-4)
            assertEquals(4.0, consumer.consumation.value.value, 1e-4)

        }

    }


    @Test
    @Ignore
    fun join() = runTest {

        val a = MutableDeviceState(Numeric<Kilograms>(1.0))
        val b = MutableDeviceState(Numeric<Kilograms>(2.0))
        val c = MutableDeviceState(Numeric<Kilograms>(3.0))
        val ab = MutableDeviceState(Numeric<Kilograms>(Double.POSITIVE_INFINITY))
        val abc = MutableDeviceState(Numeric<Kilograms>(8.0))

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

            abc.value = Numeric(3.0)
            delay(2.seconds)

            assertEquals(3.0, joinABC.consumation.value.value, 1e-1)
            assertEquals(1.5, cProducer.production.value.value, 1e-1)
            assertEquals(1.0, bProducer.production.value.value, 1e-1)
            assertEquals(0.5, aProducer.production.value.value, 1e-1)

        }
    }
}