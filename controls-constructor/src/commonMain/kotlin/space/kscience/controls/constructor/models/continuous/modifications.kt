package space.kscience.controls.constructor.models.continuous

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import space.kscience.controls.constructor.*
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
 * [ContinuousProducer] by a specified duration.
 *
 * @param scope The [CoroutineScope] used to manage the asynchronous operation of delaying values.
 * @param delay The duration by which the values are delayed.
 * @return A new [ContinuousProducer] with production and productionCapacity values delayed by the specified duration.
 */
public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousProducer<U, T>.delayed(
    scope: CoroutineScope,
    delay: Duration,
): ContinuousProducer<U, T> = object : ContinuousProducer<U, T> {

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

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousProducer<U, T>.sample(
    samplingInterval: Duration,
): ContinuousProducer<U, T> = object : ContinuousProducer<U, T> {

    override val producerAlgebra: AmountAlgebra<U, T> get() = this@sample.producerAlgebra

    override val production: DeviceState<PerSecond<U, T>> = this@sample.production

    override val productionCapacity: DeviceState<PerSecond<U, T>>
        get() = this@sample.productionCapacity.sample(samplingInterval)

    override val consumerRequest: LateBindDeviceState<AmountPerSecond<U>> = LateBindDeviceState(PerSecond.zero())

    init {
        this@sample.consumerRequest.bind(consumerRequest.sample(samplingInterval))
    }
}

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousConsumer<U, T>.delayedConsumer(
    scope: CoroutineScope,
    delay: Duration,
): ContinuousConsumer<U, T> = object : ContinuousConsumer<U, T> {
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

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousProducer<U, T>.limited(
    scope: CoroutineScope,
    productionLimit: DeviceState<AmountPerSecond<U>>
): ContinuousProducer<U, T> = object : ContinuousProducer<U, T> {
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

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousProducer<U, T>.limited(
    scope: CoroutineScope,
    productionLimit: AmountPerSecond<U>
): ContinuousProducer<U, T> = limited(scope, DeviceState(productionLimit))

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousConsumer<U, T>.limitedConsumer(
    scope: CoroutineScope,
    consumationLimit: DeviceState<AmountPerSecond<U>>
): ContinuousConsumer<U, T> = object : ContinuousConsumer<U, T> {
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

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousConsumer<U, T>.limitedConsumer(
    scope: CoroutineScope,
    consumationLimit: AmountPerSecond<U>
): ContinuousConsumer<U, T> = limitedConsumer(scope, DeviceState(consumationLimit))

/**
 * Collects an amount over a specified duration asynchronously by integrating a flow of [PerSecond] values in a
 * [DeviceState]. The method returns the total accumulated amount computed over the given duration.
 *
 * @param duration The time duration over which the amount is collected.
 * @return A [Deferred] representing the total accumulated amount of type [T] after the specified duration.
 */
context(container: StateContainer, algebra: AmountAlgebra<U, T>)
public fun <U : UnitsOfMeasurement, T : Amount<U>> DeviceState<PerSecond<U, T>>.collectAmountAsync(
    duration: Duration
): Deferred<T> = container.async {
    val clock = container.clock
    var sum: T = algebra.zero
    var lastValue: PerSecond<U, T> = value
    var lastTime = clock.now()

    val collectionJob = subscribe().onEach {
        val now = clock.now()
        sum += lastValue * (now - lastTime)
        lastTime = now
        lastValue = it
    }.launchIn(this)

    delay(duration)
    collectionJob.cancel()

    sum += lastValue.times(clock.now() - lastTime)

    return@async sum
}