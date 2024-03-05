package space.kscience.controls.spec

import space.kscience.dataforge.meta.*
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

public fun Double.asMeta(): Meta = Meta(asValue())

//TODO to be moved to DF
public object DurationConverter : MetaConverter<Duration> {
    override fun readOrNull(source: Meta): Duration = source.value?.double?.toDuration(DurationUnit.SECONDS)
        ?: run {
            val unit: DurationUnit = source["unit"].enum<DurationUnit>() ?: DurationUnit.SECONDS
            val value = source[Meta.VALUE_KEY].double ?: error("No value present for Duration")
            return@run value.toDuration(unit)
        }

    override fun convert(obj: Duration): Meta = obj.toDouble(DurationUnit.SECONDS).asMeta()
}

public val MetaConverter.Companion.duration: MetaConverter<Duration> get() = DurationConverter