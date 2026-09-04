package space.kscience.controls.utilities

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import space.kscience.controls.api.DeviceFactory
import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.BoundStateHolder.Companion.DEFAULT_INPUT_NAME
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.descriptors.node
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

    public companion object : DeviceFactory, MetaSpec() {
        public const val STATUS_OK: String = "OK"
        public const val STATUS_UNDEFINED: String = "@UNDEFINED"

        /**
         * Settings are stored as indexed `setting` nodes. The descriptor is derived from the serializer;
         * nullable thresholds additionally allow explicit null. Indexed siblings are not validated by DataForge.
         */
        public val settingsConverter: MetaConverter<List<AlarmSetting>> = object : MetaConverter<List<AlarmSetting>> {
            private val settingSerializer = MetaConverter.serializable<AlarmSetting>()

            override val descriptor: MetaDescriptor = MetaDescriptor {
                node("setting", MetaDescriptor(serializer<AlarmSetting>()).copy(multiple = true))
            }

            override fun readOrNull(source: Meta): List<AlarmSetting> =
                source.getIndexedList(Name.of("setting")).map { settingSerializer.read(it) }

            override fun convert(obj: List<AlarmSetting>): Meta = Meta {
                setIndexed(Name.of("setting"), obj.map { settingSerializer.convert(it) })
            }
        }

        public val settings: MetaRef<List<AlarmSetting>> by item(settingsConverter)

        public val metadata: MetaRef<Meta> by metaItem(METADATA_KEY.parseAsName())

        override val descriptor: MetaDescriptor = super<MetaSpec>.descriptor
        /**
         * Create an unbound alarm from settings and optional metadata.
         */
        override fun buildDevice(
            context: Context,
            meta: Meta
        ): Alarm {
            val settings = meta[settings]

            val metadata = meta[metadata] ?: Meta.EMPTY

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
