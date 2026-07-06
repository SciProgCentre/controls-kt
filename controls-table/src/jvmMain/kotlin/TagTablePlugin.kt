package space.kscience.controls.tagtable

import kotlinx.coroutines.launch
import space.kscience.controls.constructor.ConstructorPlugin
import space.kscience.controls.constructor.ValueStateFactory
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.storage.ControlsStoragePlugin
import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.NameToken
import space.kscience.dataforge.names.asName

/**
 * A plugin that manages a collection of tag tables, allowing for the registration, initialization,
 * and management of `TagTable` instances with optional names. It integrates with the `DeviceManager`
 * and `ConstructorPlugin` to provide seamless plugin-based functionality.
 */
public class TagTablePlugin : AbstractPlugin() {
    public val deviceManager: DeviceManager by require(DeviceManager)
    public val constructor: ConstructorPlugin by require(ConstructorPlugin)
    public val storage: ControlsStoragePlugin by require(ControlsStoragePlugin)

    override val tag: PluginTag get() = Companion.tag

    private val _tagTables = mutableMapOf<String?, TagTable>()

    public val tagTables: Map<String?, TagTable> get() = _tagTables

    /**
     * register and start up [tagTable] with optional [name] (by default uses empty name).
     */
    public fun install(tagTable: TagTable, name: String? = null): TagTable {
        _tagTables[name] = tagTable
        context.launch {
            tagTable.start()
        }
        return tagTable
    }

    public fun install(configuration: PlcTableConfiguration, name: String? = null): TagTable =
        install(PlcTagTable(context, configuration), name)

    override fun content(target: String): Map<Name, Any> = when (target) {
        ValueStateFactory.PROVIDER_TAGET -> tagTables.entries.associate { (name, table) ->
            NameToken(TagTable.TAG_TABLE_FACTORY_TYPE, name).asName() to table
        }

        DeviceManager.DEVICE_FACTORY_TARGET -> mapOf(
            TagTable.TAG_TABLE_FACTORY_TYPE.asName() to TagTableDevice
        )

        else -> super.content(target)
    }


    public companion object : PluginFactory<TagTablePlugin> {
        override val tag: PluginTag = PluginTag("controls.tags")

        override fun build(context: Context, meta: Meta): TagTablePlugin = TagTablePlugin()
    }
}