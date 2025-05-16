package space.kscience.controls.spec.utils

import kotlin.time.Duration

/**
 * Internal utility object for parsing operations.
 */
internal object ParsingUtils {
    /**
     * Parses a string to a [Duration], returning null on failure.
     */
    internal fun parseDurationOrNull(raw: String?): Duration? = raw?.trim()?.takeIf { it.isNotEmpty() }?.let {
        try {
            Duration.parse(it)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
