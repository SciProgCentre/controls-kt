package hep.dataforge.control.base

import hep.dataforge.control.api.ActionDescriptor
import hep.dataforge.meta.Meta
import hep.dataforge.meta.MetaItem
import hep.dataforge.meta.asMetaItem

public interface DeviceAction {
    public val name: String
    public val descriptor: ActionDescriptor
    public suspend operator fun invoke(arg: MetaItem<*>? = null): MetaItem<*>?
}

public suspend operator fun DeviceAction.invoke(meta: Meta): MetaItem<*>? = invoke(meta.asMetaItem())