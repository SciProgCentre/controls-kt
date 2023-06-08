package space.kscience.controls.misc

import kotlinx.datetime.Instant
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.long

// TODO move to core

public fun Instant.toMeta(): Meta = Meta {
    "seconds" put epochSeconds
    "nanos" put nanosecondsOfSecond
}

public fun Meta.instant(): Instant = value?.long?.let { Instant.fromEpochMilliseconds(it) } ?: Instant.fromEpochSeconds(
    get("seconds")?.long ?: 0L,
    get("nanos")?.long ?: 0L,
)