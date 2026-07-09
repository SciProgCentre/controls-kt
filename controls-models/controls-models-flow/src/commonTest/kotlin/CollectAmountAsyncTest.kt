package space.kscience.controls.models

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import space.kscience.controls.constructor.MutableValueState
import space.kscience.controls.constructor.runSimulation
import space.kscience.controls.constructor.units.AmountPerSecond
import space.kscience.controls.constructor.units.Kilograms
import space.kscience.controls.models.continuous.ContinuousFlowModel
import space.kscience.controls.models.continuous.collectAmountAsync
import space.kscience.controls.time.withVirtualTime
import space.kscience.dataforge.context.Context
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class CollectAmountAsyncTest {

    private val epoch = Instant.fromEpochMilliseconds(0L)

    private val context = Context("test") { withVirtualTime(epoch) }
    val model = object : ContinuousFlowModel(context) {}

    @Test
    fun collectsConstantFlow() = runTest {

        model.runSimulation {
            val flow = MutableValueState(AmountPerSecond<Kilograms>(2.0))

            val deferred = with(Kilograms) {
                flow.collectAmountAsync(5.seconds)
            }


            // Advance virtual time by 5 seconds to complete collection
            delay(8.seconds)

            val amount = deferred.await()
            assertEquals(10.0, amount.value, 1e-9)
        }
    }

    @Test
    fun collectsWhenFlowChanges() = runTest {

        model.runSimulation {
            val flow = MutableValueState(AmountPerSecond<Kilograms>(2.0))

            val deferred = with(Kilograms) {
                flow.collectAmountAsync(5.seconds)
            }


            // Change flow after 2 seconds from 2 kg/s to 3 kg/s
            launch {
                delay(2.seconds)
                flow.value = AmountPerSecond(3.0)
            }

            // Advance virtual time to finish the 5-second collection window
            delay(8.seconds)

            val amount = deferred.await()
            // Expected: 2 s * 2 kg/s + 3 s * 3 kg/s = 4 + 9 = 13 kg
            assertEquals(13.0, amount.value, 1e-9)
        }
    }
}