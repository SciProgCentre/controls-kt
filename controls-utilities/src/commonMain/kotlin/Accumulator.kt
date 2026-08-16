package space.kscience.controls.utilities

/*
 * LLM generated code: Accumulator virtual device that integrates values from ValueState<Double?> over a given window.
 */

import kotlinx.coroutines.CoroutineScope
import space.kscience.controls.api.DeviceFactory
import space.kscience.controls.api.resolveDevice
import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.expressions.integrate
import space.kscience.controls.constructor.propertyAsState
import space.kscience.controls.constructor.registerProperty
import space.kscience.controls.duration
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.nullable
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
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

            val deviceManager = context.plugins[DeviceManager] ?: error("DeviceManager plugin not found")

            val state = deviceManager.resolveDevice(deviceName)
                .propertyAsState(propertyName, MetaConverter.double.nullable(), null)

            return Accumulator(context, state, window)
        }
    }
}
