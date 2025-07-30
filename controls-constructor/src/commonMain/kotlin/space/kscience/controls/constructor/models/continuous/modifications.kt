package space.kscience.controls.constructor.models.continuous

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.constructor.LateBindDeviceState
import space.kscience.controls.constructor.combine
import space.kscience.controls.constructor.transform
import space.kscience.controls.constructor.units.Amount
import space.kscience.controls.constructor.units.Numeric
import space.kscience.controls.constructor.units.UnitsOfMeasurement
import kotlin.time.Duration


public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousProducerInterface<U, T>.delayed(
    scope: CoroutineScope,
    delay: Duration
): ContinuousProducerInterface<U, T> = object : ContinuousProducerInterface<U, T> {

    override val production: DeviceState<T> = DeviceState.transform(
        scope = scope,
        state = this@delayed.production,
        initialValue = this@delayed.production.value
    ) {
        delay(delay)
        it
    }


    override val productionCapacity: DeviceState<T> get() = this@delayed.productionCapacity

    override val consumerRequest: LateBindDeviceState<Numeric<U>> get() = this@delayed.consumerRequest
}

public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousProducerInterface<U, T>.limited(
    scope: CoroutineScope,
    productionLimit: DeviceState<Numeric<U>>
): ContinuousProducerInterface<U, T> = object : ContinuousProducerInterface<U, T> {
    override val production: DeviceState<T>
        get() = this@limited.production

    override val productionCapacity: DeviceState<T>
        get() = this@limited.productionCapacity

    override val consumerRequest: LateBindDeviceState<Numeric<U>> = LateBindDeviceState(scope,productionLimit.value)

    private val limitedValue: DeviceState<Numeric<U>> = DeviceState.combine(
        scope,
        consumerRequest,
        productionLimit
    ) { consumerRequest: Numeric<U>, limit: Numeric<U> ->
        minOf(consumerRequest, limit)
    }

    init {
        this@limited.consumerRequest.bind(limitedValue)
    }

}

public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousProducerInterface<U, T>.limited(
    scope: CoroutineScope,
    productionLimit: Numeric<U>
): ContinuousProducerInterface<U, T> = limited(scope, DeviceState(productionLimit))