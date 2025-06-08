package space.kscience.controls.constructor.models.flow

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.constructor.Model
import space.kscience.controls.constructor.MutableDeviceState
import space.kscience.controls.constructor.clock
import space.kscience.controls.constructor.units.Amount
import space.kscience.controls.constructor.units.NumericalValue
import space.kscience.controls.constructor.units.UnitsOfMeasurement
import space.kscience.controls.constructor.units.times
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


public data class DiscreteFlowPackage<U : UnitsOfMeasurement>(
    val amount: Amount<U>,
    val creationTime: Instant
)

public interface DiscreteFlowModel : Model {
    public val discretization: Duration get() = 1.seconds
}

/**
 * Actor suspends [emit] when it can't consume package
 */
public interface DiscreteActor<U : UnitsOfMeasurement> : FlowCollector<DiscreteFlowPackage<U>> {

    public val model: DiscreteFlowModel

    /**
     * The rate in which actual consumation (not suggestion) is done averaged over model default discretization period.
     */
    public val consumation: DeviceState<Amount<U>>
}

/**
 * Non-invasive measurement of flow rate. Writes values to [target]
 */
internal fun <U : UnitsOfMeasurement> Flow<DiscreteFlowPackage<U>>.measureFlow(
    model: DiscreteFlowModel,
    target: MutableDeviceState<Amount<U>>,
): Flow<DiscreteFlowPackage<U>> {
//    require(target in model.states) { "Target state is not registered in the state container" }

    val mutex = Mutex()

    val buffer = ArrayDeque<DiscreteFlowPackage<U>>(100)

    model.launch {
        var mark = model.clock.now()
        while (isActive) {
            delay(model.discretization)
            mutex.withLock {
                val sum = buffer.sumOf { it.amount.value }
                buffer.clear()
                val now = model.clock.now()
                val duration = now - mark
                target.value = NumericalValue(sum / (duration / 1.seconds))
                mark = now
            }
        }
    }

    return onEach {
        mutex.withLock {
            buffer.add(it)
        }
    }
}

/**
 * Limits input of the incoming flow to [limit] per second
 */
internal fun <U : UnitsOfMeasurement> Flow<DiscreteFlowPackage<U>>.limitFlow(
    model: DiscreteFlowModel,
    limit: suspend () -> NumericalValue<U>
): Flow<DiscreteFlowPackage<U>> = flow {
    var accumulated: Double = 0.0
    val lock = Mutex(true)

    val lockDiscretization = model.discretization / 2

    model.launch {
        while (isActive) {
            delay(lockDiscretization)
            accumulated = 0.0
            if (lock.isLocked) {
                lock.unlock()
            }
        }
    }

    collect {
        if (accumulated + it.amount.value > limit().value * (lockDiscretization / 1.seconds)) {
            //ensure the lock is locked and wait for unlock
            lock.tryLock()
            lock.lock()
        }

        accumulated += it.amount.value
        emit(it)
    }

}

/**
 * A consumer for discrete material flow
 */
public class DiscreteConsumer<U : UnitsOfMeasurement>(
    override val model: DiscreteFlowModel,
    public val capacity: DeviceState<NumericalValue<U>>,
    public var target: DiscreteActor<U>? = null
) : DiscreteActor<U> {

    private val channel = Channel<DiscreteFlowPackage<U>>()

    override suspend fun emit(value: DiscreteFlowPackage<U>) {
        channel.send(value)
    }

    private val _consumation = MutableDeviceState<Amount<U>>(NumericalValue(0.0))

    private val collectionJob = channel.consumeAsFlow()
        .limitFlow(model) { capacity.value }
        .measureFlow(model, _consumation)
        .onEach {
            //try emitting package down the line if target is defined
            target?.emit(it)
        }
        .launchIn(model)

    override val consumation: DeviceState<Amount<U>> get() = _consumation
}

public class DiscreateProducer<U : UnitsOfMeasurement>(
    public val model: DiscreteFlowModel,
    public val capacity: DeviceState<NumericalValue<U>>,
    public var target: DiscreteActor<U>,
    private val packagesPerPeriod: Double = 100.0,
) {
    private val _production = MutableDeviceState<Amount<U>>(NumericalValue(0.0))

    public val production: DeviceState<Amount<U>> get() = _production

    private val productionJob = flow {
        val step = model.discretization / packagesPerPeriod
        while (true) {
            val amountPerPackage = capacity.value * (step / 1.seconds)
            emit(DiscreteFlowPackage(amountPerPackage, model.clock.now()))
            delay(step)
        }
    }.onEach {
        target.emit(it)
    }.measureFlow(model, _production).launchIn(model)
}
