package space.kscience.controls.misc

import io.ktor.utils.io.core.*
import kotlinx.datetime.Instant
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.io.IOFormat
import space.kscience.dataforge.io.IOFormatFactory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import kotlin.reflect.KType
import kotlin.reflect.typeOf


/**
 * An [IOFormat] for [Instant]
 */
public object InstantIOFormat : IOFormat<Instant>, IOFormatFactory<Instant> {
    override fun build(context: Context, meta: Meta): IOFormat<Instant> = this

    override val name: Name = "instant".asName()

    override val type: KType get() = typeOf<Instant>()

    override fun writeObject(output: Output, obj: Instant) {
        output.writeLong(obj.epochSeconds)
        output.writeInt(obj.nanosecondsOfSecond)
    }

    override fun readObject(input: Input): Instant {
        val seconds = input.readLong()
        val nanoseconds = input.readInt()
        return Instant.fromEpochSeconds(seconds, nanoseconds)
    }
}

public fun Instant.toMeta(): Meta = Meta(toString())

public val Meta.instant: Instant? get() = value?.string?.let { Instant.parse(it) }