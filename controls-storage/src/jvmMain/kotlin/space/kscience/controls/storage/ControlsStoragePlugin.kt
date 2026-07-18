package space.kscience.controls.storage

import space.kscience.dataforge.context.*
import space.kscience.dataforge.io.IOPlugin
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name

public class ControlsStoragePlugin : AbstractPlugin() {
    public val io: IOPlugin by require(IOPlugin)

    override val tag: PluginTag get() = Companion.tag

    public val rowEnvelopeConverters: Map<String, RowsEnvelopeConverter<Meta>> by lazy {
        context.gather<RowsEnvelopeConverter<Meta>>(RowsEnvelopeConverter.ROWS_ENVELOPE_CONVERTER_TARGET).values.associateBy { it.envelopeType }
    }

    override fun content(target: String): Map<Name, Any> = when (target) {
        RowsEnvelopeConverter.ROWS_ENVELOPE_CONVERTER_TARGET -> mapOf(
            Name.of("zip") to ZipRowsEnvelopeConverter.meta,
            Name.of("plain") to PlainRowsEnvelopeConverter.meta
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