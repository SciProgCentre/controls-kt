package hep.dataforge.control.base

import hep.dataforge.meta.MetaItem
import hep.dataforge.values.asValue

fun Double.asMetaItem(): MetaItem.ValueItem = MetaItem.ValueItem(asValue())