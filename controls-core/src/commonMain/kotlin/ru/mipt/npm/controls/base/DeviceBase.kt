package ru.mipt.npm.controls.base

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.mipt.npm.controls.api.ActionDescriptor
import ru.mipt.npm.controls.api.Device
import ru.mipt.npm.controls.api.PropertyDescriptor
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaItem
import space.kscience.dataforge.misc.DFExperimental
import kotlin.coroutines.CoroutineContext

//TODO move to DataForge-core
@DFExperimental
public data class LogEntry(val content: String, val priority: Int = 0)


@OptIn(ExperimentalCoroutinesApi::class)
private open class BasicReadOnlyDeviceProperty(
    val device: DeviceBase,
    override val name: String,
    default: MetaItem?,
    override val descriptor: PropertyDescriptor,
    private val getter: suspend (before: MetaItem?) -> MetaItem,
) : ReadOnlyDeviceProperty {

    override val scope: CoroutineScope get() = device

    private val state: MutableStateFlow<MetaItem?> = MutableStateFlow(default)
    override val value: MetaItem? get() = state.value

    override suspend fun invalidate() {
        state.value = null
    }

    override fun updateLogical(item: MetaItem) {
        state.value = item
        scope.launch {
            device.sharedPropertyFlow.emit(Pair(name, item))
        }
    }

    override suspend fun read(force: Boolean): MetaItem {
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

    override fun flow(): StateFlow<MetaItem?> = state
}


@OptIn(ExperimentalCoroutinesApi::class)
private class BasicDeviceProperty(
    device: DeviceBase,
    name: String,
    default: MetaItem?,
    descriptor: PropertyDescriptor,
    getter: suspend (MetaItem?) -> MetaItem,
    private val setter: suspend (oldValue: MetaItem?, newValue: MetaItem) -> MetaItem?,
) : BasicReadOnlyDeviceProperty(device, name, default, descriptor, getter), DeviceProperty {

    override var value: MetaItem?
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

    override suspend fun write(item: MetaItem) {
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
 * Baseline implementation of [Device] interface
 */
@Suppress("EXPERIMENTAL_API_USAGE")
public abstract class DeviceBase(final override val context: Context) : Device {

    override val coroutineContext: CoroutineContext =
        context.coroutineContext + SupervisorJob(context.coroutineContext[Job])


    private val _properties = HashMap<String, ReadOnlyDeviceProperty>()
    public val properties: Map<String, ReadOnlyDeviceProperty> get() = _properties
    private val _actions = HashMap<String, DeviceAction>()
    public val actions: Map<String, DeviceAction> get() = _actions

    internal val sharedPropertyFlow = MutableSharedFlow<Pair<String, MetaItem>>()

    override val propertyFlow: SharedFlow<Pair<String, MetaItem>> get() = sharedPropertyFlow

    private val sharedLogFlow = MutableSharedFlow<LogEntry>()

    /**
     * The [SharedFlow] of log messages
     */
    @DFExperimental
    public val logFlow: SharedFlow<LogEntry>
        get() = sharedLogFlow

    protected suspend fun log(message: String, priority: Int = 0) {
        sharedLogFlow.emit(LogEntry(message, priority))
    }

    override val propertyDescriptors: Collection<PropertyDescriptor>
        get() = _properties.values.map { it.descriptor }

    override val actionDescriptors: Collection<ActionDescriptor>
        get() = _actions.values.map { it.descriptor }

    private fun <P : ReadOnlyDeviceProperty> registerProperty(name: String, property: P) {
        if (_properties.contains(name)) error("Property with name $name already registered")
        _properties[name] = property
    }

    internal fun registerAction(name: String, action: DeviceAction) {
        if (_actions.contains(name)) error("Action with name $name already registered")
        _actions[name] = action
    }

    override suspend fun getProperty(propertyName: String): MetaItem =
        (_properties[propertyName] ?: error("Property with name $propertyName not defined")).read()

    override suspend fun invalidateProperty(propertyName: String) {
        (_properties[propertyName] ?: error("Property with name $propertyName not defined")).invalidate()
    }

    override suspend fun setProperty(propertyName: String, value: MetaItem) {
        (_properties[propertyName] as? DeviceProperty ?: error("Property with name $propertyName not defined")).write(
            value
        )
    }

    override suspend fun execute(action: String, argument: MetaItem?): MetaItem? =
        (_actions[action] ?: error("Request with name $action not defined")).invoke(argument)

    /**
     * Create a bound read-only property with given [getter]
     */
    public fun createReadOnlyProperty(
        name: String,
        default: MetaItem?,
        descriptorBuilder: PropertyDescriptor.() -> Unit = {},
        getter: suspend (MetaItem?) -> MetaItem,
    ): ReadOnlyDeviceProperty {
        val property = BasicReadOnlyDeviceProperty(
            this,
            name,
            default,
            PropertyDescriptor(name).apply(descriptorBuilder),
            getter
        )
        registerProperty(name, property)
        return property
    }


    /**
     * Create a bound mutable property with given [getter] and [setter]
     */
    internal fun createMutableProperty(
        name: String,
        default: MetaItem?,
        descriptorBuilder: PropertyDescriptor.() -> Unit = {},
        getter: suspend (MetaItem?) -> MetaItem,
        setter: suspend (oldValue: MetaItem?, newValue: MetaItem) -> MetaItem?,
    ): DeviceProperty {
        val property = BasicDeviceProperty(
            this,
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
        private val block: suspend (MetaItem?) -> MetaItem?,
    ) : DeviceAction {
        override suspend fun invoke(arg: MetaItem?): MetaItem? =
            withContext(coroutineContext) {
                block(arg)
            }
    }

    /**
     * Create a new bound action
     */
    internal fun createAction(
        name: String,
        descriptorBuilder: ActionDescriptor.() -> Unit = {},
        block: suspend (MetaItem?) -> MetaItem?,
    ): DeviceAction {
        val action = BasicDeviceAction(name, ActionDescriptor(name).apply(descriptorBuilder), block)
        registerAction(name, action)
        return action
    }

    public companion object {

    }
}



