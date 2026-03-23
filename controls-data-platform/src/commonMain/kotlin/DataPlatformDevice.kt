package space.kscience.controls.dataplatform

import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import space.kscience.controls.api.*
import space.kscience.controls.time.ClockManager
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.debug
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.parseAsName
import space.kscience.dataforge.names.toStringUnescaped
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/**
 * A device that exposes property values in a data platform
 */
public class DataPlatformDevice(
    override val context: Context,
    public val configuration: DataPlatformConfiguration
) : Device {

    override val coroutineContext: CoroutineContext = context.coroutineContext + SupervisorJob(context.coroutineContext[Job])

    private val clockManager = context.request(ClockManager)

    override val clock: Clock = clockManager.clock

    public val platform: DataPlatform = DataPlatform(context, configuration)

    private val values = mutableMapOf<Name, Meta>()

    private val _messageFlow = MutableSharedFlow<DeviceMessage>(configuration.properties.size * 2)

    override val messageFlow: SharedFlow<DeviceMessage> get() = _messageFlow

    private val readJob = launch {
        configuration.properties.entries.groupBy { it.value.timer }.forEach { (timerName, properties) ->
            val timer = configuration.timers[timerName]?.timer(clockManager) ?: error("Timer $timerName not found")
            timer.subscribe().onEach { instant ->
                properties.forEach { (propertyName, property) ->
                    val value = property.read(platform)
                    values[propertyName] = value.value
                    _messageFlow.emit(
                        PropertyChangedMessage(
                            time = value.time,
                            property = propertyName.toStringUnescaped(),
                            value = value.value,
                        )
                    )
                }
            }.launchIn(this)
        }
    }

    override val propertyDescriptors: Collection<PropertyDescriptor> =
        configuration.properties.map { (name, platformProperty) ->
            PropertyDescriptor(name.toString())
            //TODO add type descriptors
        }

    override val actionDescriptors: Collection<ActionDescriptor> = emptyList()

    override suspend fun readProperty(propertyName: String): Meta =
        values[propertyName.parseAsName(true)] ?: error("Property $propertyName not found")

    override suspend fun writeProperty(propertyName: String, value: Meta) {
        error("Write is not supported")
    }

    override suspend fun execute(
        actionName: String,
        argument: Meta?
    ): Meta? {
        TODO("Not yet implemented")
    }

    override var lifecycleState: LifecycleState = LifecycleState.STOPPED
        private set


    private suspend fun setLifecycleState(lifecycleState: LifecycleState) {
        this.lifecycleState = lifecycleState
        _messageFlow.emit(
            DeviceLifeCycleMessage(clock.now(), lifecycleState)
        )
    }

    final override suspend fun start() {
        if (lifecycleState == LifecycleState.STOPPED) {
            super.start()
            setLifecycleState(LifecycleState.STARTING)
            setLifecycleState(LifecycleState.STARTED)
        } else {
            logger.debug { "Device $this is already started" }
        }
    }

    final override suspend fun stop() {
        setLifecycleState(LifecycleState.STOPPED)
        super.stop()
    }

}