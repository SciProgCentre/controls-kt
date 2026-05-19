package space.kscience.controls.dataplatform.storage

import kotlinx.serialization.Serializable

/**
 * Configuration of storage targets
 */
@Serializable
public class PlatformStorageConfiguration(
    public val targets: Map<String, PlatformStorageTarget>
)

/**
 * An individual target for storing data (there could be several for each data platform)
 */
@Serializable
public sealed interface PlatformStorageTarget

/**
 * A file-based storage target
 */
@Serializable
public class CompressedFileStorageTarget(
    public val path: String,
    public val compression: RowsCompression? = null,
    public val splitByDate: Boolean = true,
) : PlatformStorageTarget

