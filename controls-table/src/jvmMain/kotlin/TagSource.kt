package space.kscience.controls.tagtable

import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.Meta

/**
 * An abstraction for a source of tag values
 */
public sealed interface TagSource : AutoCloseable {
    /**
     * Read the current value of the tag from the source. Return null if the source is not open.
     * Throw an exception if the source is active, but read failed.
     */
    public suspend fun readTag(address: Meta): ValueWithTime<Meta>?

    /**
     * Read multiple values from the source. Return null if the source is not open. This method fails if any property read fails.
     */
    public suspend fun readMultipleTags(
        addresses: Collection<Pair<String, Meta>>
    ): Map<String, ValueWithTime<Meta>>? = addresses.associate {
        val value = readTag(it.second) ?: return null
        it.first to value
    }

    /**
     * Throw an exception if the column is not valid for this source
     */
    public fun validate(column: TagTableColumn)

    /**
     * Open the source and throw an exception if it fails
     */
    public suspend fun open()

}

