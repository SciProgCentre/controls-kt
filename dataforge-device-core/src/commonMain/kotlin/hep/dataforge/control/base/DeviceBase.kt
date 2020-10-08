package hep.dataforge.control.base

import hep.dataforge.context.Context
import hep.dataforge.control.api.ActionDescriptor
import hep.dataforge.control.api.Device
import hep.dataforge.control.api.DeviceListener
import hep.dataforge.control.api.PropertyDescriptor
import hep.dataforge.meta.MetaItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Baseline implementation of [Device] interface
 */
public abstract class DeviceBase(override val context: Context) : Device {

    private val _properties = HashMap<String, ReadOnlyDeviceProperty>()
    public val properties: Map<String, ReadOnlyDeviceProperty> get() = _properties
    private val _actions = HashMap<String, DeviceAction>()
    public val actions: Map<String, DeviceAction> get() = _actions

    private val listeners = ArrayList<Pair<Any?, DeviceListener>>(4)

    override fun registerListener(listener: DeviceListener, owner: Any?) {
        listeners.add(owner to listener)
    }

    override fun removeListeners(owner: Any?) {
        listeners.removeAll { it.first == owner }
    }

    internal fun notifyListeners(block: DeviceListener.() -> Unit) {
        listeners.forEach { it.second.block() }
    }

    public fun notifyPropertyChanged(propertyName: String) {
        scope.launch {
            val value = getProperty(propertyName)
            notifyListeners { propertyChanged(propertyName, value) }
        }
    }

    override val propertyDescriptors: Collection<PropertyDescriptor>
        get() = _properties.values.map { it.descriptor }

    override val actionDescriptors: Collection<ActionDescriptor>
        get() = _actions.values.map { it.descriptor }

    internal fun <P : ReadOnlyDeviceProperty> registerProperty(name: String, property: P) {
        if (_properties.contains(name)) error("Property with name $name already registered")
        _properties[name] = property
    }

    internal fun registerAction(name: String, action: DeviceAction) {
        if (_actions.contains(name)) error("Action with name $name already registered")
        _actions[name] = action
    }

    override suspend fun getProperty(propertyName: String): MetaItem<*> =
        (_properties[propertyName] ?: error("Property with name $propertyName not defined")).read()

    override suspend fun invalidateProperty(propertyName: String) {
        (_properties[propertyName] ?: error("Property with name $propertyName not defined")).invalidate()
    }

    override suspend fun setProperty(propertyName: String, value: MetaItem<*>) {
        (_properties[propertyName] as? DeviceProperty ?: error("Property with name $propertyName not defined")).write(
            value
        )
    }

    override suspend fun execute(command: String, argument: MetaItem<*>?): MetaItem<*>? =
        (_actions[command] ?: error("Request with name $command not defined")).invoke(argument)

    @OptIn(ExperimentalCoroutinesApi::class)
    private open inner class BasicReadOnlyDeviceProperty(
        override val name: String,
        default: MetaItem<*>?,
        override val descriptor: PropertyDescriptor,
        private val getter: suspend (before: MetaItem<*>?) -> MetaItem<*>,
    ) : ReadOnlyDeviceProperty {

        override val scope: CoroutineScope get() = this@DeviceBase.scope

        private val state: MutableStateFlow<MetaItem<*>?> = MutableStateFlow(default)
        override val value: MetaItem<*>? get() = state.value

        override suspend fun invalidate() {
            state.value = null
        }

        override fun updateLogical(item: MetaItem<*>) {
            state.value = item
            notifyListeners {
                propertyChanged(name, item)
            }
        }

        override suspend fun read(force: Boolean): MetaItem<*> {
            //backup current value
            val currentValue = value
            return if (force || currentValue == null) {
                //all device operations should be run on device context
                //propagate error, but do not fail scope
                val res = withContext(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job])) {
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

    /**
     * Create a bound read-only property with given [getter]
     */
    public fun newReadOnlyProperty(
        name: String,
        default: MetaItem<*>?,
        descriptorBuilder: PropertyDescriptor.() -> Unit = {},
        getter: suspend (MetaItem<*>?) -> MetaItem<*>,
    ): ReadOnlyDeviceProperty {
        val property = BasicReadOnlyDeviceProperty(
            name,
            default,
            PropertyDescriptor(name).apply(descriptorBuilder),
            getter
        )
        registerProperty(name, property)
        return property
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private inner class BasicDeviceProperty(
        name: String,
        default: MetaItem<*>?,
        descriptor: PropertyDescriptor,
        getter: suspend (MetaItem<*>?) -> MetaItem<*>,
        private val setter: suspend (oldValue: MetaItem<*>?, newValue: MetaItem<*>) -> MetaItem<*>?,
    ) : BasicReadOnlyDeviceProperty(name, default, descriptor, getter), DeviceProperty {

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
                withContext(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job])) {
                    setter(oldValue, item)?.let {
                        updateLogical(it)
                    }
                }
            }
        }
    }

    /**
     * Create a bound mutable property with given [getter] and [setter]
     */
    public fun newMutableProperty(
        name: String,
        default: MetaItem<*>?,
        descriptorBuilder: PropertyDescriptor.() -> Unit = {},
        getter: suspend (MetaItem<*>?) -> MetaItem<*>,
        setter: suspend (oldValue: MetaItem<*>?, newValue: MetaItem<*>) -> MetaItem<*>?,
    ): DeviceProperty {
        val property = BasicDeviceProperty(
            name,
            default,
            PropertyDescriptor(name).apply(descriptorBuilder),
            getter,
            setter
        )
        registerProperty(name, property)
        return property
    }

    /**
     * A stand-alone action
     */
    private inner class BasicDeviceAction(
        override val name: String,
        override val descriptor: ActionDescriptor,
        private val block: suspend (MetaItem<*>?) -> MetaItem<*>?,
    ) : DeviceAction {
        override suspend fun invoke(arg: MetaItem<*>?): MetaItem<*>? =
            withContext(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job])) {
                block(arg).also {
                    notifyListeners {
                        actionExecuted(name, arg, it)
                    }
                }
            }
    }

    /**
     * Create a new bound action
     */
    public fun newAction(
        name: String,
        descriptorBuilder: ActionDescriptor.() -> Unit = {},
        block: suspend (MetaItem<*>?) -> MetaItem<*>?,
    ): DeviceAction {
        val action = BasicDeviceAction(name, ActionDescriptor(name).apply(descriptorBuilder), block)
        registerAction(name, action)
        return action
    }

    public companion object {

    }
}



