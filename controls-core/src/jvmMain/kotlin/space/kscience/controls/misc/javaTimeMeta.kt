package space.kscience.controls.misc

import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.long
import java.time.Instant

// TODO move to core

public fun Instant.toMeta(): Meta = Meta {
    "seconds" put epochSecond
    "nanos" put nano
}

public fun Meta.instant(): Instant = value?.long?.let { Instant.ofEpochMilli(it) } ?: Instant.ofEpochSecond(
    get("seconds")?.long ?: 0L,
    get("nanos")?.long ?: 0L,
)