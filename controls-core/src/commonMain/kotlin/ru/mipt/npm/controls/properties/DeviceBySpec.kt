package ru.mipt.npm.controls.properties

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.mipt.npm.controls.api.ActionDescriptor
import ru.mipt.npm.controls.api.Device
import ru.mipt.npm.controls.api.PropertyDescriptor
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Global
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaItem
import space.kscience.dataforge.meta.transformations.MetaConverter
import kotlin.coroutines.CoroutineContext
import kotlin.properties.Delegates.observable
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * @param D recursive self-type for properties and actions
 */
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

    private val logicalState: HashMap<String, MetaItem?> = HashMap()

    private val _propertyFlow: MutableSharedFlow<Pair<String, MetaItem>> = MutableSharedFlow()

    override val propertyFlow: SharedFlow<Pair<String, MetaItem>> get() = _propertyFlow

    @Suppress("UNCHECKED_CAST")
    internal val self: D
        get() = this as D

    internal fun getLogicalState(propertyName: String): MetaItem? = logicalState[propertyName]

    private val stateLock = Mutex()

    internal suspend fun updateLogical(propertyName: String, value: MetaItem?) {
        if (value != logicalState[propertyName]) {
            stateLock.withLock {
                logicalState[propertyName] = value
            }
            if (value != null) {
                _propertyFlow.emit(propertyName to value)
            }
        }
    }

    /**
     * Force read physical value and push an update if it is changed. It does not matter if logical state is present.
     * The logical state is updated after read
     */
    public suspend fun readProperty(propertyName: String): MetaItem {
        val newValue = properties[propertyName]?.readItem(self)
            ?: error("A property with name $propertyName is not registered in $this")
        updateLogical(propertyName, newValue)
        return newValue
    }

    override suspend fun getProperty(propertyName: String): MetaItem =
        logicalState[propertyName] ?: readProperty(propertyName)

    override suspend fun invalidateProperty(propertyName: String) {
        stateLock.withLock {
            logicalState.remove(propertyName)
        }
    }

    override suspend fun setProperty(propertyName: String, value: MetaItem): Unit {
        //If there is a physical property with given name, invalidate logical property and write physical one
        (properties[propertyName] as? WritableDevicePropertySpec<D, out Any>)?.let {
            it.writeItem(self, value)
            invalidateProperty(propertyName)
        } ?: run {
            updateLogical(propertyName, value)
        }
    }

    override suspend fun execute(action: String, argument: MetaItem?): MetaItem? =
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
                invalidateProperty(property.name)
                _propertyFlow.emit(property.name to converter.objectToMetaItem(newValue))
            }
        }
    }

    public suspend fun <T : Any> DevicePropertySpec<D, T>.read(): T = read(self)

    override fun close() {
        with(spec){ self.onShutdown() }
        super.close()
    }
}

public suspend fun <D : DeviceBySpec<D>, T : Any> D.getSuspend(
    propertySpec: DevicePropertySpec<D, T>
): T = propertySpec.read(this@getSuspend).also {
    updateLogical(propertySpec.name, propertySpec.converter.objectToMetaItem(it))
}


public fun <D : DeviceBySpec<D>, T : Any> D.getAsync(
    propertySpec: DevicePropertySpec<D, T>
): Deferred<T> = async {
    getSuspend(propertySpec)
}

public operator fun <D : DeviceBySpec<D>, T : Any> D.set(propertySpec: WritableDevicePropertySpec<D, T>, value: T) {
    launch {
        propertySpec.write(this@set, value)
        invalidateProperty(propertySpec.name)
    }
}