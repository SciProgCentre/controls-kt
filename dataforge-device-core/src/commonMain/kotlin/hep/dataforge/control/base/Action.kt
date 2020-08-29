package hep.dataforge.control.base

import hep.dataforge.control.api.ActionDescriptor
import hep.dataforge.meta.MetaBuilder
import hep.dataforge.meta.MetaItem
import hep.dataforge.values.Value
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

interface Action {
    val name: String
    val descriptor: ActionDescriptor
    suspend operator fun invoke(arg: MetaItem<*>? = null): MetaItem<*>?
}

private fun DeviceBase.actionExecuted(action: String, argument: MetaItem<*>?, result: MetaItem<*>?){
    notifyListeners { actionExecuted(action, argument, result) }
}

/**
 * A stand-alone action
 */
class IsolatedAction(
    override val name: String,
    override val descriptor: ActionDescriptor,
    val callback: (action: String, argument: MetaItem<*>?, result: MetaItem<*>?) -> Unit,
    val block: suspend (MetaItem<*>?) -> MetaItem<*>?
) : Action {
    override suspend fun invoke(arg: MetaItem<*>?): MetaItem<*>? = block(arg).also {
        callback(name, arg, it)
    }
}

class ActionDelegate<D : DeviceBase>(
    val owner: D,
    val descriptorBuilder: ActionDescriptor.() -> Unit = {},
    val block: suspend (MetaItem<*>?) -> MetaItem<*>?
) : ReadOnlyProperty<D, Action> {
    override fun getValue(thisRef: D, property: KProperty<*>): Action {
        val name = property.name
        return owner.registerAction(name) {
            IsolatedAction(name, ActionDescriptor(name).apply(descriptorBuilder), owner::actionExecuted, block)
        }
    }
}

fun <D : DeviceBase> D.request(
    descriptorBuilder: ActionDescriptor.() -> Unit = {},
    block: suspend (MetaItem<*>?) -> MetaItem<*>?
): ActionDelegate<D> = ActionDelegate(this, descriptorBuilder, block)

fun <D : DeviceBase> D.requestValue(
    descriptorBuilder: ActionDescriptor.() -> Unit = {},
    block: suspend (MetaItem<*>?) -> Any?
): ActionDelegate<D> = ActionDelegate(this, descriptorBuilder) {
    val res = block(it)
    MetaItem.ValueItem(Value.of(res))
}

fun <D : DeviceBase> D.requestMeta(
    descriptorBuilder: ActionDescriptor.() -> Unit = {},
    block: suspend MetaBuilder.(MetaItem<*>?) -> Unit
): ActionDelegate<D> = ActionDelegate(this, descriptorBuilder) {
    val res = MetaBuilder().apply { block(it) }
    MetaItem.NodeItem(res)
}

fun <D : DeviceBase> D.action(
    descriptorBuilder: ActionDescriptor.() -> Unit = {},
    block: suspend (MetaItem<*>?) -> Unit
): ActionDelegate<D> = ActionDelegate(this, descriptorBuilder) {
    block(it)
    null
}