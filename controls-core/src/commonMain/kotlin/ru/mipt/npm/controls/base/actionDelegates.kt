package ru.mipt.npm.controls.base

import ru.mipt.npm.controls.api.ActionDescriptor
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MutableMeta
import space.kscience.dataforge.values.Value
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty


private fun <D : DeviceBase> D.provideAction(): ReadOnlyProperty<D, DeviceAction> =
    ReadOnlyProperty { _: D, property: KProperty<*> ->
        val name = property.name
        return@ReadOnlyProperty actions[name]!!
    }

public typealias ActionDelegate = ReadOnlyProperty<DeviceBase, DeviceAction>

private class ActionProvider<D : DeviceBase>(
    val owner: D,
    val descriptorBuilder: ActionDescriptor.() -> Unit = {},
    val block: suspend (Meta?) -> Meta?,
) : PropertyDelegateProvider<D, ActionDelegate> {
    override operator fun provideDelegate(thisRef: D, property: KProperty<*>): ActionDelegate {
        val name = property.name
        owner.createAction(name, descriptorBuilder, block)
        return owner.provideAction()
    }
}

public fun DeviceBase.requesting(
    descriptorBuilder: ActionDescriptor.() -> Unit = {},
    action: suspend (Meta?) -> Meta?,
): PropertyDelegateProvider<DeviceBase, ActionDelegate> = ActionProvider(this, descriptorBuilder, action)

public fun <D : DeviceBase> D.requestingValue(
    descriptorBuilder: ActionDescriptor.() -> Unit = {},
    action: suspend (Meta?) -> Any?,
): PropertyDelegateProvider<D, ActionDelegate> = ActionProvider(this, descriptorBuilder) {
    val res = action(it)
    Meta(Value.of(res))
}

public fun <D : DeviceBase> D.requestingMeta(
    descriptorBuilder: ActionDescriptor.() -> Unit = {},
    action: suspend MutableMeta.(Meta?) -> Unit,
): PropertyDelegateProvider<D, ActionDelegate> = ActionProvider(this, descriptorBuilder) {
    Meta { action(it) }
}

public fun DeviceBase.acting(
    descriptorBuilder: ActionDescriptor.() -> Unit = {},
    action: suspend (Meta?) -> Unit,
): PropertyDelegateProvider<DeviceBase, ActionDelegate> = ActionProvider(this, descriptorBuilder) {
    action(it)
    null
}