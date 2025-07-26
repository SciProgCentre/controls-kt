package space.kscience.controls.constructor.models.continuous

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.constructor.LateBindDeviceState
import space.kscience.controls.constructor.transform
import space.kscience.controls.constructor.units.Amount
import space.kscience.controls.constructor.units.Numeric
import space.kscience.controls.constructor.units.UnitsOfMeasurement
import kotlin.time.Duration


public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousProducerInterface<U, T>.delayedProduction(
    scope: CoroutineScope,
    delay: Duration
): ContinuousProducerInterface<U, T> = object : ContinuousProducerInterface<U, T> {

    override val production: DeviceState<T> = DeviceState.transform(
        state = this@delayedProduction.production,
        scope = scope,
        initialValue = this@delayedProduction.production.value
    ) {
        delay(delay)
        it
    }


    override val productionCapacity: DeviceState<T> get() = this@delayedProduction.productionCapacity

    override val consumerRequest: LateBindDeviceState<Numeric<U>> get() = this@delayedProduction.consumerRequest
}