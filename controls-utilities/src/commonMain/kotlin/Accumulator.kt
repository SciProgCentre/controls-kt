package space.kscience.controls.utilities

/*
 * LLM generated code: Accumulator virtual device that integrates values from ValueState<Double?> over a given window.
 */

import kotlinx.coroutines.CoroutineScope
import space.kscience.controls.api.DeviceFactory
import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.BoundStateHolder.Companion.DEFAULT_INPUT_NAME
import space.kscience.controls.constructor.expressions.integrate
import space.kscience.controls.duration
import space.kscience.controls.manager.DeviceManager
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.get
import kotlin.time.Duration

/**
 * Virtual device that integrates values from [value] state over given [window].
 * Null values are ignored during integration.
 *
 * @param context The context for the device.
 * @param window The time window duration over which to integrate values.
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

    public companion object : DeviceFactory {
        /**
         * Create an Accumulator for an existing device in [DeviceManager]
         */
        override fun buildDevice(
            context: Context,
            meta: Meta
        ): Accumulator {
            val window: Duration = meta["window"]?.let {
                MetaConverter.duration.read(it)
            } ?: error("`window` parameter not defined")

            val constructor = context.request(ConstructorPlugin)

            return Accumulator(context, window)
        }
    }
}
