package hep.dataforge.control.base

import hep.dataforge.control.api.ActionDescriptor
import hep.dataforge.meta.MetaBuilder
import hep.dataforge.meta.MetaItem
import hep.dataforge.values.Value
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty


private fun <D : DeviceBase> D.provideAction(): ReadOnlyProperty<D, Action> =
    ReadOnlyProperty { _: D, property: KProperty<*> ->
        val name = property.name
        return@ReadOnlyProperty actions[name]!!
    }

public typealias ActionDelegate = ReadOnlyProperty<DeviceBase, Action>

private class ActionProvider<D : DeviceBase>(
    val owner: D,
    val descriptorBuilder: ActionDescriptor.() -> Unit = {},
    val block: suspend (MetaItem<*>?) -> MetaItem<*>?,
) : PropertyDelegateProvider<D, ActionDelegate> {
    override operator fun provideDelegate(thisRef: D, property: KProperty<*>): ActionDelegate {
        val name = property.name
        owner.newAction(name, descriptorBuilder, block)
        return owner.provideAction()
    }
}

public fun DeviceBase.requesting(
    descriptorBuilder: ActionDescriptor.() -> Unit = {},
    block: suspend (MetaItem<*>?) -> MetaItem<*>?,
): PropertyDelegateProvider<DeviceBase, ActionDelegate> = ActionProvider(this, descriptorBuilder, block)

public fun <D : DeviceBase> D.requestingValue(
    descriptorBuilder: ActionDescriptor.() -> Unit = {},
    block: suspend (MetaItem<*>?) -> Any?,
): PropertyDelegateProvider<D, ActionDelegate> = ActionProvider(this, descriptorBuilder) {
    val res = block(it)
    MetaItem.ValueItem(Value.of(res))
}

public fun <D : DeviceBase> D.requestingMeta(
    descriptorBuilder: ActionDescriptor.() -> Unit = {},
    block: suspend MetaBuilder.(MetaItem<*>?) -> Unit,
): PropertyDelegateProvider<D, ActionDelegate> = ActionProvider(this, descriptorBuilder) {
    val res = MetaBuilder().apply { block(it) }
    MetaItem.NodeItem(res)
}

public fun DeviceBase.acting(
    descriptorBuilder: ActionDescriptor.() -> Unit = {},
    block: suspend (MetaItem<*>?) -> Unit,
): PropertyDelegateProvider<DeviceBase, ActionDelegate> = ActionProvider(this, descriptorBuilder) {
    block(it)
    null
}