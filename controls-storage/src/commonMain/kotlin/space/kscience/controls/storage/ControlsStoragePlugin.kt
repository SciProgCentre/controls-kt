package space.kscience.controls.storage

import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name

public fun interface StorageFactory: Factory<DeviceMessageStorage>

/**
 * Injection point for storage factories
 */
public class ControlsStoragePlugin : AbstractPlugin() {
    override val tag: PluginTag get() = Companion.tag

    public val storageFactories: Map<Name, StorageFactory> by lazy { context.gather<StorageFactory>(STORAGE_TARGET) }

    public companion object : PluginFactory<ControlsStoragePlugin> {
        public const val STORAGE_TARGET: String = "storage"

        override val tag: PluginTag = PluginTag("controls.storage", "controls")

        override fun build(
            context: Context,
            meta: Meta
        ): ControlsStoragePlugin = ControlsStoragePlugin()

    }
}