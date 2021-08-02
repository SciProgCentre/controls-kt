package ru.mipt.npm.controls.base

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.mipt.npm.controls.api.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.misc.DFExperimental
import kotlin.collections.set
import kotlin.coroutines.CoroutineContext

//TODO move to DataForge-core
@DFExperimental
public data class LogEntry(val content: String, val priority: Int = 0)


@OptIn(ExperimentalCoroutinesApi::class)
private open class BasicReadOnlyDeviceProperty(
    val device: DeviceBase,
    override val name: String,
    default: Meta?,
    override val descriptor: PropertyDescriptor,
    private val getter: suspend (before: Meta?) -> Meta,
) : ReadOnlyDeviceProperty {

    override val scope: CoroutineScope get() = device

    private val state: MutableStateFlow<Meta?> = MutableStateFlow(default)
    override val value: Meta? get() = state.value

    override suspend fun invalidate() {
        state.value = null
    }

    override fun updateLogical(item: Meta) {
        state.value = item
        scope.launch {
            device.sharedMessageFlow.emit(
                PropertyChangedMessage(
                    property = name,
                    value = item,
                )
            )
        }
    }

    override suspend fun read(force: Boolean): Meta {
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

    override fun flow(): StateFlow<Meta?> = state
}


@OptIn(ExperimentalCoroutinesApi::class)
private class BasicDeviceProperty(
    device: DeviceBase,
    name: String,
    default: Meta?,
    descriptor: PropertyDescriptor,
    getter: suspend (Meta?) -> Meta,
    private val setter: suspend (oldValue: Meta?, newValue: Meta) -> Meta?,
) : BasicReadOnlyDeviceProperty(device, name, default, descriptor, getter), DeviceProperty {

    override var value: Meta?
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

    override suspend fun write(item: Meta) {
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

    internal val sharedMessageFlow = MutableSharedFlow<DeviceMessage>()

    override val messageFlow: SharedFlow<DeviceMessage> get() = sharedMessageFlow
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

    override suspend fun readProperty(propertyName: String): Meta =
        (_properties[propertyName] ?: error("Property with name $propertyName not defined")).read()

    override fun getProperty(propertyName: String): Meta? =
        (_properties[propertyName] ?: error("Property with name $propertyName not defined")).value

    override suspend fun invalidate(propertyName: String) {
        (_properties[propertyName] ?: error("Property with name $propertyName not defined")).invalidate()
    }

    override suspend fun writeItem(propertyName: String, value: Meta) {
        (_properties[propertyName] as? DeviceProperty ?: error("Property with name $propertyName not defined")).write(
            value
        )
    }

    override suspend fun execute(action: String, argument: Meta?): Meta? =
        (_actions[action] ?: error("Request with name $action not defined")).invoke(argument)

    /**
     * Create a bound read-only property with given [getter]
     */
    public fun createReadOnlyProperty(
        name: String,
        default: Meta?,
        descriptorBuilder: PropertyDescriptor.() -> Unit = {},
        getter: suspend (Meta?) -> Meta,
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
        default: Meta?,
        descriptorBuilder: PropertyDescriptor.() -> Unit = {},
        getter: suspend (Meta?) -> Meta,
        setter: suspend (oldValue: Meta?, newValue: Meta) -> Meta?,
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
        private val block: suspend (Meta?) -> Meta?,
    ) : DeviceAction {
        override suspend fun invoke(arg: Meta?): Meta? =
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
        block: suspend (Meta?) -> Meta?,
    ): DeviceAction {
        val action = BasicDeviceAction(name, ActionDescriptor(name).apply(descriptorBuilder), block)
        registerAction(name, action)
        return action
    }

    public companion object {

    }
}



