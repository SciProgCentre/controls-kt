package space.kscience.controls.storage.exposed

import org.jetbrains.exposed.v1.jdbc.Database
import space.kscience.controls.storage.ControlsStoragePlugin
import space.kscience.controls.storage.StorageFactory
import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName

public class ExposedPlugin : AbstractPlugin() {
    override val tag: PluginTag get() = Companion.tag

    private val _databases = mutableMapOf<String, ExposedDeviceMessageStorage>()

    public val databases: Map<String, ExposedDeviceMessageStorage> get() = _databases

    /**
     * Manually register a storage in the plugin
     *
     * This method is useful for custom DB configurations.
     */
    public fun registerExposedStorage(name: String, storage: ExposedDeviceMessageStorage) {
        _databases[name] = storage
    }

    /**
     * Manually register a database in the plugin
     */
    public fun registerDatabase(name: String, database: Database, pageSize: Int = 1000) {
        _databases[name] = ExposedDeviceMessageStorage(database, pageSize)
    }

    /**
     * A factory that creates and caches [ExposedDeviceMessageStorage]. If the factory is called with the same parameters,
     * it returns value from the cache.
     *
     * If `databaseName` meta field is provided and appropriate storage is in the cache, it is returned, ignoring other parameters.
     *
     * The following parameters are accepted:
     * - `url` - (required) JDBC URL of the database
     * - `driver` - JDBC driver class name
     * - `user` - database username
     * - `password` - database user password
     * - `pageSize` - the number of rows to be loaded at once from the database
     *
     */
    public val storageFactory: StorageFactory = StorageFactory { context, meta ->
        check(context === this.context) { "Storage context is not the same as the plugin context" }

        val databaseName = meta["database"].string ?: "database[${meta.hashCode().toHexString()}]"

        databases[databaseName]?.let { return@StorageFactory it }

        val url = meta["url"].string ?: error("Database URL is not specified")
        val driver = meta["driver"].string
        val user = meta["user"].string ?: ""
        val password = meta["password"].string ?: ""


        val database = if (driver != null) {
            Database.connect(
                url = url,
                driver = driver,
                user = user,
                password = password,
            )
        } else {
            Database.connect(
                url = url,
                user = user,
                password = password,
            )
        }

        val pageSize = meta["pageSize"].int ?: 1000
        ExposedDeviceMessageStorage(database, pageSize).also {
            _databases[databaseName] = it
        }
    }

    override fun content(target: String): Map<Name, Any> = when (target) {
        ControlsStoragePlugin.STORAGE_TARGET -> mapOf("exposed".asName() to storageFactory)
        else -> super.content(target)
    }

    public companion object : PluginFactory<ExposedPlugin> {
        override val tag: PluginTag = PluginTag("controls.storage.exposed", "controls")

        override fun build(context: Context, meta: Meta): ExposedPlugin = ExposedPlugin()
    }
}