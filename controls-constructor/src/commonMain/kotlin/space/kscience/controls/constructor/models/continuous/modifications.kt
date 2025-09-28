package space.kscience.controls.constructor.models.continuous

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.constructor.LateBindDeviceState
import space.kscience.controls.constructor.combine
import space.kscience.controls.constructor.transform
import space.kscience.controls.constructor.units.*
import kotlin.time.Duration

/**
 * Delays the emission of the values from the current [DeviceState] by a specified [delay] duration.
 *
 * @param scope The [CoroutineScope] used to manage the asynchronous operation of delaying the values.
 * @param delay The delay duration to apply to each value emitted by the [DeviceState].
 * @return A new [DeviceState] that emits delayed values from the current state by the specified [delay].
 */
public fun <T> DeviceState<T>.delayedBy(
    scope: CoroutineScope,
    delay: Duration,
    initialValue: T = value
): DeviceState<T> = DeviceState.transform(scope, this, initialValue) {
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
public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousProducerInterface<U, T>.delayed(
    scope: CoroutineScope,
    delay: Duration,
): ContinuousProducerInterface<U, T> = object : ContinuousProducerInterface<U, T> {

    override val producerAlgebra: AmountAlgebra<U, T> get() = this@delayed.producerAlgebra

    override val production: DeviceState<PerSecond<U, T>> = this@delayed.production

    override val productionCapacity: DeviceState<PerSecond<U, T>>
        get() = this@delayed.productionCapacity.delayedBy(
            scope,
            delay
        )

    override val consumerRequest: LateBindDeviceState<AmountPerSecond<U>> = LateBindDeviceState(PerSecond.zero())

    init {
        this@delayed.consumerRequest.bind(consumerRequest.delayedBy(scope, delay))
    }
}

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousConsumerInterface<U, T>.delayedConsumer(
    scope: CoroutineScope,
    delay: Duration,
): ContinuousConsumerInterface<U, T> = object : ContinuousConsumerInterface<U, T> {
    override val consumerAlgebra: AmountAlgebra<U, T> get() = this@delayedConsumer.consumerAlgebra

    override val consumation: DeviceState<PerSecond<U, T>> = this@delayedConsumer.consumation

    override val consumationCapacity: DeviceState<AmountPerSecond<U>> =
        this@delayedConsumer.consumationCapacity.delayedBy(scope, delay, PerSecond.zero())

    override val supplyRequest: LateBindDeviceState<PerSecond<U, T>> =
        LateBindDeviceState(consumerAlgebra.zero.perSecond)

    init {
        this@delayedConsumer.supplyRequest.bind(supplyRequest.delayedBy(scope, delay, consumerAlgebra.zero.perSecond))
    }
}

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousProducerInterface<U, T>.limited(
    scope: CoroutineScope,
    productionLimit: DeviceState<AmountPerSecond<U>>
): ContinuousProducerInterface<U, T> = object : ContinuousProducerInterface<U, T> {
    override val producerAlgebra: AmountAlgebra<U, T> get() = this@limited.producerAlgebra

    override val production: DeviceState<PerSecond<U, T>> get() = this@limited.production

    override val productionCapacity: DeviceState<PerSecond<U, T>> get() = this@limited.productionCapacity

    override val consumerRequest: LateBindDeviceState<AmountPerSecond<U>> = LateBindDeviceState(productionLimit.value)

    private val limitedValue: DeviceState<AmountPerSecond<U>> = DeviceState.combine(
        scope,
        consumerRequest,
        productionLimit
    ) { consumerRequest: AmountPerSecond<U>, limit: AmountPerSecond<U> ->
        minOf(consumerRequest, limit)
    }

    init {
        //bind inner consumation request to outer limited value
        this@limited.consumerRequest.bind(limitedValue)
    }
}

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousProducerInterface<U, T>.limited(
    scope: CoroutineScope,
    productionLimit: AmountPerSecond<U>
): ContinuousProducerInterface<U, T> = limited(scope, DeviceState(productionLimit))

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousConsumerInterface<U, T>.limitedConsumer(
    scope: CoroutineScope,
    consumationLimit: DeviceState<AmountPerSecond<U>>
): ContinuousConsumerInterface<U, T> = object : ContinuousConsumerInterface<U, T> {
    override val consumerAlgebra: AmountAlgebra<U, T> get() = this@limitedConsumer.consumerAlgebra

    override val consumation: DeviceState<PerSecond<U, T>> get() = this@limitedConsumer.consumation
    override val consumationCapacity: DeviceState<AmountPerSecond<U>> get() = this@limitedConsumer.consumationCapacity
    override val supplyRequest: LateBindDeviceState<PerSecond<U, T>> = LateBindDeviceState(
        with(consumerAlgebra) {
            this@limitedConsumer.supplyRequest.value.coerceValueIn<U, T>(
                PerSecond.zero<U>()..consumationLimit.value
            )
        }

    )

    private val limitedValue: DeviceState<PerSecond<U, T>> = DeviceState.combine(
        scope,
        supplyRequest,
        consumationLimit
    ) { supplyRequest: PerSecond<U, T>, limit: AmountPerSecond<U> ->
        with(consumerAlgebra) {
            supplyRequest.coerceValueIn(PerSecond.zero<U>()..limit)
        }
    }

    init {
        this@limitedConsumer.supplyRequest.bind(limitedValue)
    }
}

public fun <U : UnitsOfMatter, T: Amount<U>> ContinuousConsumerInterface<U, T>.limitedConsumer(
    scope: CoroutineScope,
    consumationLimit: AmountPerSecond<U>
): ContinuousConsumerInterface<U, T> = limitedConsumer(scope, DeviceState(consumationLimit))