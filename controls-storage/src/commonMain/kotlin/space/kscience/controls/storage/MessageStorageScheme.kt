package space.kscience.controls.storage

import space.kscience.controls.time.duration
import space.kscience.dataforge.meta.Scheme
import space.kscience.dataforge.meta.SchemeSpec
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.Name
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


/**
 * A description for configuration block of property
 */
public class MessageStorageScheme : Scheme() {
    public var storage: String by string { error("Storage name is required") }
    public var sampleInterval: Duration by duration { 10.seconds }

    public companion object : SchemeSpec<MessageStorageScheme>(::MessageStorageScheme) {
        public val MESSAGE_STORAGE_KEY: Name = Name.of("storage")
    }
}