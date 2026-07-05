package space.kscience.controls.storage

import space.kscience.dataforge.context.*
import space.kscience.dataforge.io.IOPlugin
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName

public class ControlsStoragePlugin : AbstractPlugin() {
    public val io: IOPlugin by require(IOPlugin)

    override val tag: PluginTag get() = Companion.tag

    public val rowEnvelopeConverters: Map<String, RowsEnvelopeConverter<Meta>> by lazy {
        context.gather<RowsEnvelopeConverter<Meta>>(RowsEnvelopeConverter.ROWS_ENVELOPE_CONVERTER_TYPE).values.associateBy { it.envelopeType }
    }

    override fun content(target: String): Map<Name, Any> = when (target) {
        RowsEnvelopeConverter.ROWS_ENVELOPE_CONVERTER_TYPE -> mapOf(
            "zip".asName() to ZipRowsEnvelopeConverter.meta,
            "plain".asName() to PlainRowsEnvelopeConverter.meta
        )

        else -> super.content(target)
    }


    public companion object : PluginFactory<ControlsStoragePlugin> {
        override val tag: PluginTag = PluginTag("controls.storage")

        override fun build(
            context: Context,
            meta: Meta
        ): ControlsStoragePlugin = ControlsStoragePlugin()

    }
}