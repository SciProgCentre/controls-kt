package space.kscience.controls.utilities

/*
 * LLM generated code: Accumulator virtual device that integrates values from ValueState<Double?> over a given window.
 */

import kotlinx.coroutines.CoroutineScope
import space.kscience.controls.api.DeviceFactory
import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.expressions.integrate
import space.kscience.controls.duration
import space.kscience.controls.manager.DeviceManager
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.names.parseAsName
import kotlin.time.Duration

/**
 * Virtual device that integrates values from [value] state over given [window].
 * Null values are ignored during integration.
 *
 * @param context The context for the device.
 * @param value The source numeric state (can contain null values).
 * @param window The time window duration over which to integrate values.
 */
public class Accumulator(
    context: Context,
    private val value: ValueState<Double?>,
    public val window: Duration,
    coroutineScope: CoroutineScope = context
) : DeviceConstructor(context) {

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
            val deviceName = meta["deviceName"].string?.parseAsName() ?: error("`deviceName` parameter not defined")
            val propertyName = meta["propertyName"].string ?: error("`propertyName` parameter not defined")
            val window: Duration = meta["window"]?.let {
                MetaConverter.duration.read(it)
            } ?: error("`window` parameter not defined")

            val constructor = context.request(ConstructorPlugin)

            val state = constructor.provideDevicePropertyState(deviceName, propertyName).map { it.double }

            return Accumulator(context, state, window)
        }
    }
}
