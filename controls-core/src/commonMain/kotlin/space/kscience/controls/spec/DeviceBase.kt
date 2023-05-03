package space.kscience.controls.spec

import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.api.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Global
import space.kscience.dataforge.meta.Meta
import kotlin.coroutines.CoroutineContext


/**
 * A base abstractions for [Device], introducing specifications for properties
 */
@OptIn(InternalDeviceAPI::class)
public abstract class DeviceBase<D : DeviceBase<D>>(
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
    protected suspend fun <T> updateLogical(spec: DevicePropertySpec<D, T>, value: T) {
        updateLogical(spec.name, spec.converter.objectToMeta(value))
    }

    /**
     * Force read physical value and push an update if it is changed. It does not matter if logical state is present.
     * The logical state is updated after read
     */
    override suspend fun readProperty(propertyName: String): Meta {
        val newValue = properties[propertyName]?.readMeta(self)
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

    override suspend fun writeProperty(propertyName: String, value: Meta): Unit {
        //If there is a physical property with given name, invalidate logical property and write physical one
        (properties[propertyName] as? WritableDevicePropertySpec<D, out Any?>)?.let {
            invalidate(propertyName)
            it.writeMeta(self, value)
        } ?: run {
            updateLogical(propertyName, value)
        }
    }

    override suspend fun execute(action: String, argument: Meta?): Meta? =
        actions[action]?.executeWithMeta(self, argument)

    /**
     * Read typed value and update/push event if needed.
     * Return null if property read is not successful or property is undefined.
     */
    public suspend fun <T> DevicePropertySpec<D, T>.readOrNull(): T? {
        val res = read(self) ?: return null
        updateLogical(name, converter.objectToMeta(res))
        return res
    }

    public suspend fun <T> DevicePropertySpec<D, T>.read(): T =
        readOrNull() ?: error("Failed to read property $name state")

    public fun <T> DevicePropertySpec<D, T>.get(): T? = getProperty(name)?.let(converter::metaToObject)

    /**
     * Write typed property state and invalidate logical state
     */
    public suspend fun <T> WritableDevicePropertySpec<D, T>.write(value: T) {
        invalidate(name)
        write(self, value)
        //perform asynchronous read and update after write
        launch {
            read()
        }
    }

    /**
     * Reset logical state of a property
     */
    public suspend fun DevicePropertySpec<D, *>.invalidate() {
        invalidate(name)
    }

    public suspend operator fun <I, O> DeviceActionSpec<D, I, O>.invoke(input: I? = null): O? = execute(self, input)

}

/**
 * A device generated from specification
 * @param D recursive self-type for properties and actions
 */
public open class DeviceBySpec<D : DeviceBySpec<D>>(
    public val spec: DeviceSpec<in D>,
    context: Context = Global,
    meta: Meta = Meta.EMPTY,
) : DeviceBase<D>(context, meta) {
    override val properties: Map<String, DevicePropertySpec<D, *>> get() = spec.properties
    override val actions: Map<String, DeviceActionSpec<D, *, *>> get() = spec.actions

    override suspend fun open(): Unit = with(spec) {
        super.open()
        self.onOpen()
    }

    override fun close(): Unit = with(spec) {
        self.onClose()
        super.close()
    }
}

