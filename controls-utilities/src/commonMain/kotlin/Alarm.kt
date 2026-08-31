package space.kscience.controls.utilities

import kotlinx.serialization.Serializable
import space.kscience.controls.api.DeviceFactory
import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.BoundStateHolder.Companion.DEFAULT_INPUT_NAME
import space.kscience.controls.manager.DeviceManager
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.getIndexedList

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
    settings: List<AlarmSetting> = emptyList(),
    meta: Meta = Meta.EMPTY
) : DeviceConstructor(context, meta), BoundStateHolder {

    /**
     * The list of alarm settings. Order of threshold matters. If two thresholds are violated simultaneously, the last wins.
     */
    public val alarmSettings: MutableValueState<List<AlarmSetting>> by virtualProperty(
        metaConverter = settingsConverter,
        initialState = settings
    )

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
        public const val STATUS_UNDEFINED: String = "@UNDEFINED"

        public val settingsConverter: MetaConverter<List<AlarmSetting>> = object : MetaConverter<List<AlarmSetting>> {
            private val settingSerializer = MetaConverter.serializable<AlarmSetting>()

            override fun readOrNull(source: Meta): List<AlarmSetting> =
                source.getIndexedList(Name.of("setting")).map { settingSerializer.read(it) }

            override fun convert(obj: List<AlarmSetting>): Meta = Meta {
                setIndexed(Name.of("setting"), obj.map { settingSerializer.convert(it) })
            }
        }

        /**
         * Create an alarm for an existing device in [DeviceManager]
         */
        override fun buildDevice(
            context: Context,
            meta: Meta
        ): Alarm {
            val settings = meta["settings"]?.let { settingsConverter.readOrNull(it) }

            val metadata = meta[METADATA_KEY] ?: Meta.EMPTY

            return Alarm(context, settings ?: emptyList(), metadata)
        }

        public fun buildDeviceMeta(settings: List<AlarmSetting>, metadata: Meta? = null): Meta = Meta {
            if (settings.isNotEmpty()) {
                "settings" put settingsConverter.convert(settings)
            }
            metadata?.let { METADATA_KEY put it }
        }
    }
}