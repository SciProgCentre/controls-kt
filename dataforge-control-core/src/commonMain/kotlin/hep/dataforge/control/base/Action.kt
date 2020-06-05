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

class SimpleAction(
    override val name: String,
    override val descriptor: ActionDescriptor,
    val block: suspend (MetaItem<*>?) -> MetaItem<*>?
) : Action {
    override suspend fun invoke(arg: MetaItem<*>?): MetaItem<*>? = block(arg)
}

class ActionDelegate<D : DeviceBase>(
    val owner: D,
    val descriptor: ActionDescriptor = ActionDescriptor.empty(),
    val block: suspend (MetaItem<*>?) -> MetaItem<*>?
) : ReadOnlyProperty<D, Action> {
    override fun getValue(thisRef: D, property: KProperty<*>): Action {
        val name = property.name
        return owner.resolveAction(name) {
            SimpleAction(name, descriptor, block)
        }
    }
}

fun <D : DeviceBase> D.request(
    descriptor: ActionDescriptor = ActionDescriptor.empty(),
    block: suspend (MetaItem<*>?) -> MetaItem<*>?
): ActionDelegate<D> = ActionDelegate(this, descriptor, block)

fun <D : DeviceBase> D.requestValue(
    descriptor: ActionDescriptor = ActionDescriptor.empty(),
    block: suspend (MetaItem<*>?) -> Any?
): ActionDelegate<D> = ActionDelegate(this, descriptor){
    val res = block(it)
    MetaItem.ValueItem(Value.of(res))
}

fun <D : DeviceBase> D.requestMeta(
    descriptor: ActionDescriptor = ActionDescriptor.empty(),
    block: suspend MetaBuilder.(MetaItem<*>?) -> Unit
): ActionDelegate<D> = ActionDelegate(this, descriptor){
    val res = MetaBuilder().apply { block(it)}
    MetaItem.NodeItem(res)
}

fun <D : DeviceBase> D.action(
    descriptor: ActionDescriptor = ActionDescriptor.empty(),
    block: suspend (MetaItem<*>?) -> Unit
): ActionDelegate<D> = ActionDelegate(this, descriptor) {
    block(it)
    null
}