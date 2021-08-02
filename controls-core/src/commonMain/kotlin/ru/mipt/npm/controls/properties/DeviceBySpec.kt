package ru.mipt.npm.controls.properties

import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.mipt.npm.controls.api.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Global
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.transformations.MetaConverter
import kotlin.coroutines.CoroutineContext
import kotlin.properties.Delegates.observable
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * @param D recursive self-type for properties and actions
 */
@OptIn(InternalDeviceAPI::class)
public open class DeviceBySpec<D : DeviceBySpec<D>>(
    public val spec: DeviceSpec<D>,
    context: Context = Global,
    meta: Meta = Meta.EMPTY
) : Device {
    override var context: Context = context
        internal set

    public var meta: Meta = meta
        internal set

    public val properties: Map<String, DevicePropertySpec<D, *>> get() = spec.properties
    public val actions: Map<String, DeviceActionSpec<D, *, *>> get() = spec.actions

    override val propertyDescriptors: Collection<PropertyDescriptor>
        get() = properties.values.map { it.descriptor }

    override val actionDescriptors: Collection<ActionDescriptor>
        get() = actions.values.map { it.descriptor }

    override val coroutineContext: CoroutineContext by lazy {
        context.coroutineContext + SupervisorJob(context.coroutineContext[Job])
    }

    private val logicalState: HashMap<String, Meta?> = HashMap()

    private val sharedMessageFlow: MutableSharedFlow<DeviceMessage> = MutableSharedFlow()

    public override val messageFlow: SharedFlow<DeviceMessage> get() = sharedMessageFlow

    @Suppress("UNCHECKED_CAST")
    internal val self: D
        get() = this as D

    private val stateLock = Mutex()

    private suspend fun updateLogical(propertyName: String, value: Meta?) {
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
     * Force read physical value and push an update if it is changed. It does not matter if logical state is present.
     * The logical state is updated after read
     */
    override suspend fun readProperty(propertyName: String): Meta {
        val newValue = properties[propertyName]?.readItem(self)
            ?: error("A property with name $propertyName is not registered in $this")
        updateLogical(propertyName, newValue)
        return newValue
    }

    override fun getProperty(propertyName: String): Meta? = logicalState[propertyName]

    override suspend fun invalidate(propertyName: String) {
        stateLock.withLock {
            logicalState.remove(propertyName)
        }
    }

    override suspend fun writeItem(propertyName: String, value: Meta): Unit {
        //If there is a physical property with given name, invalidate logical property and write physical one
        (properties[propertyName] as? WritableDevicePropertySpec<D, out Any>)?.let {
            it.writeItem(self, value)
            invalidate(propertyName)
        } ?: run {
            updateLogical(propertyName, value)
        }
    }

    override suspend fun execute(action: String, argument: Meta?): Meta? =
        actions[action]?.executeItem(self, argument)


    /**
     * A delegate that represents the logical-only state of the device
     */
    public fun <T : Any> state(
        converter: MetaConverter<T>,
        initialValue: T,
    ): ReadWriteProperty<D, T> = observable(initialValue) { property: KProperty<*>, oldValue: T, newValue: T ->
        if (oldValue != newValue) {
            launch {
                invalidate(property.name)
                sharedMessageFlow.emit(PropertyChangedMessage(property.name, converter.objectToMeta(newValue)))
            }
        }
    }

    /**
     * Read typed value and update/push event if needed
     */
    public suspend fun <T : Any> DevicePropertySpec<D, T>.read(): T {
        val res = read(self)
        updateLogical(name, converter.objectToMeta(res))
        return res
    }

    public fun <T : Any> DevicePropertySpec<D, T>.get(): T? = getProperty(name)?.let(converter::metaToObject)

    /**
     * Write typed property state and invalidate logical state
     */
    public suspend fun <T : Any> WritableDevicePropertySpec<D, T>.write(value: T) {
        write(self, value)
        invalidate(name)
    }

    override fun close() {
        with(spec) { self.onShutdown() }
        super.close()
    }
}

public suspend fun <D : DeviceBySpec<D>, T : Any> D.read(
    propertySpec: DevicePropertySpec<D, T>
): T = propertySpec.read()

public fun <D : DeviceBySpec<D>, T : Any> D.write(
    propertySpec: WritableDevicePropertySpec<D, T>,
    value: T
): Job = launch {
    propertySpec.write(value)
}
