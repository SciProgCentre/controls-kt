package space.kscience.controls.spec

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.api.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.debug
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.misc.DFExperimental
import kotlin.coroutines.CoroutineContext

/**
 * Write a meta [item] to [device]
 */
@OptIn(InternalDeviceAPI::class)
private suspend fun <D : Device, T> MutableDevicePropertySpec<D, T>.writeMeta(device: D, item: Meta) {
    write(device, converter.readOrNull(item) ?: error("Meta $item could not be read with $converter"))
}

/**
 * Read Meta item from the [device]
 */
@OptIn(InternalDeviceAPI::class)
private suspend fun <D : Device, T> DevicePropertySpec<D, T>.readMeta(device: D): Meta? =
    read(device)?.let(converter::convert)


private suspend fun <D : Device, I, O> DeviceActionSpec<D, I, O>.executeWithMeta(
    device: D,
    item: Meta,
): Meta? {
    val arg: I = inputConverter.readOrNull(item) ?: error("Failed to convert $item with $inputConverter")
    val res = execute(device, arg)
    return res?.let { outputConverter.convert(res) }
}


/**
 * A base abstractions for [Device], introducing specifications for properties
 */
public abstract class DeviceBase<D : Device>(
    final override val context: Context,
    final override val meta: Meta = Meta.EMPTY,
) : CachingDevice {

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


    private val sharedMessageFlow: MutableSharedFlow<DeviceMessage> = MutableSharedFlow(
        replay = meta["message.buffer"].int ?: 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val coroutineContext: CoroutineContext = context.newCoroutineContext(
        SupervisorJob(context.coroutineContext[Job]) +
                CoroutineName("Device $this") +
                CoroutineExceptionHandler { _, throwable ->
                    launch {
                        sharedMessageFlow.emit(
                            DeviceErrorMessage(
                                errorMessage = throwable.message,
                                errorType = throwable::class.simpleName,
                                errorStackTrace = throwable.stackTraceToString()
                            )
                        )
                    }
                }
    )


    /**
     * Logical state store
     */
    private val logicalState: HashMap<String, Meta?> = HashMap()

    public override val messageFlow: SharedFlow<DeviceMessage> get() = sharedMessageFlow

    @Suppress("UNCHECKED_CAST")
    internal val self: D
        get() = this as D

    private val stateLock = Mutex()

    /**
     * Update logical property state and notify listeners
     */
    protected suspend fun propertyChanged(propertyName: String, value: Meta?) {
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
     * Notify the device that a property with [spec] value is changed
     */
    protected suspend fun <T> propertyChanged(spec: DevicePropertySpec<D, T>, value: T) {
        propertyChanged(spec.name, spec.converter.convert(value))
    }

    /**
     * Force read physical value and push an update if it is changed. It does not matter if logical state is present.
     * The logical state is updated after read
     */
    override suspend fun readProperty(propertyName: String): Meta {
        val spec = properties[propertyName] ?: error("Property with name $propertyName not found")
        val meta = spec.readMeta(self) ?: error("Failed to read property $propertyName")
        propertyChanged(propertyName, meta)
        return meta
    }

    /**
     * Read property if it exists and read correctly. Return null otherwise.
     */
    public suspend fun readPropertyOrNull(propertyName: String): Meta? {
        val spec = properties[propertyName] ?: return null
        val meta = spec.readMeta(self) ?: return null
        propertyChanged(propertyName, meta)
        return meta
    }

    override fun getProperty(propertyName: String): Meta? = logicalState[propertyName]

    override suspend fun invalidate(propertyName: String) {
        stateLock.withLock {
            logicalState.remove(propertyName)
        }
    }

    override suspend fun writeProperty(propertyName: String, value: Meta): Unit {
        //bypass property setting if it already has that value
        if (logicalState[propertyName] == value) {
            logger.debug { "Skipping setting $propertyName to $value because value is already set" }
            return
        }
        when (val property = properties[propertyName]) {
            null -> {
                //If there are no registered physical properties with given name, write a logical one.
                propertyChanged(propertyName, value)
            }

            is MutableDevicePropertySpec -> {
                //if there is a writeable property with a given name, invalidate logical and write physical
                invalidate(propertyName)
                property.writeMeta(self, value)
                // perform read after writing if the writer did not set the value and the value is still in invalid state
                if (logicalState[propertyName] == null) {
                    val meta = property.readMeta(self)
                    propertyChanged(propertyName, meta)
                }
            }

            else -> {
                error("Property $property is not writeable")
            }
        }
    }

    override suspend fun execute(actionName: String, argument: Meta?): Meta? {
        val spec = actions[actionName] ?: error("Action with name $actionName not found")
        return spec.executeWithMeta(self, argument ?: Meta.EMPTY)
    }

    @DFExperimental
    final override var lifecycleState: DeviceLifecycleState = DeviceLifecycleState.STOPPED
        private set(value) {
            if (field != value) {
                launch {
                    sharedMessageFlow.emit(
                        DeviceLifeCycleMessage(value)
                    )
                }
            }
            field = value
        }

    protected open suspend fun onStart() {

    }

    @OptIn(DFExperimental::class)
    final override suspend fun start() {
        if (lifecycleState == DeviceLifecycleState.STOPPED) {
            super.start()
            lifecycleState = DeviceLifecycleState.STARTING
            onStart()
            lifecycleState = DeviceLifecycleState.STARTED
        } else {
            logger.debug { "Device $this is already started" }
        }
    }

    protected open fun onStop() {

    }

    @OptIn(DFExperimental::class)
    final override fun stop() {
        onStop()
        lifecycleState = DeviceLifecycleState.STOPPED
        super.stop()
    }


    abstract override fun toString(): String

}

