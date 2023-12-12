package space.kscience.controls.spec

import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.transformations.MetaConverter
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

public fun Double.asMeta(): Meta = Meta(asValue())

//TODO to be moved to DF
public object DurationConverter : MetaConverter<Duration> {
    override val type: KType = typeOf<Duration>()

    override fun metaToObjectOrNull(meta: Meta): Duration = meta.value?.double?.toDuration(DurationUnit.SECONDS)
        ?: run {
            val unit: DurationUnit = meta["unit"].enum<DurationUnit>() ?: DurationUnit.SECONDS
            val value = meta[Meta.VALUE_KEY].double ?: error("No value present for Duration")
            return@run value.toDuration(unit)
        }

    override fun objectToMeta(obj: Duration): Meta = obj.toDouble(DurationUnit.SECONDS).asMeta()
}

public val MetaConverter.Companion.duration: MetaConverter<Duration> get() = DurationConverter