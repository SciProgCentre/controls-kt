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

/**
 * Delays the emission of the values from the current [DeviceState] by a specified [delay] duration.
 *
 * @param scope The [CoroutineScope] used to manage the asynchronous operation of delaying the values.
 * @param delay The delay duration to apply to each value emitted by the [DeviceState].
 * @return A new [DeviceState] that emits delayed values from the current state by the specified [delay].
 */
public fun <T> DeviceState<T>.delayedBy(scope: CoroutineScope, delay: Duration): DeviceState<T> =
    DeviceState.transform(scope, this, value) {
        delay(delay)
        it
    }

/**
 * Delays the emission of values from the production and productionCapacity properties of a
 * [ContinuousProducerInterface] by a specified duration.
 *
 * @param scope The [CoroutineScope] used to manage the asynchronous operation of delaying values.
 * @param delay The duration by which the values are delayed.
 * @return A new [ContinuousProducerInterface] with production and productionCapacity values delayed by the specified duration.
 */
public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousProducerInterface<U, T>.delayed(
    scope: CoroutineScope,
    delay: Duration,
): ContinuousProducerInterface<U, T> = object : ContinuousProducerInterface<U, T> {

    override val production: DeviceState<T> = this@delayed.production

    override val productionCapacity: DeviceState<T> get() = this@delayed.productionCapacity.delayedBy(scope, delay)

    override val consumerRequest: LateBindDeviceState<Numeric<U>> = LateBindDeviceState(Numeric.zero())

    init {
        this@delayed.consumerRequest.bind(consumerRequest.delayedBy(scope, delay))
    }
}

public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousProducerInterface<U, T>.limited(
    scope: CoroutineScope,
    productionLimit: DeviceState<Numeric<U>>
): ContinuousProducerInterface<U, T> = object : ContinuousProducerInterface<U, T> {
    override val production: DeviceState<T> get() = this@limited.production

    override val productionCapacity: DeviceState<T> get() = this@limited.productionCapacity

    override val consumerRequest: LateBindDeviceState<Numeric<U>> = LateBindDeviceState(productionLimit.value)

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