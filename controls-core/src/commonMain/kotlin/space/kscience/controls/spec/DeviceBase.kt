package space.kscience.controls.spec

import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.api.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Global
import space.kscience.dataforge.meta.Meta
import kotlin.coroutines.CoroutineContext


@OptIn(InternalDeviceAPI::class)
private suspend fun <D : Device, T> WritableDevicePropertySpec<D, T>.writeMeta(device: D, item: Meta) {
    write(device, converter.metaToObject(item) ?: error("Meta $item could not be read with $converter"))
}

@OptIn(InternalDeviceAPI::class)
private suspend fun <D : Device, T> DevicePropertySpec<D, T>.readMeta(device: D): Meta? =
    read(device)?.let(converter::objectToMeta)


private suspend fun <D : Device, I, O> DeviceActionSpec<D, I, O>.executeWithMeta(
    device: D,
    item: Meta?,
): Meta? {
    val arg = item?.let { inputConverter.metaToObject(item) }
    val res = execute(device, arg)
    return res?.let { outputConverter.objectToMeta(res) }
}


/**
 * A base abstractions for [Device], introducing specifications for properties
 */
public abstract class DeviceBase<D : Device>(
    override val context: Context = Global,
    override val meta: Meta = Meta.EMPTY,
) : Device {

    /**
     * Collection of property specifications
     */
    public abstract val properties: Map<String, DevicePropertySpec<D, *>>

    /**
     * Collection of action specifications
     */
    public abstract val actions: Map<String, DeviceActionSpec<D, *, *>>

    override val propertyDescriptors: Collection<PropertyDescriptor>
        get() = properties.values.map { it.descriptor }

    override val actionDescriptors: Collection<ActionDescriptor>
        get() = actions.values.map { it.descriptor }

    override val coroutineContext: CoroutineContext by lazy {
        context.coroutineContext + SupervisorJob(context.coroutineContext[Job])
    }

    /**
     * Logical state store
     */
    private val logicalState: HashMap<String, Meta?> = HashMap()

    private val sharedMessageFlow: MutableSharedFlow<DeviceMessage> = MutableSharedFlow()

    public override val messageFlow: SharedFlow<DeviceMessage> get() = sharedMessageFlow

    @Suppress("UNCHECKED_CAST")
    internal val self: D
        get() = this as D

    private val stateLock = Mutex()

    /**
     * Update logical property state and notify listeners
     */
    protected suspend fun updateLogical(propertyName: String, value: Meta?) {
        if (value != logicalState[propertyName]) {
            stateLock.withLock {
                logicalState[propertyName] = value
            }
            if (value != null) {
                sharedMessageFlow.emit(PropertyChangedMessage(propertyName, value))
            }
        }
    }

    /**
     * Update logical state using given [spec] and its convertor
     */
    public suspend fun <T> updateLogical(spec: DevicePropertySpec<D, T>, value: T) {
        updateLogical(spec.name, spec.converter.objectToMeta(value))
    }

    /**
     * Force read physical value and push an update if it is changed. It does not matter if logical state is present.
     * The logical state is updated after read
     */
    override suspend fun readProperty(propertyName: String): Meta {
        val spec = properties[propertyName] ?: error("Property with name $propertyName not found")
        val meta = spec.readMeta(self) ?: error("Failed to read property $propertyName")
        updateLogical(propertyName, meta)
        return meta
    }

    /**
     * Read property if it exists and read correctly. Return null otherwise.
     */
    public suspend fun readPropertyOrNull(propertyName: String): Meta? {
        val spec = properties[propertyName] ?: return null
        val meta = spec.readMeta(self) ?: return null
        updateLogical(propertyName, meta)
        return meta
    }

    override fun getProperty(propertyName: String): Meta? = logicalState[propertyName]

    override suspend fun invalidate(propertyName: String) {
        stateLock.withLock {
            logicalState.remove(propertyName)
        }
    }

    override suspend fun writeProperty(propertyName: String, value: Meta): Unit {
        when (val property = properties[propertyName]) {
            null -> {
                //If there is a physical property with a given name, invalidate logical property and write physical one
                updateLogical(propertyName, value)
            }

            is WritableDevicePropertySpec -> {
                invalidate(propertyName)
                property.writeMeta(self, value)
            }

            else -> {
                error("Property $property is not writeable")
            }
        }
    }

    override suspend fun execute(actionName: String, argument: Meta?): Meta? {
        val spec = actions[actionName] ?: error("Action with name $actionName not found")
        return spec.executeWithMeta(self, argument)
    }

}

