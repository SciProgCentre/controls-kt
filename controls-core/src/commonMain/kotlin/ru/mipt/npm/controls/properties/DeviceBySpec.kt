package ru.mipt.npm.controls.properties

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.mipt.npm.controls.api.ActionDescriptor
import ru.mipt.npm.controls.api.Device
import ru.mipt.npm.controls.api.PropertyDescriptor
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Global
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaItem

/**
 * @param D recursive self-type for properties and actions
 */
public open class DeviceBySpec<D : DeviceBySpec<D>> : Device {
    override var context: Context = Global
        internal set

    public var meta: Meta = Meta.EMPTY
        internal set

    public var properties: Map<String, DevicePropertySpec<D, *>> = emptyMap()
        internal set

    public var actions: Map<String, DeviceActionSpec<D, *, *>> = emptyMap()
        internal set

    override val propertyDescriptors: Collection<PropertyDescriptor>
        get() = properties.values.map { it.descriptor }

    override val actionDescriptors: Collection<ActionDescriptor>
        get() = actions.values.map { it.descriptor }

    override val scope: CoroutineScope get() = context

    private val logicalState: HashMap<String, MetaItem?> = HashMap()

    private val _propertyFlow: MutableSharedFlow<Pair<String, MetaItem>> = MutableSharedFlow()

    override val propertyFlow: SharedFlow<Pair<String, MetaItem>> get() = _propertyFlow

    @Suppress("UNCHECKED_CAST")
    internal val self: D
        get() = this as D

    internal fun getLogicalState(propertyName: String): MetaItem? = logicalState[propertyName]

    private val stateLock = Mutex()

    internal suspend fun setLogicalState(propertyName: String, value: MetaItem?) {
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
     * Force read physical value and push an update if it is changed
     */
    public suspend fun readProperty(propertyName: String): MetaItem {
        val newValue = properties[propertyName]?.readItem(self)
            ?: error("A property with name $propertyName is not registered in $this")
        setLogicalState(propertyName, newValue)
        return newValue
    }

    override suspend fun getProperty(propertyName: String): MetaItem =
        logicalState[propertyName] ?: readProperty(propertyName)

    override suspend fun invalidateProperty(propertyName: String) {
        stateLock.withLock {
            logicalState.remove(propertyName)
        }
    }

    override suspend fun setProperty(propertyName: String, value: MetaItem) {
        //If there is a physical property with given name, invalidate logical property and write physical one
        (properties[propertyName] as? WritableDevicePropertySpec<D, out Any>)?.let {
            it.writeItem(self, value)
            invalidateProperty(propertyName)
        } ?: run {
            setLogicalState(propertyName, value)
        }
    }

    override suspend fun execute(action: String, argument: MetaItem?): MetaItem? =
        actions[action]?.executeItem(self, argument)
}


public operator fun <D : DeviceBySpec<D>, T : Any> D.get(
    propertySpec: DevicePropertySpec<D, T>
): Deferred<T> = scope.async {
    propertySpec.read(this@get).also {
        setLogicalState(propertySpec.name, propertySpec.converter.objectToMetaItem(it))
    }
}

public operator fun <D : DeviceBySpec<D>, T : Any> D.set(propertySpec: WritableDevicePropertySpec<D, T>, value: T) {
    scope.launch {
        propertySpec.write(this@set, value)
        invalidateProperty(propertySpec.name)
    }
}