package space.kscience.controls.utilities

import kotlinx.serialization.Serializable
import space.kscience.controls.api.DeviceFactory
import space.kscience.controls.api.resolveDevice
import space.kscience.controls.constructor.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.nullable
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.parseAsName

/**
 * Settings for alarm thresholds.
 *
 * @param lowerThreshold lower threshold. Could be undefined
 * @param upperThreshold upper threshold. Could be undefined
 * @param status status to be returned if the threshold is violated
 */
@Serializable
public data class AlarmSetting(
    val lowerThreshold: Double?,
    val upperThreshold: Double?,
    val status: String
) {
    init {
        require(lowerThreshold != null || upperThreshold != null) { "At least one threshold must be defined" }
    }
}

/**
 * The current state of alarm for property
 */
@Serializable
public data class AlarmState(
    val message: String,
    val value: Double?
)

/**
 * Special virtual device to produce multi-stage alarm with dynamic settings.
 */
public class Alarm(
    context: Context,
    private val value: ValueState<Double?>,
    meta: Meta = Meta.EMPTY
) : DeviceConstructor(context, meta) {

    /**
     * The list of alarm settings. Order of threshold matters. If two thresholds are violated simultaneously, the last wins.
     */
    public val alarmSettings: MutableValueState<List<AlarmSetting>> by virtualProperty(
        metaConverter = MetaConverter.serializable<List<AlarmSetting>>(),
        initialState = emptyList()
    )

    public val state: ValueState<AlarmState> = combineState(alarmSettings, value) { settings, value ->
        //early return undefined value if value is null
        if (value == null) return@combineState AlarmState(STATUS_UNDEFINED, null)


        var message = STATUS_OK

        settings.forEach { setting ->
            setting.lowerThreshold?.takeIf { value < it }?.let { message = setting.status }
            setting.upperThreshold?.takeIf { value > it }?.let { message = setting.status }
        }
        AlarmState(message, value)
    }


    init {
        registerProperty(
            name = "state",
            converter = MetaConverter.serializable<AlarmState>(),
            state = state
        )
    }


    public companion object : DeviceFactory {
        public const val STATUS_OK: String = "OK"
        public const val STATUS_UNDEFINED: String = "UNDEFINED"

        /**
         * Create an alarm for an existing device in [DeviceManager]
         */
        override fun buildDevice(
            context: Context,
            meta: Meta
        ): Alarm {
            val deviceName = meta["deviceName"].string?.parseAsName() ?: error("`deviceName` parameter not defined")
            val propertyName = meta["propertyName"].string ?: error("`propertyName` parameter not defined")

            val deviceManager = context.plugins[DeviceManager] ?: error("DeviceManager plugin not found")

            val state = deviceManager.resolveDevice(deviceName)
                .propertyAsState(propertyName, MetaConverter.double.nullable(), null)

            return Alarm(context, state, meta)
        }
    }
}