package space.kscience.controls.spec.runtime

import space.kscience.controls.spec.utils.deviceManagerConfig
import space.kscience.dataforge.context.Context

/**
 * Provides information about system resources relevant to device management,
 * primarily the configured default concurrency level from [DeviceHubConfig].
 */
public class SystemResourceInfo(private val context: Context) {
    /**
     * Returns the configured default concurrency level for device operations.
     */
    public fun getConcurrencyLevel(): Int = context.deviceManagerConfig.defaultConcurrencyLevel
}