package ru.mipt.npm.controls.base

import ru.mipt.npm.controls.api.ActionDescriptor
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaItem
import space.kscience.dataforge.meta.asMetaItem

public interface DeviceAction {
    public val name: String
    public val descriptor: ActionDescriptor
    public suspend operator fun invoke(arg: MetaItem? = null): MetaItem?
}

public suspend operator fun DeviceAction.invoke(meta: Meta): MetaItem? = invoke(meta.asMetaItem())