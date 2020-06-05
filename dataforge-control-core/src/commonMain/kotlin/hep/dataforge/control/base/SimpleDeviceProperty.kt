package hep.dataforge.control.base

import hep.dataforge.control.api.PropertyDescriptor
import hep.dataforge.meta.Meta
import hep.dataforge.meta.MetaBuilder
import hep.dataforge.meta.MetaItem
import hep.dataforge.meta.double
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

@OptIn(ExperimentalCoroutinesApi::class)
open class SimpleReadOnlyDeviceProperty(
    override val name: String,
    default: MetaItem<*>?,
    override val descriptor: PropertyDescriptor,
    override val scope: CoroutineScope,
    private val updateCallback: (name: String, item: MetaItem<*>) -> Unit,
    private val getter: suspend (before: MetaItem<*>?) -> MetaItem<*>
) : ReadOnlyDeviceProperty {

    private val state: MutableStateFlow<MetaItem<*>?> = MutableStateFlow(default)
    override val value: MetaItem<*>? get() = state.value

    override suspend fun invalidate() {
        state.value = null
    }

    private fun update(item: MetaItem<*>) {
        state.value = item
        updateCallback(name, item)
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
            update(res)
            res
        } else {
            currentValue
        }
    }

    override fun flow(): StateFlow<MetaItem<*>?> = state
}

private class ReadOnlyDevicePropertyDelegate<D : DeviceBase>(
    val owner: D,
    val default: MetaItem<*>?,
    val descriptor: PropertyDescriptor = PropertyDescriptor.empty(),
    private val getter: suspend (MetaItem<*>?) -> MetaItem<*>
) : ReadOnlyProperty<D, SimpleReadOnlyDeviceProperty> {

    override fun getValue(thisRef: D, property: KProperty<*>): SimpleReadOnlyDeviceProperty {
        val name = property.name

        return owner.resolveProperty(name) {
            @OptIn(ExperimentalCoroutinesApi::class)
            SimpleReadOnlyDeviceProperty(
                name,
                default,
                descriptor,
                owner.scope,
                owner::propertyChanged,
                getter
            )
        } as SimpleReadOnlyDeviceProperty
    }
}

fun <D : DeviceBase> D.reading(
    default: MetaItem<*>? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend (MetaItem<*>?) -> MetaItem<*>
): ReadOnlyProperty<D, SimpleReadOnlyDeviceProperty> = ReadOnlyDevicePropertyDelegate(
    this,
    default,
    PropertyDescriptor.invoke(descriptorBuilder),
    getter
)

fun <D : DeviceBase> D.readingValue(
    default: Value? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend () -> Any
): ReadOnlyProperty<D, SimpleReadOnlyDeviceProperty> = ReadOnlyDevicePropertyDelegate(
    this,
    default?.let { MetaItem.ValueItem(it) },
    PropertyDescriptor.invoke(descriptorBuilder),
    { MetaItem.ValueItem(Value.of(getter())) }
)

fun <D : DeviceBase> D.readingNumber(
    default: Number? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend () -> Number
): ReadOnlyProperty<D, SimpleReadOnlyDeviceProperty> = ReadOnlyDevicePropertyDelegate(
    this,
    default?.let { MetaItem.ValueItem(it.asValue()) },
    PropertyDescriptor.invoke(descriptorBuilder),
    {
        val number = getter()
        MetaItem.ValueItem(number.asValue())
    }
)

fun <D : DeviceBase> D.readingMeta(
    default: Meta? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend MetaBuilder.() -> Unit
): ReadOnlyProperty<D, SimpleReadOnlyDeviceProperty> = ReadOnlyDevicePropertyDelegate(
    this,
    default?.let { MetaItem.NodeItem(it) },
    PropertyDescriptor.invoke(descriptorBuilder),
    { MetaItem.NodeItem(MetaBuilder().apply { getter() }) }
)

@OptIn(ExperimentalCoroutinesApi::class)
class SimpleDeviceProperty(
    name: String,
    default: MetaItem<*>?,
    descriptor: PropertyDescriptor,
    scope: CoroutineScope,
    updateCallback: (name: String, item: MetaItem<*>?) -> Unit,
    getter: suspend (MetaItem<*>?) -> MetaItem<*>,
    private val setter: suspend (oldValue: MetaItem<*>?, newValue: MetaItem<*>) -> Unit
) : SimpleReadOnlyDeviceProperty(name, default, descriptor, scope, updateCallback, getter), DeviceProperty {

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
                setter(oldValue, item)
            }
        }
    }
}

private class DevicePropertyDelegate<D : DeviceBase>(
    val owner: D,
    val default: MetaItem<*>?,
    val descriptor: PropertyDescriptor = PropertyDescriptor.empty(),
    private val getter: suspend (MetaItem<*>?) -> MetaItem<*>,
    private val setter: suspend (oldValue: MetaItem<*>?, newValue: MetaItem<*>) -> Unit
) : ReadOnlyProperty<D, SimpleDeviceProperty> {

    override fun getValue(thisRef: D, property: KProperty<*>): SimpleDeviceProperty {
        val name = property.name
        return owner.resolveProperty(name) {
            @OptIn(ExperimentalCoroutinesApi::class)
            SimpleDeviceProperty(
                name,
                default,
                descriptor,
                owner.scope,
                owner::propertyChanged,
                getter,
                setter
            )
        } as SimpleDeviceProperty
    }
}

fun <D : DeviceBase> D.writing(
    default: MetaItem<*>? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend (MetaItem<*>?) -> MetaItem<*>,
    setter: suspend (oldValue: MetaItem<*>?, newValue: MetaItem<*>) -> Unit
): ReadOnlyProperty<D, SimpleDeviceProperty> = DevicePropertyDelegate(
    this,
    default,
    PropertyDescriptor.invoke(descriptorBuilder),
    getter,
    setter
)

fun <D : DeviceBase> D.writingVirtual(
    default: MetaItem<*>,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {}
): ReadOnlyProperty<D, SimpleDeviceProperty> = writing(
    default,
    descriptorBuilder,
    getter = { it ?: default },
    setter = { _, _ -> }
)

fun <D : DeviceBase> D.writingVirtual(
    default: Value,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {}
): ReadOnlyProperty<D, SimpleDeviceProperty> = writing(
    MetaItem.ValueItem(default),
    descriptorBuilder,
    getter = { it ?: MetaItem.ValueItem(default) },
    setter = { _, _ -> }
)

fun <D : DeviceBase> D.writingDouble(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend (Double) -> Double,
    setter: suspend (oldValue: Double?, newValue: Double) -> Unit
): ReadOnlyProperty<D, SimpleDeviceProperty> {
    val innerGetter: suspend (MetaItem<*>?) -> MetaItem<*> = {
        MetaItem.ValueItem(getter(it.double ?: Double.NaN).asValue())
    }

    val innerSetter: suspend (oldValue: MetaItem<*>?, newValue: MetaItem<*>) -> Unit = { oldValue, newValue ->
        setter(oldValue.double, newValue.double ?: Double.NaN)
    }

    return DevicePropertyDelegate(
        this,
        MetaItem.ValueItem(Double.NaN.asValue()),
        PropertyDescriptor.invoke(descriptorBuilder),
        innerGetter,
        innerSetter
    )
}
