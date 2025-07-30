package space.kscience.controls.constructor.models.continuous

import kotlinx.coroutines.delay
import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.*
import space.kscience.dataforge.context.Context
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A simulation model that represents a continuous buffer for storing a quantity of a measurable unit.
 * The buffer dynamically responds to supply and consumption requests while ensuring constraints such as buffer capacity.
 *
 * @param U The unit of measurement associated with the buffer contents.
 * @param T The amount type representing the measurable quantity stored in the buffer.
 * @param context The simulation context in which the buffer operates.
 * @param algebra The algebraic operations to manipulate the amount type.
 * @param bufferCapacity The maximum capacity of the buffer as a device state.
 * @param supplyRequest A device state representing the amount requested to be added to the buffer.
 * @param consumerRequest A device state representing the amount requested to be consumed from the buffer.
 * @param initialLevel The initial quantity stored in the buffer.
 * @param timeStep The simulation time step for updating the buffer's state.
 *
 * The model tracks the current level of stored content, calculates remaining buffer space, and manages production and consumption dynamics.
 * It ensures the buffer's level stays within the bounds defined by its capacity.
 */
public class ContinuousBuffer<U : UnitsOfMeasurement, T : Amount<U>>(
    context: Context,
    public val algebra: AmountAlgebra<U, T>,
    public val bufferCapacity: DeviceState<Numeric<U>>,
    override val supplyRequest: LateBindDeviceState<T> = LateBindDeviceState(context,algebra.zero),
    override val consumerRequest: LateBindDeviceState<Numeric<U>> = LateBindDeviceState(context,Numeric.zero()),
    initialLevel: T = algebra.zero,
    timeStep: Duration = 1.seconds
) : ModelConstructor(context), ContinuousProducerInterface<U, T>, ContinuousConsumerInterface<U, T> {

    private val _content: MutableDeviceState<T> = MutableDeviceState(initialLevel)

    public val content: DeviceState<T> get() = _content

    init {
        registerState(bufferCapacity)
        registerState(content)
        registerState(supplyRequest)
        registerState(consumerRequest)
    }

    override val productionCapacity: DeviceState<T> = combineState(
        supplyRequest,
        content
    ) { supplyRequest: T, content: T ->
        with(algebra) {
            supplyRequest + content
        }
    }

    override val production: DeviceState<T> = combineState(
        supplyRequest,
        content,
        consumerRequest
    ) { supplyRequest, content, consumeRequest ->
        with(algebra) {
            val productionCapacity = supplyRequest + content
            productionCapacity.coerceValueIn(Numeric.zero<U>()..consumeRequest)
        }
    }

    override val consumationCapacity: DeviceState<Numeric<U>> = combineState(
        bufferCapacity,
        content,
        consumerRequest
    ) { bufferSize, content, consumationRequest: Numeric<U> ->
        val remainingSpace = bufferSize - Numeric<U>(content.value)
        remainingSpace + consumationRequest
    }

    override val consumation: DeviceState<T> = combineState(
        supplyRequest,
        bufferCapacity,
        content,
        consumerRequest
    ) { supplyRequest: T, bufferCapacity, content, consumationRequest ->
        with(algebra) {
            val remainingSpace = bufferCapacity - Numeric<U>(content.value)
            val consumationCapacity = remainingSpace + consumationRequest
            supplyRequest.coerceValueIn(Numeric.zero<U>()..consumationCapacity)
        }
    }

    private val levelChange = onTimer(
        tick = timeStep,
        reads = listOf(production, consumation, bufferCapacity),
        writes = listOf(content)
    ) { prev, next ->
        with(algebra) {
            delay(timeStep)

            val delta = consumation.value - production.value

            _content.value = (_content.value + delta * (timeStep / 1.seconds))
                .coerceValueIn(Numeric.zero<U>()..bufferCapacity.value)
        }
    }
}

/**
 * Creates a [ContinuousBuffer] model that represents a buffer for storing and managing
 * quantities of a measurable unit in a simulation context. The buffer ensures that its
 * contents respect the given capacity constraints and dynamically manages supply requests.
 *
 * @param context The simulation context in which the buffer operates.
 * @param capacity The maximum capacity of the buffer, represented as a [Numeric] value.
 * @param supplyRequest A device state representing the amount requested to be supplied
 *        to the buffer. Defaults to a [LateBindDeviceState] with a value of zero.
 * @return A [ContinuousBuffer] instance configured with the specified parameters.
 */
public fun <U : UnitsOfMeasurement> ContinuousBuffer(
    context: Context,
    capacity: Numeric<U>,
): ContinuousBuffer<U, Numeric<U>> = ContinuousBuffer(
    context,
    NumericAmountAlgebra<U>(),
    DeviceState(capacity)
)

public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousFlowModel.buffer(
    algebra: AmountAlgebra<U, T>,
    bufferCapacity: Numeric<U>,
    initialLevel: T = algebra.zero,
    timeStep: Duration = 1.seconds
): ContinuousBuffer<U, T> = model(
    ContinuousBuffer(
        context = context,
        algebra = algebra,
        bufferCapacity = DeviceState(bufferCapacity),
        initialLevel = initialLevel,
        timeStep = timeStep
    )
)