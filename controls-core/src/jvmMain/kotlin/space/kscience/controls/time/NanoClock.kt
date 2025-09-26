package space.kscience.controls.time

import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

internal actual fun resolveClock(meta: Meta): Clock? = when (meta["clock.mode"].string) {
    "jvm" -> NanoClock
    else -> null
}

public object NanoClock : Clock {
    override fun now(): Instant = java.time.Instant.now().toKotlinInstant()
}