package hep.dataforge.control.base

import hep.dataforge.meta.*
import hep.dataforge.meta.transformations.MetaConverter
import hep.dataforge.values.asValue
import hep.dataforge.values.double
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

public fun Double.asMetaItem(): MetaItemValue = MetaItemValue(asValue())

//TODO to be moved to DF
public object DurationConverter : MetaConverter<Duration> {
    override fun itemToObject(item: MetaItem): Duration = when (item) {
        is MetaItemNode -> {
            val unit: DurationUnit = item.node["unit"].enum<DurationUnit>() ?: DurationUnit.SECONDS
            val value = item.node[Meta.VALUE_KEY].double ?: error("No value present for Duration")
            value.toDuration(unit)
        }
        is MetaItemValue -> item.value.double.toDuration(DurationUnit.SECONDS)
    }

    override fun objectToMetaItem(obj: Duration): MetaItem = obj.toDouble(DurationUnit.SECONDS).asMetaItem()
}

public val MetaConverter.Companion.duration: MetaConverter<Duration> get() = DurationConverter