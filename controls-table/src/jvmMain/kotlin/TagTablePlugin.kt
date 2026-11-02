package space.kscience.controls.tagtable

import space.kscience.controls.api.id
import space.kscience.controls.constructor.ConstructorPlugin
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.ValueStateFactory
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.storage.ControlsStoragePlugin
import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.names.Name

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

    public val tagTables: Map<String?, TagTable>
        field = mutableMapOf<String?, TagTable>()

    /**
     * Register [tagTable] without starting it. Register tables before the first factory resolution in [ConstructorPlugin].
     */
    public fun register(tagTable: TagTable, name: String? = null): TagTable {
        tagTables[name] = tagTable
        return tagTable
    }

    /**
     * register and start up [tagTable] with optional [name] (by default uses empty name).
     */
    public fun install(tagTable: TagTable, name: String? = null): TagTable {
        register(tagTable, name)
        deviceManager.install(name ?: tagTable.id, tagTable)
        return tagTable
    }

    public fun install(configuration: TagTableConfiguration, name: String? = null): TagTable =
        install(PlcTagTable(context, configuration), name)

    public object ValueFactorySpec : MetaSpec() {
        public val table: MetaRef<String> by string()
        public val tag: MetaRef<String> by string()
    }

    /**
     * Get a value state factory providing value states in this plugin
     */
    public val valueStateFactory: ValueStateFactory = object : ValueStateFactory {
        override fun build(context: Context, meta: Meta): ValueState<Meta> {
            val tableName = meta[ValueFactorySpec.table] ?: DEFAULT_TABLE
            val tag = meta[ValueFactorySpec.tag] ?: error("No tag specified")
            val table = tagTables[tableName] ?: error("Table with name $tableName not found")
            return table.subscribe(tag)
        }

        override val descriptor: MetaDescriptor get() = ValueFactorySpec.descriptor
    }

    override fun content(target: String): Map<Name, Any> = when (target) {

        ValueStateFactory.PROVIDER_TAGET -> mapOf(
            Name.of(TagTable.TAG_TABLE_FACTORY_TYPE) to valueStateFactory
        )

        DeviceManager.DEVICE_FACTORY_TARGET -> mapOf(
            Name.of(TagTable.TAG_TABLE_FACTORY_TYPE) to TagTable
        )

        else -> super.content(target)
    }


    public companion object : PluginFactory<TagTablePlugin> {
        override val tag: PluginTag = PluginTag("controls.tags")

        public val DEFAULT_TABLE: String? = null

        override fun build(context: Context, meta: Meta): TagTablePlugin = TagTablePlugin()
    }
}
