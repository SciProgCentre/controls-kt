package space.kscience.controls.utilities

import kotlinx.serialization.Serializable
import space.kscience.controls.api.DeviceFactory
import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.BoundStateHolder.Companion.DEFAULT_INPUT_NAME
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.descriptors.node
import space.kscience.dataforge.meta.descriptors.required
import space.kscience.dataforge.meta.descriptors.value
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.getIndexedList
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
    val lowerThreshold: Double? = null,
    val upperThreshold: Double? = null,
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


    /**
     * DataForge does not validate indexed siblings; the converter and [AlarmSetting] validate individual rules.
     */
    public object Spec : MetaSpec() {
        public val settings: MetaRef<List<AlarmSetting>> by item(settingsConverter) {
            node("setting") {
                multiple = true
                value("lowerThreshold", ValueType.NUMBER, ValueType.NULL)
                value("upperThreshold", ValueType.NUMBER, ValueType.NULL)
                value("status", ValueType.STRING) { required() }
            }
        }

        public val metadata: MetaRef<Meta> by metaItem(METADATA_KEY.parseAsName())
    }

    public companion object : DeviceFactory {
        public const val STATUS_OK: String = "OK"
        public const val STATUS_UNDEFINED: String = "@UNDEFINED"

        override val descriptor: MetaDescriptor get() = Spec.descriptor

        public val settingsConverter: MetaConverter<List<AlarmSetting>> = object : MetaConverter<List<AlarmSetting>> {
            private val settingSerializer = MetaConverter.serializable<AlarmSetting>()

            override fun readOrNull(source: Meta): List<AlarmSetting> =
                source.getIndexedList(Name.of("setting")).map { settingSerializer.read(it) }

            override fun convert(obj: List<AlarmSetting>): Meta = Meta {
                setIndexed(Name.of("setting"), obj.map { settingSerializer.convert(it) })
            }
        }

        /**
         * Create an unbound alarm from settings and optional metadata.
         */
        override fun buildDevice(
            context: Context,
            meta: Meta
        ): Alarm {
            val settings = meta[Spec.settings]

            val metadata = meta[Spec.metadata] ?: Meta.EMPTY

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
