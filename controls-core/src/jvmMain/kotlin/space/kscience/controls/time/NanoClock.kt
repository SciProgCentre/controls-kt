package space.kscience.controls.time

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string

internal actual fun resolveClock(meta: Meta): Clock? = when (meta["clock.mode"].string) {
    "jvm" -> NanoClock
    else -> null
}

public object NanoClock: Clock {
    override fun now(): Instant = java.time.Instant.now().toKotlinInstant()
}