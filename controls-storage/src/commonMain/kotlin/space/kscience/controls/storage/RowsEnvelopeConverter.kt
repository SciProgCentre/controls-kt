package space.kscience.controls.storage

import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.meta.Meta
import space.kscience.tables.Rows

/**
 * Table to Envelope converter.
 */
public interface RowsEnvelopeConverter<T> {

    public val envelopeType: String

    /**
     * Reads a table of rows from the given envelope.
     *
     * This method processes the provided envelope and extracts rows of data in a structured format.
     *
     * @param envelope The envelope containing the rows of data to be read.
     * @return A `Rows` instance containing the data read from the envelope.
     */
    public fun readRows(envelope: Envelope): Rows<T>

    /**
     * Converts a table of rows into an envelope format, optionally including metadata.
     *
     * @param rows The table of rows to be converted.
     * @param meta Optional metadata to be included in the envelope. Defaults to an empty metadata object.
     * @return An envelope created from the provided rows and metadata.
     */
    public fun writeRows(rows: Rows<T>, meta: Meta = Meta.EMPTY): Envelope

    public companion object {
        public const val ROWS_ENVELOPE_CONVERTER_TARGET: String = "enveolope.rows"
    }
}

