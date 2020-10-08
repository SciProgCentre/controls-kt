package hep.dataforge.control.base

import hep.dataforge.control.api.ActionDescriptor
import hep.dataforge.meta.MetaItem

public interface DeviceAction {
    public val name: String
    public val descriptor: ActionDescriptor
    public suspend operator fun invoke(arg: MetaItem<*>? = null): MetaItem<*>?
}