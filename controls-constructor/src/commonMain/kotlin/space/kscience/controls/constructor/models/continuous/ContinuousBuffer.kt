package space.kscience.controls.constructor.models.continuous

import kotlinx.coroutines.delay
import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.*
import space.kscience.dataforge.context.Context
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A simulation model that represents a continuous buffer for storing a quantity of a measurable unit.
 * The buffer dynamically responds to supply and consumption requests while ensuring constraints such as buffer capacity.
 *
 * @param U The unit of measurement associated with the buffer contents.
 * @param T The amount type representing the measurable quantity stored in the buffer.
 * @param context The simulation context in which the buffer operates.
 * @param consumerAlgebra The algebraic operations to manipulate the amount type.
 * @param bufferCapacity The maximum capacity of the buffer as a device state.
 * @param supplyRequest A device state representing the amount requested to be added to the buffer.
 * @param consumerRequest A device state representing the amount requested to be consumed from the buffer.
 * @param initialLevel The initial quantity stored in the buffer.
 * @param timeStep The simulation time step for updating the buffer's state.
 *
 * The model tracks the current level of stored content, calculates remaining buffer space, and manages production and consumption dynamics.
 * It ensures the buffer's level stays within the bounds defined by its capacity.
 */
public class ContinuousBuffer<U : UnitsOfMatter, T : Amount<U>>(
    context: Context,
    override val consumerAlgebra: AmountAlgebra<U, T>,
    public val bufferCapacity: DeviceState<NumericAmount<U>>,
    override val supplyRequest: LateBindDeviceState<PerSecond<U, T>> = LateBindDeviceState(consumerAlgebra.zero.perSecond),
    override val consumerRequest: LateBindDeviceState<AmountPerSecond<U>> = LateBindDeviceState(AmountPerSecond.zero()),
    initialLevel: T = consumerAlgebra.zero,
    externalTimer: TimerState? = null,
) : ModelConstructor(context, bufferCapacity, supplyRequest, consumerRequest),
    ContinuousProducer<U, T>,
    ContinuousConsumer<U, T> {

    override val producerAlgebra: AmountAlgebra<U, T> get() = consumerAlgebra

    private val _content: MutableDeviceState<T> = MutableDeviceState(initialLevel)

    public val content: DeviceState<T> get() = _content

    init {
        registerState(content)
    }

    override val productionCapacity: DeviceState<PerSecond<U, T>> = combineState(
        supplyRequest,
        content
    ) { supplyRequest: PerSecond<U, T>, content: T ->
        with(consumerAlgebra) {
            supplyRequest + content.perSecond
        }
    }

    override val production: DeviceState<PerSecond<U, T>> = combineState(
        supplyRequest,
        content,
        consumerRequest
    ) { supplyRequest, content, consumeRequest ->
        with(consumerAlgebra) {
            val productionCapacity = supplyRequest + content.perSecond
            productionCapacity.coerceValueIn(NumericAmount.zero<U>()..consumeRequest)
        }
    }

    override val consumationCapacity: DeviceState<AmountPerSecond<U>> = combineState(
        bufferCapacity,
        content,
        consumerRequest,
    ) { bufferSize: NumericAmount<U>, content: T, consumationRequest: AmountPerSecond<U> ->
        AmountPerSecond(consumationRequest.value + bufferSize.value - content.value)
    }

    override val consumation: DeviceState<PerSecond<U, T>> = combineState(
        supplyRequest,
        bufferCapacity,
        content,
        consumerRequest
    ) { supplyRequest: PerSecond<U, T>, bufferCapacity: NumericAmount<U>, content: T, consumationRequest ->
        with(consumerAlgebra) {
            val remainingSpace = bufferCapacity - NumericAmount<U>(content.value)
            val consumationCapacity = AmountPerSecond<U>(remainingSpace.value + consumationRequest.value)
            supplyRequest.coerceValueIn(NumericAmount.zero<U>()..consumationCapacity)
        }
    }

    private val timer = externalTimer ?: timer(1.seconds)

    private val levelChange = onTimer(
        timer = timer,
        reads = listOf(production, consumation, bufferCapacity),
        writes = listOf(content)
    ) { prev, next ->
        with(consumerAlgebra) {
            val duration = next - prev
            require(duration >= Duration.ZERO) { "Negative time change" }

            val delta = consumation.value - production.value

            _content.emit(
                (_content.value + delta * duration)
                    .coerceValueIn(NumericAmount.zero<U>()..bufferCapacity.value)
            )
        }
    }
}

/**
 * Creates a [ContinuousBuffer] model that represents a buffer for storing and managing
 * quantities of a measurable unit in a simulation context. The buffer ensures that its
 * contents respect the given capacity constraints and dynamically manages supply requests.
 *
 * @param context The simulation context in which the buffer operates.
 * @param capacity The maximum capacity of the buffer, represented as a [PerSecond] value.
 * @param supplyRequest A device state representing the amount requested to be supplied
 *        to the buffer. Defaults to a [LateBindDeviceState] with a value of zero.
 * @return A [ContinuousBuffer] instance configured with the specified parameters.
 */
public fun <U : UnitsOfMatter> ContinuousBuffer(
    context: Context,
    capacity: NumericAmount<U>,
): ContinuousBuffer<U, NumericAmount<U>> = ContinuousBuffer(
    context,
    NumericAmountAlgebra<U>(),
    DeviceState(capacity)
)

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousFlowModel.buffer(
    algebra: AmountAlgebra<U, T>,
    bufferCapacity: NumericAmount<U>,
    initialLevel: T = algebra.zero,
    externalTimer: TimerState? = null,
): ContinuousBuffer<U, T> = model(
    ContinuousBuffer(
        context = context,
        consumerAlgebra = algebra,
        bufferCapacity = DeviceState(bufferCapacity),
        initialLevel = initialLevel,
        externalTimer = externalTimer,
    )
)