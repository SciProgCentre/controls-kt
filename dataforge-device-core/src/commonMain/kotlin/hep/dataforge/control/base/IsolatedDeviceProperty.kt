package hep.dataforge.control.base

import hep.dataforge.control.api.PropertyDescriptor
import hep.dataforge.meta.*
import hep.dataforge.values.Null
import hep.dataforge.values.Value
import hep.dataforge.values.asValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

private fun DeviceBase.propertyChanged(name: String, item: MetaItem<*>?){
    notifyListeners { propertyChanged(name, item) }
}

/**
 * A stand-alone [ReadOnlyDeviceProperty] implementation not directly attached to a device
 */
@OptIn(ExperimentalCoroutinesApi::class)
public open class IsolatedReadOnlyDeviceProperty(
    override val name: String,
    default: MetaItem<*>?,
    override val descriptor: PropertyDescriptor,
    override val scope: CoroutineScope,
    private val callback: (name: String, item: MetaItem<*>) -> Unit,
    private val getter: suspend (before: MetaItem<*>?) -> MetaItem<*>
) : ReadOnlyDeviceProperty {

    private val state: MutableStateFlow<MetaItem<*>?> = MutableStateFlow(default)
    override val value: MetaItem<*>? get() = state.value

    override suspend fun invalidate() {
        state.value = null
    }

    override fun updateLogical(item: MetaItem<*>) {
        state.value = item
        callback(name, item)
    }

    override suspend fun read(force: Boolean): MetaItem<*> {
        //backup current value
        val currentValue = value
        return if (force || currentValue == null) {
            val res = withContext(scope.coroutineContext) {
                //all device operations should be run on device context
                //TODO add error catching
                getter(currentValue)
            }
            updateLogical(res)
            res
        } else {
            currentValue
        }
    }

    override fun flow(): StateFlow<MetaItem<*>?> = state
}

public fun DeviceBase.readOnlyProperty(
    name: String,
    default: MetaItem<*>?,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend (MetaItem<*>?) -> MetaItem<*>
): ReadOnlyDeviceProperty = registerProperty(name) {
    IsolatedReadOnlyDeviceProperty(
        name,
        default,
        PropertyDescriptor(name).apply(descriptorBuilder),
        scope,
        ::propertyChanged,
        getter
    )
}

private class ReadOnlyDevicePropertyDelegate<D : DeviceBase>(
    val owner: D,
    val default: MetaItem<*>?,
    val descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    private val getter: suspend (MetaItem<*>?) -> MetaItem<*>
) : ReadOnlyProperty<D, ReadOnlyDeviceProperty> {

    override fun getValue(thisRef: D, property: KProperty<*>): ReadOnlyDeviceProperty {
        val name = property.name

        return owner.registerProperty(name) {
            @OptIn(ExperimentalCoroutinesApi::class)
            IsolatedReadOnlyDeviceProperty(
                name,
                default,
                PropertyDescriptor(name).apply(descriptorBuilder),
                owner.scope,
                owner::propertyChanged,
                getter
            )
        }
    }
}

public fun <D : DeviceBase> D.reading(
    default: MetaItem<*>? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend (MetaItem<*>?) -> MetaItem<*>
): ReadOnlyProperty<D, ReadOnlyDeviceProperty> = ReadOnlyDevicePropertyDelegate(
    this,
    default,
    descriptorBuilder,
    getter
)

public fun <D : DeviceBase> D.readingValue(
    default: Value? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend () -> Any?
): ReadOnlyProperty<D, ReadOnlyDeviceProperty> = ReadOnlyDevicePropertyDelegate(
    this,
    default?.let { MetaItem.ValueItem(it) },
    descriptorBuilder,
    getter = { MetaItem.ValueItem(Value.of(getter())) }
)

public fun <D : DeviceBase> D.readingNumber(
    default: Number? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend () -> Number
): ReadOnlyProperty<D, ReadOnlyDeviceProperty> = ReadOnlyDevicePropertyDelegate(
    this,
    default?.let { MetaItem.ValueItem(it.asValue()) },
    descriptorBuilder,
    getter = {
        val number = getter()
        MetaItem.ValueItem(number.asValue())
    }
)

public fun <D : DeviceBase> D.readingString(
    default: Number? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend () -> String
): ReadOnlyProperty<D, ReadOnlyDeviceProperty> = ReadOnlyDevicePropertyDelegate(
    this,
    default?.let { MetaItem.ValueItem(it.asValue()) },
    descriptorBuilder,
    getter = {
        val number = getter()
        MetaItem.ValueItem(number.asValue())
    }
)

public fun <D : DeviceBase> D.readingMeta(
    default: Meta? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend MetaBuilder.() -> Unit
): ReadOnlyProperty<D, ReadOnlyDeviceProperty> = ReadOnlyDevicePropertyDelegate(
    this,
    default?.let { MetaItem.NodeItem(it) },
    descriptorBuilder,
    getter = {
        MetaItem.NodeItem(MetaBuilder().apply { getter() })
    }
)

@OptIn(ExperimentalCoroutinesApi::class)
public class IsolatedDeviceProperty(
    name: String,
    default: MetaItem<*>?,
    descriptor: PropertyDescriptor,
    scope: CoroutineScope,
    updateCallback: (name: String, item: MetaItem<*>?) -> Unit,
    getter: suspend (MetaItem<*>?) -> MetaItem<*>,
    private val setter: suspend (oldValue: MetaItem<*>?, newValue: MetaItem<*>) -> MetaItem<*>?
) : IsolatedReadOnlyDeviceProperty(name, default, descriptor, scope, updateCallback, getter), DeviceProperty {

    override var value: MetaItem<*>?
        get() = super.value
        set(value) {
            scope.launch {
                if (value == null) {
                    invalidate()
                } else {
                    write(value)
                }
            }
        }

    private val writeLock = Mutex()

    override suspend fun write(item: MetaItem<*>) {
        writeLock.withLock {
            //fast return if value is not changed
            if (item == value) return@withLock
            val oldValue = value
            //all device operations should be run on device context
            withContext(scope.coroutineContext) {
                //TODO add error catching
                setter(oldValue, item)?.let {
                    updateLogical(it)
                }
            }
        }
    }
}

public fun DeviceBase.mutableProperty(
    name: String,
    default: MetaItem<*>?,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend (MetaItem<*>?) -> MetaItem<*>,
    setter: suspend (oldValue: MetaItem<*>?, newValue: MetaItem<*>) -> MetaItem<*>?
): DeviceProperty = registerMutableProperty(name) {
    IsolatedDeviceProperty(
        name,
        default,
        PropertyDescriptor(name).apply(descriptorBuilder),
        scope,
        ::propertyChanged,
        getter,
        setter
    )
}

private class DevicePropertyDelegate<D : DeviceBase>(
    val owner: D,
    val default: MetaItem<*>?,
    val descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    private val getter: suspend (MetaItem<*>?) -> MetaItem<*>,
    private val setter: suspend (oldValue: MetaItem<*>?, newValue: MetaItem<*>) -> MetaItem<*>?
) : ReadOnlyProperty<D, DeviceProperty> {

    override fun getValue(thisRef: D, property: KProperty<*>): IsolatedDeviceProperty {
        val name = property.name
        return owner.registerMutableProperty(name) {
            @OptIn(ExperimentalCoroutinesApi::class)
            IsolatedDeviceProperty(
                name,
                default,
                PropertyDescriptor(name).apply(descriptorBuilder),
                owner.scope,
                owner::propertyChanged,
                getter,
                setter
            )
        } as IsolatedDeviceProperty
    }
}

public fun <D : DeviceBase> D.writing(
    default: MetaItem<*>? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend (MetaItem<*>?) -> MetaItem<*>,
    setter: suspend (oldValue: MetaItem<*>?, newValue: MetaItem<*>) -> MetaItem<*>?
): ReadOnlyProperty<D, DeviceProperty> = DevicePropertyDelegate(
    this,
    default,
    descriptorBuilder,
    getter,
    setter
)

public fun <D : DeviceBase> D.writingVirtual(
    default: MetaItem<*>,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {}
): ReadOnlyProperty<D, DeviceProperty> = writing(
    default,
    descriptorBuilder,
    getter = { it ?: default },
    setter = { _, newItem -> newItem }
)

public fun <D : DeviceBase> D.writingVirtual(
    default: Value,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {}
): ReadOnlyProperty<D, DeviceProperty> = writing(
    MetaItem.ValueItem(default),
    descriptorBuilder,
    getter = { it ?: MetaItem.ValueItem(default) },
    setter = { _, newItem -> newItem }
)

public fun <D : DeviceBase> D.writingDouble(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend (Double) -> Double,
    setter: suspend (oldValue: Double?, newValue: Double) -> Double?
): ReadOnlyProperty<D, DeviceProperty> {
    val innerGetter: suspend (MetaItem<*>?) -> MetaItem<*> = {
        MetaItem.ValueItem(getter(it.double ?: Double.NaN).asValue())
    }

    val innerSetter: suspend (oldValue: MetaItem<*>?, newValue: MetaItem<*>) -> MetaItem<*>? = { oldValue, newValue ->
        setter(oldValue.double, newValue.double ?: Double.NaN)?.asMetaItem()
    }

    return DevicePropertyDelegate(
        this,
        MetaItem.ValueItem(Double.NaN.asValue()),
        descriptorBuilder,
        innerGetter,
        innerSetter
    )
}

public fun <D : DeviceBase> D.writingBoolean(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend (Boolean?) -> Boolean,
    setter: suspend (oldValue: Boolean?, newValue: Boolean) -> Boolean?
): ReadOnlyProperty<D, DeviceProperty> {
    val innerGetter: suspend (MetaItem<*>?) -> MetaItem<*> = {
        MetaItem.ValueItem(getter(it.boolean).asValue())
    }

    val innerSetter: suspend (oldValue: MetaItem<*>?, newValue: MetaItem<*>) -> MetaItem<*>? = { oldValue, newValue ->
        setter(oldValue.boolean, newValue.boolean?: error("Can't convert $newValue to boolean"))?.asValue()?.asMetaItem()
    }

    return DevicePropertyDelegate(
        this,
        MetaItem.ValueItem(Null),
        descriptorBuilder,
        innerGetter,
        innerSetter
    )
}