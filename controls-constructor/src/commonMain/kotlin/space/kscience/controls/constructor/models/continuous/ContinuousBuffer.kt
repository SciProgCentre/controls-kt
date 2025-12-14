package space.kscience.controls.constructor.models.continuous

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import space.kscience.dataforge.names.parseAsName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
 * @param externalTimer An optional timer to be used for dynamic level change calculations.
 *
 * The model tracks the current level of stored content, calculates remaining buffer space, and manages production and consumption dynamics.
 * It ensures the buffer's level stays within the bounds defined by its capacity.
 */
public class ContinuousBuffer<U : UnitsOfMatter, T : Amount<U>>(
    context: Context,
    override val consumerAlgebra: AmountAlgebra<U, T>,
    public val bufferCapacity: ValueState<NumericAmount<U>>,
    override val supplyRequest: LateBindValueState<PerSecond<U, T>> = LateBindValueState(consumerAlgebra.zero.perSecond),
    override val consumerRequest: LateBindValueState<AmountPerSecond<U>> = LateBindValueState(AmountPerSecond.zero()),
    initialLevel: T = consumerAlgebra.zero,
    externalTimer: TimerState? = null,
) : ModelConstructor(context),
    ContinuousProducer<U, T>,
    ContinuousConsumer<U, T> {

    override val producerAlgebra: AmountAlgebra<U, T> get() = consumerAlgebra

    private val _content: MutableValueState<T> = MutableValueState(initialLevel)

    public val content: ValueState<T> get() = _content

    init {
        registerState(content, "content".asName())
        registerState(bufferCapacity, "bufferCapacity".asName())
        registerState(supplyRequest, "supply.request".parseAsName(true))
        registerState(consumerRequest, "consumer.request".parseAsName(true))
    }

    override val productionCapacity: ValueState<PerSecond<U, T>> = combineState(
        first = supplyRequest,
        second = content,
        name = "production.capacity".parseAsName(true)
    ) { supplyRequest: PerSecond<U, T>, content: T ->
        with(consumerAlgebra) {
            supplyRequest + content.perSecond
        }
    }

    override val production: ValueState<PerSecond<U, T>> = combineState(
        first = supplyRequest,
        second = content,
        third = consumerRequest,
        name = "production".asName()
    ) { supplyRequest, content, consumeRequest ->
        with(consumerAlgebra) {
            val productionCapacity = supplyRequest + content.perSecond
            productionCapacity.coerceValueIn(NumericAmount.zero<U>()..consumeRequest)
        }
    }

    override val consumationCapacity: ValueState<AmountPerSecond<U>> = combineState(
        first = bufferCapacity,
        second = content,
        third = consumerRequest,
        name = "consumation.capacity".parseAsName(true)
    ) { bufferSize: NumericAmount<U>, content: T, consumationRequest: AmountPerSecond<U> ->
        AmountPerSecond(consumationRequest.value + bufferSize.value - content.value)
    }

    override val consumation: ValueState<PerSecond<U, T>> = combineState(
        first = supplyRequest,
        second = bufferCapacity,
        third = content,
        forth = consumerRequest,
        name = "consumation".asName()
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
 * @return A [ContinuousBuffer] instance configured with the specified parameters.
 */
public fun <U : UnitsOfMatter> ContinuousBuffer(
    context: Context,
    capacity: NumericAmount<U>,
): ContinuousBuffer<U, NumericAmount<U>> = ContinuousBuffer(
    context,
    NumericAmountAlgebra<U>(),
    ValueState(capacity)
)

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousFlowModel.buffer(
    algebra: AmountAlgebra<U, T>,
    bufferCapacity: NumericAmount<U>,
    initialLevel: T = algebra.zero,
    externalTimer: TimerState? = null,
    modelName: Name? = null
): ContinuousBuffer<U, T> = model(
    ContinuousBuffer(
        context = context,
        consumerAlgebra = algebra,
        bufferCapacity = ValueState(bufferCapacity),
        initialLevel = initialLevel,
        externalTimer = externalTimer,
    ),
    modelName
)