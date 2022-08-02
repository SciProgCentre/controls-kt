package space.kscience.controls.spec

import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.enum
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.transformations.MetaConverter
import space.kscience.dataforge.values.asValue
import space.kscience.dataforge.values.double
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

public fun Double.asMeta(): Meta = Meta(asValue())

//TODO to be moved to DF
public object DurationConverter : MetaConverter<Duration> {
    override fun metaToObject(meta: Meta): Duration = meta.value?.double?.toDuration(DurationUnit.SECONDS)
        ?: run {
            val unit: DurationUnit = meta["unit"].enum<DurationUnit>() ?: DurationUnit.SECONDS
            val value = meta[Meta.VALUE_KEY].double ?: error("No value present for Duration")
            return@run value.toDuration(unit)
        }

    override fun objectToMeta(obj: Duration): Meta = obj.toDouble(DurationUnit.SECONDS).asMeta()
}

public val MetaConverter.Companion.duration: MetaConverter<Duration> get() = DurationConverter