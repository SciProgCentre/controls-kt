package space.kscience.controls.constructor.models.discrete

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import space.kscience.controls.api.ExperimentalControlsApi
import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.Amount
import space.kscience.controls.constructor.units.NumericAmount
import space.kscience.controls.constructor.units.UnitsOfMeasurement
import space.kscience.controls.constructor.units.times
import space.kscience.controls.time.ValueWithTime
import space.kscience.controls.time.clock
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.NameToken
import space.kscience.dataforge.names.asName
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.Instant


@ExperimentalControlsApi
public data class DiscreteFlowPacket<U : UnitsOfMeasurement>(
    val source: Name,
    val amount: Amount<U>,
    val creationTime: Instant
)

@ExperimentalControlsApi
public abstract class DiscreteFlowModel(context: Context) : ModelConstructor(context)


/**
 * Actor suspends [emit] when it can't consume package
 */
@ExperimentalControlsApi
public interface DiscreteActor<U : UnitsOfMeasurement> : FlowCollector<DiscreteFlowPacket<U>> {

    /**
     * The rate in which actual consumation (not suggestion) is done averaged over model default discretization period.
     */
    public val consumation: ValueState<Amount<U>>
}

/**
 * Non-invasive measurement of discrete rate. Writes values to [target]
 */
@ExperimentalControlsApi
internal fun <U : UnitsOfMeasurement> Flow<DiscreteFlowPacket<U>>.measureFlow(
    clock: Clock,
    target: MutableValueState<Amount<U>>,
    numberOfPackages: Int = 10
): Flow<DiscreteFlowPacket<U>> {
    require(numberOfPackages > 2) { "Number of packages must be more than 2 to calculate average" }

    val buffer = ArrayDeque<ValueWithTime<DiscreteFlowPacket<U>>>(numberOfPackages)

    fun push(packet: DiscreteFlowPacket<U>) {
        buffer.addLast(ValueWithTime(packet, clock.now()))
        if (buffer.size > numberOfPackages - 1) {
            buffer.removeFirst()
        }
    }

    return onEach { packet ->
        push(packet)
        if (buffer.size > 2) {
            val timeDelta = buffer.last().time - buffer.first().time
            val amount = buffer.drop(1).sumOf { it.value.amount.value }
            val rate = amount / timeDelta.toDouble(DurationUnit.SECONDS)
            target.value = NumericAmount(rate)
        }
    }
}

/**
 * Limits input of the incoming discrete to [limit] per second
 */
@ExperimentalControlsApi
internal fun <U : UnitsOfMeasurement> Flow<DiscreteFlowPacket<U>>.limitFlow(
    clock: Clock,
    limit: suspend () -> NumericAmount<U>
): Flow<DiscreteFlowPacket<U>> = flow {
    // last package time
    var time = clock.now()

    collect { packet ->
        val now = clock.now()
        val interval = now - time

        val deltaT = (packet.amount.value / limit().value).seconds - interval

        if (deltaT.isPositive()) {
            delay(deltaT)
        }

        time = clock.now()

        emit(packet)
    }

}


/**
 * A consumer for discrete material discrete
 */
@ExperimentalControlsApi
public class DiscreteConsumer<U : UnitsOfMeasurement>(
    context: Context,
    public val capacity: ValueState<NumericAmount<U>>,
    public var target: FlowCollector<DiscreteFlowPacket<U>>? = null
) : ModelConstructor(context), DiscreteActor<U> {

    override val modelType: Name = NameToken("consumer", hashCode().toHexString()).asName()

    private val channel = Channel<DiscreteFlowPacket<U>>()

    override suspend fun emit(value: DiscreteFlowPacket<U>) {
        channel.send(value)
    }

    private val _consumation = MutableValueState<Amount<U>>(NumericAmount(0.0), context.clock)

    override val consumation: ValueState<Amount<U>> get() = _consumation

    init {
        registerState(consumation, "consumation".asName())
        registerState(capacity, "capacity".asName())
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

@ExperimentalControlsApi
public fun <U : UnitsOfMeasurement> DiscreteFlowModel.registerConsumer(
    capacity: ValueState<NumericAmount<U>>,
    target: FlowCollector<DiscreteFlowPacket<U>>? = null
): DiscreteConsumer<U> = model(DiscreteConsumer(context, capacity, target))

@ExperimentalControlsApi
public class DiscreateProducer<U : UnitsOfMeasurement>(
    context: Context,
    public val capacity: ValueState<NumericAmount<U>>,
    public var target: DiscreteActor<U>,
    private val packageInterval: Duration = 0.1.seconds,
) : ModelConstructor(context) {
    override val modelType: Name = NameToken("producer", hashCode().toHexString()).asName()

    private val _production = MutableValueState<Amount<U>>(NumericAmount(0.0), context.clock)

    public val production: ValueState<Amount<U>> get() = _production

    init {
        registerState(production, "production".asName())
        registerState(capacity, "capacity".asName())
    }

    private val clock: Clock = context.clock

    private val productionJob = flow {
        while (true) {
            delay(packageInterval)
            val amountPerPackage = capacity.value * (packageInterval / 1.seconds)
            emit(DiscreteFlowPacket(modelType, amountPerPackage, clock.now()))
        }
    }.onEach {
        target.emit(it)
    }.measureFlow(
        clock = clock,
        target = _production
    ).launchIn(this)
}

@ExperimentalControlsApi
public fun <U : UnitsOfMeasurement> DiscreteFlowModel.registerProducer(
    capacity: ValueState<NumericAmount<U>>,
    target: DiscreteActor<U>,
    packageInterval: Duration = 0.1.seconds
): DiscreateProducer<U> = model(DiscreateProducer(context, capacity, target, packageInterval))