package hep.dataforge.control.base

import hep.dataforge.control.api.ActionDescriptor
import hep.dataforge.meta.MetaBuilder
import hep.dataforge.meta.MetaItem
import hep.dataforge.meta.MetaItemNode
import hep.dataforge.meta.MetaItemValue
import hep.dataforge.values.Value
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
    val block: suspend (MetaItem?) -> MetaItem?,
) : PropertyDelegateProvider<D, ActionDelegate> {
    override operator fun provideDelegate(thisRef: D, property: KProperty<*>): ActionDelegate {
        val name = property.name
        owner.createAction(name, descriptorBuilder, block)
        return owner.provideAction()
    }
}

public fun DeviceBase.requesting(
    descriptorBuilder: ActionDescriptor.() -> Unit = {},
    action: suspend (MetaItem?) -> MetaItem?,
): PropertyDelegateProvider<DeviceBase, ActionDelegate> = ActionProvider(this, descriptorBuilder, action)

public fun <D : DeviceBase> D.requestingValue(
    descriptorBuilder: ActionDescriptor.() -> Unit = {},
    action: suspend (MetaItem?) -> Any?,
): PropertyDelegateProvider<D, ActionDelegate> = ActionProvider(this, descriptorBuilder) {
    val res = action(it)
    MetaItemValue(Value.of(res))
}

public fun <D : DeviceBase> D.requestingMeta(
    descriptorBuilder: ActionDescriptor.() -> Unit = {},
    action: suspend MetaBuilder.(MetaItem?) -> Unit,
): PropertyDelegateProvider<D, ActionDelegate> = ActionProvider(this, descriptorBuilder) {
    val res = MetaBuilder().apply { action(it) }
    MetaItemNode(res)
}

public fun DeviceBase.acting(
    descriptorBuilder: ActionDescriptor.() -> Unit = {},
    action: suspend (MetaItem?) -> Unit,
): PropertyDelegateProvider<DeviceBase, ActionDelegate> = ActionProvider(this, descriptorBuilder) {
    action(it)
    null
}