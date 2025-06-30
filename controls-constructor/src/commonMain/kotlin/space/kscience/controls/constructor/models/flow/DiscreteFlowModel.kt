package space.kscience.controls.constructor.models.flow

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.Amount
import space.kscience.controls.constructor.units.NumericalValue
import space.kscience.controls.constructor.units.UnitsOfMeasurement
import space.kscience.controls.constructor.units.times
import space.kscience.controls.time.clock
import space.kscience.controls.time.simulationDispatcher
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.NameToken
import space.kscience.dataforge.names.asName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit


public data class DiscreteFlowPackage<U : UnitsOfMeasurement>(
    val amount: Amount<U>,
    val creationTime: Instant
)

public interface DiscreteFlowModel : Model

public suspend fun <M : DiscreteFlowModel> M.runSimulation(
    block: suspend M.() -> Unit
) {
    withContext(context.simulationDispatcher) {
        block()
    }
}

/**
 * Actor suspends [emit] when it can't consume package
 */
public interface DiscreteActor<U : UnitsOfMeasurement> : FlowCollector<DiscreteFlowPackage<U>> {

    /**
     * The rate in which actual consumation (not suggestion) is done averaged over model default discretization period.
     */
    public val consumation: DeviceState<Amount<U>>
}

/**
 * Non-invasive measurement of flow rate. Writes values to [target]
 */
internal fun <U : UnitsOfMeasurement> Flow<DiscreteFlowPackage<U>>.measureFlow(
    clock: Clock,
    target: MutableDeviceState<Amount<U>>,
): Flow<DiscreteFlowPackage<U>> {

    var time: Instant? = null

    return onEach { pack ->
        val now = clock.now()
        time?.let { from ->
            val delta = now - from
            target.value = NumericalValue(
                pack.amount.value / delta.toDouble(DurationUnit.SECONDS)
            )
        }
        time = now
    }
}

/**
 * Limits input of the incoming flow to [limit] per second
 */
internal fun <U : UnitsOfMeasurement> Flow<DiscreteFlowPackage<U>>.limitFlow(
    clock: Clock,
    limit: suspend () -> NumericalValue<U>
): Flow<DiscreteFlowPackage<U>> = flow {
    // last package time
    var time = clock.now()

    collect { pack ->
        val now = clock.now()
        val interval = now - time

        val deltaT = (pack.amount.value / limit().value).seconds - interval

        if (deltaT.isPositive()) {
            delay(deltaT)
        }

        time = now

        emit(pack)
    }

}


/**
 * A consumer for discrete material flow
 */
public class DiscreteConsumer<U : UnitsOfMeasurement>(
    context: Context,
    public val capacity: DeviceState<NumericalValue<U>>,
    public var target: FlowCollector<DiscreteFlowPackage<U>>? = null
) : ModelConstructor(context, capacity), DiscreteActor<U> {

    override val name: Name = NameToken("consumer", hashCode().toHexString()).asName()

    private val channel = Channel<DiscreteFlowPackage<U>>()

    override suspend fun emit(value: DiscreteFlowPackage<U>) {
        channel.send(value)
    }

    private val _consumation = MutableDeviceState<Amount<U>>(NumericalValue(0.0))

    override val consumation: DeviceState<Amount<U>> get() = _consumation

    init {
        registerState(consumation)
    }

    private val clock: Clock = context.clock

    private val collectionJob = channel.consumeAsFlow()
        .limitFlow(clock) { capacity.value }
        .measureFlow(
            clock = clock,
            target = _consumation
        ).onEach {
            //try emitting package down the line if target is defined
            target?.emit(it)
        }.launchIn(this)

}

public fun <U : UnitsOfMeasurement> DiscreteFlowModel.registerConsumer(
    capacity: DeviceState<NumericalValue<U>>,
    target: FlowCollector<DiscreteFlowPackage<U>>? = null
): DiscreteConsumer<U> = model(DiscreteConsumer(context, capacity, target))

public class DiscreateProducer<U : UnitsOfMeasurement>(
    context: Context,
    public val capacity: DeviceState<NumericalValue<U>>,
    public var target: DiscreteActor<U>,
    private val packageInterval: Duration = 0.1.seconds,
) : ModelConstructor(context, capacity) {
    override val name: Name = NameToken("producer", hashCode().toHexString()).asName()

    private val _production = MutableDeviceState<Amount<U>>(NumericalValue(0.0))

    public val production: DeviceState<Amount<U>> get() = _production

    init {
        registerState(production)
    }

    private val clock: Clock = context.clock

    private val productionJob = flow {
        while (true) {
            val amountPerPackage = capacity.value * (packageInterval / 1.seconds)
            emit(DiscreteFlowPackage(amountPerPackage, clock.now()))
            delay(packageInterval)
        }
    }.onEach {
        target.emit(it)
    }.measureFlow(
        clock = clock,
        target = _production
    ).launchIn(this)
}

public fun <U : UnitsOfMeasurement> DiscreteFlowModel.registerProducer(
    capacity: DeviceState<NumericalValue<U>>,
    target: DiscreteActor<U>,
    packageInterval: Duration = 0.1.seconds
): DiscreateProducer<U> = model(DiscreateProducer(context, capacity, target, packageInterval))