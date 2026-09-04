package space.kscience.controls.utilities

import kotlinx.coroutines.CoroutineScope
import space.kscience.controls.api.DeviceFactory
import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.BoundStateHolder.Companion.DEFAULT_INPUT_NAME
import space.kscience.controls.constructor.expressions.integrate
import space.kscience.controls.duration
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.descriptors.required
import kotlin.time.Duration

/**
 * Virtual device that sums numeric samples from [value] over the given [window].
 * Null samples add nothing but still expire old samples and update the result timestamp.
 *
 * @param context The context for the device.
 * @param window The time window duration over which to sum samples.
 */
public class Accumulator(
    context: Context,
    public val window: Duration,
    coroutineScope: CoroutineScope = context
) : DeviceConstructor(context), BoundStateHolder {

    public val value: ValueState<Double?>
        field: LateBindValueState<Double?> = LateBindValueState<Double?>(null)

    override fun bind(state: ValueState<Meta>, inputName: String) {
        when (inputName) {
            DEFAULT_INPUT_NAME, "value" -> {
                value.bind(state.map { it.double })
            }

            else -> {
                error("Can't resolve input name $inputName in $this")
            }
        }
    }

    public val state: ValueState<Double> = value.integrate(window, coroutineScope)

    init {
        registerProperty(
            name = "state",
            converter = MetaConverter.double,
            state = state
        )
    }

    public companion object : DeviceFactory, MetaSpec() {

        public val window: MetaRef<Duration> by item(MetaConverter.duration) {
            valueType(ValueType.NUMBER, ValueType.STRING)
            required()
            description = "Accumulation window: seconds or duration string"
        }

        override val descriptor: MetaDescriptor = super<MetaSpec>.descriptor

        /**
         * Create an unbound accumulator from its required window parameter.
         */
        override fun buildDevice(
            context: Context,
            meta: Meta
        ): Accumulator {
            val window: Duration = meta[window] ?: error("`window` parameter not defined")

            return Accumulator(context, window)
        }
    }
}
