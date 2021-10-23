package ru.mipt.npm.controls.base

import ru.mipt.npm.controls.api.ActionDescriptor
import space.kscience.dataforge.meta.Meta

public interface DeviceAction {
    public val name: String
    public val descriptor: ActionDescriptor
    public suspend operator fun invoke(arg: Meta? = null): Meta?
}
