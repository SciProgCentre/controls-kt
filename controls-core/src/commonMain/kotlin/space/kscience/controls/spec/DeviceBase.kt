package space.kscience.controls.spec

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.api.*
import space.kscience.controls.time.clock
import space.kscience.controls.time.deviceDispatcher
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.debug
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.int
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock




/**
 * A base abstraction for [Device], introducing specifications for properties
 */
public abstract class DeviceBase(
    final override val context: Context,
    final override val meta: Meta = Meta.EMPTY,
) : CachingDevice {

    /**
     * Collection of property specifications
     */
    protected abstract val readers: Map<String, PropertyReader<*>>

    protected abstract val writers: Map<String, PropertyWriter<*>>

    /**
     * Collection of action specifications
     */
    protected abstract val actions: Map<String, ActionExecutor<*, *>>

    override val propertyDescriptors: Collection<PropertyDescriptor>
        get() = readers.values.map { it.descriptor }

    override val actionDescriptors: Collection<ActionDescriptor>
        get() = actions.values.map { it.descriptor }


    private val sharedMessageFlow: MutableSharedFlow<DeviceMessage> = MutableSharedFlow(
        replay = meta["message.buffer"].int ?: 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    override val coroutineContext: CoroutineContext = context.newCoroutineContext(
        SupervisorJob(context.coroutineContext[Job]) +
                CoroutineName("Device $id") +
                context.deviceDispatcher +
                CoroutineExceptionHandler { _, throwable ->
                    launch {
                        sharedMessageFlow.emit(
                            DeviceErrorMessage(
                                time = clock.now(),
                                errorMessage = throwable.message,
                                errorType = throwable::class.simpleName,
                                errorStackTrace = throwable.stackTraceToString()
                            )
                        )
                    }
                    logger.error(throwable) { "Exception in device $id" }
                }
    )


    /**
     * Logical state store
     */
    private val logicalState: HashMap<String, Meta?> = HashMap()

    public override val messageFlow: SharedFlow<DeviceMessage> get() = sharedMessageFlow

    private val stateLock = Mutex()

    /**
     * Update logical property state and notify listeners
     */
    public suspend fun propertyChanged(propertyName: String, value: Meta?) {
        if (value != logicalState[propertyName]) {
            stateLock.withLock {
                logicalState[propertyName] = value
            }
            if (value != null) {
                sharedMessageFlow.emit(PropertyChangedMessage(clock.now(), propertyName, value))
            }
        }
    }

    /**
     * Notify the device that a property with [spec] value is changed
     */
    public suspend fun <T> propertyChanged(spec: DevicePropertySpec<T>, value: T) {
        propertyChanged(spec.name, spec.converter.convert(value))
    }

    /**
     * Force read physical value and push an update if it is changed. It does not matter if logical state is present.
     * The logical state is updated after read
     */
    override suspend fun readProperty(propertyName: String): Meta {
        val spec = readers[propertyName] ?: error("Property with name $propertyName not found")
        val meta = spec.readMeta()
        propertyChanged(propertyName, meta)
        return meta
    }

    /**
     * Read property if it exists and read correctly. Return null otherwise.
     */
    public suspend fun readPropertyOrNull(propertyName: String): Meta? {
        val reader = readers[propertyName] ?: return null
        val meta = reader.readMeta()
        propertyChanged(propertyName, meta)
        return meta
    }

    override fun getCachedProperty(propertyName: String): Meta? = logicalState[propertyName]

    @InternalDeviceAPI
    override fun setCachedProperty(propertyName: String, value: Meta?) {
        if (value == null) {
            logicalState.remove(propertyName)
        } else {
            logicalState[propertyName] = value
        }
    }

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
        when (val property = writers[propertyName]) {
            null -> {
                //If there are no registered physical properties with given name, write a logical one.
                propertyChanged(propertyName, value)
            }

           else ->  {
                //if there is a writeable property with a given name, invalidate logical and write physical
                invalidate(propertyName)
                property.writeMeta( value)
                // perform read after writing if the writer did not set the value and the value is still in invalid state
                if (logicalState[propertyName] == null) {
                    readers[propertyName]?.let { reader ->
                        val meta = reader.readMeta()
                        propertyChanged(propertyName, meta)
                    }
                }
            }

        }
    }

    override suspend fun execute(actionName: String, argument: Meta?): Meta? {
        val spec = actions[actionName] ?: error("Action with name $actionName not found")
        return spec.executeWithMeta( argument ?: Meta.EMPTY)
    }

    final override var lifecycleState: LifecycleState = LifecycleState.STOPPED
        private set


    private suspend fun setLifecycleState(lifecycleState: LifecycleState) {
        this.lifecycleState = lifecycleState
        sharedMessageFlow.emit(
            DeviceLifeCycleMessage(clock.now(), lifecycleState)
        )
    }

    protected open suspend fun onStart() {

    }

    final override suspend fun start() {
        if (lifecycleState == LifecycleState.STOPPED) {
            super.start()
            setLifecycleState(LifecycleState.STARTING)
            onStart()
            setLifecycleState(LifecycleState.STARTED)
        } else {
            logger.debug { "Device $this is already started" }
        }
    }

    protected open suspend fun onStop() {

    }

    final override suspend fun stop() {
        onStop()
        setLifecycleState(LifecycleState.STOPPED)
        super.stop()
    }

    override val clock: Clock = context.clock

    abstract override fun toString(): String

}

