package space.kscience.controls.client

import com.benasher44.uuid.uuid4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import space.kscience.controls.api.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.name
import space.kscience.controls.time.clock
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.dataforge.names.Name
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.send
import space.kscience.magix.api.subscribe
import kotlin.coroutines.CoroutineContext

private fun stringUID() = uuid4().leastSignificantBits.toString(16)

/**
 * A remote-accessible device that relies on connection via Magix
 */
public class DeviceClient internal constructor(
    override val context: Context,
    private val deviceName: Name,
    propertyDescriptors: Collection<PropertyDescriptor>,
    actionDescriptors: Collection<ActionDescriptor>,
    incomingFlow: Flow<DeviceMessage>,
    private val send: suspend (DeviceMessage) -> Unit,
) : CachingDevice {


    override var actionDescriptors: Collection<ActionDescriptor> = actionDescriptors
        internal set

    override var propertyDescriptors: Collection<PropertyDescriptor> = propertyDescriptors
        internal set

    override val coroutineContext: CoroutineContext = context.coroutineContext + Job(context.coroutineContext[Job])

    private val mutex = Mutex()

    private val propertyCache = HashMap<String, Meta>()

    private val flowInternal = incomingFlow.filter {
        it.sourceDevice == deviceName
    }.onEach { message ->
        when (message) {
            is PropertyChangedMessage -> mutex.withLock {
                propertyCache[message.property] = message.value
            }

            else -> {
                //ignore
            }
        }
    }.shareIn(this, started = SharingStarted.Eagerly)

    override val messageFlow: Flow<DeviceMessage> get() = flowInternal


    override suspend fun readProperty(propertyName: String): Meta {
        send(
            PropertyGetMessage(clock.now(), propertyName, targetDevice = deviceName)
        )
        return messageFlow.filterIsInstance<PropertyChangedMessage>().first {
            it.property == propertyName
        }.value
    }

    override fun getProperty(propertyName: String): Meta? = propertyCache[propertyName]

    override suspend fun invalidate(propertyName: String) {
        mutex.withLock {
            propertyCache.remove(propertyName)
        }
    }

    override suspend fun writeProperty(propertyName: String, value: Meta) {
        send(
            PropertySetMessage(
                time = clock.now(),
                property = propertyName,
                value = value,
                targetDevice = deviceName
            )
        )
    }

    override suspend fun execute(actionName: String, argument: Meta?): Meta? {
        val id = stringUID()
        send(
            ActionExecuteMessage(
                time = clock.now(),
                action = actionName,
                argument = argument,
                requestId = id,
                targetDevice = deviceName
            )
        )
        return messageFlow.filterIsInstance<ActionResultMessage>().first {
            it.action == actionName && it.requestId == id
        }.result
    }

    private val lifecycleStateFlow = messageFlow.filterIsInstance<DeviceLifeCycleMessage>()
        .map { it.state }.stateIn(this, started = SharingStarted.Eagerly, LifecycleState.STARTED)

    @DFExperimental
    override val lifecycleState: LifecycleState get() = lifecycleStateFlow.value

    override val clock: Clock = context.clock
}

/**
 * Connect to a remote device via this endpoint.
 *
 * @param context a [Context] to run device in
 * @param thisEndpoint the name of this endpoint
 * @param deviceEndpoint the name of endpoint in Magix to connect to
 * @param deviceName the name of device within endpoint
 */
public suspend fun MagixEndpoint.remoteDevice(
    context: Context,
    thisEndpoint: String,
    deviceEndpoint: String,
    deviceName: Name,
): DeviceClient = coroutineScope {
    val subscription = subscribe(DeviceManager.magixFormat, originFilter = listOf(deviceEndpoint))
        .map { it.second }
        .filter {
            it.sourceDevice == null || it.sourceDevice == deviceName
        }.shareIn(context, SharingStarted.Lazily,10)

    val deferredDescriptorMessage = CompletableDeferred<DescriptionMessage>()

    launch {
        deferredDescriptorMessage.complete(
            subscription.filterIsInstance<DescriptionMessage>().first()
        )
    }

    send(
        format = DeviceManager.magixFormat,
        payload = GetDescriptionMessage(Clock.System.now(), targetDevice = deviceName),
        source = thisEndpoint,
        target = deviceEndpoint,
        id = stringUID()
    )


    val descriptionMessage = deferredDescriptorMessage.await()

    DeviceClient(
        context = context,
        deviceName = deviceName,
        propertyDescriptors = descriptionMessage.properties,
        actionDescriptors = descriptionMessage.actions,
        incomingFlow = subscription
    ) {
        send(
            format = DeviceManager.magixFormat,
            payload = it,
            source = thisEndpoint,
            target = deviceEndpoint,
            id = stringUID()
        )
    }
}

/**
 * Create a dynamic [DeviceHub] from incoming messages
 */
public suspend fun MagixEndpoint.remoteDeviceHub(
    context: Context,
    thisEndpoint: String,
    deviceEndpoint: String,
): DeviceHub {
    val devices = mutableMapOf<Name, DeviceClient>()
    val subscription = subscribe(DeviceManager.magixFormat, originFilter = listOf(deviceEndpoint))
        .map { it.second }
        .shareIn(context, SharingStarted.Eagerly)
    subscription.filterIsInstance<DescriptionMessage>().onEach { descriptionMessage ->
        devices.getOrPut(descriptionMessage.sourceDevice) {
            DeviceClient(
                context = context,
                deviceName = descriptionMessage.sourceDevice,
                propertyDescriptors = descriptionMessage.properties,
                actionDescriptors = descriptionMessage.actions,
                incomingFlow = subscription
            ) {
                send(
                    format = DeviceManager.magixFormat,
                    payload = it,
                    source = thisEndpoint,
                    target = deviceEndpoint,
                    id = stringUID()
                )
            }
        }.run {
            propertyDescriptors = descriptionMessage.properties
        }
    }.launchIn(context)


    send(
        format = DeviceManager.magixFormat,
        payload = GetDescriptionMessage(Clock.System.now(), targetDevice = null),
        source = thisEndpoint,
        target = deviceEndpoint,
        id = stringUID()
    )

    return DeviceHub(devices)
}

/**
 * Request a description update for all devices on an endpoint
 */
public suspend fun MagixEndpoint.requestDeviceUpdate(
    thisEndpoint: String,
    deviceEndpoint: String,
) {
    send(
        format = DeviceManager.magixFormat,
        payload = GetDescriptionMessage(Clock.System.now()),
        source = thisEndpoint,
        target = deviceEndpoint,
        id = stringUID()
    )
}

/**
 * Subscribe on specific property of a device without creating a device
 */
public fun <T> MagixEndpoint.controlsPropertyFlow(
    endpointName: String,
    deviceName: Name,
    propertySpec: DevicePropertySpec<*, T>,
): Flow<T> {
    val subscription = subscribe(DeviceManager.magixFormat, originFilter = listOf(endpointName)).map { it.second }

    return subscription.filterIsInstance<PropertyChangedMessage>()
        .filter { message ->
            message.sourceDevice == deviceName && message.property == propertySpec.name
        }.map {
            propertySpec.converter.read(it.value)
        }
}

public suspend fun <T> MagixEndpoint.sendControlsPropertyChange(
    sourceEndpointName: String,
    targetEndpointName: String,
    deviceName: Name,
    propertySpec: DevicePropertySpec<*, T>,
    value: T,
) {
    val message = PropertySetMessage(
        Clock.System.now(),
        property = propertySpec.name,
        value = propertySpec.converter.convert(value),
        targetDevice = deviceName
    )
    send(DeviceManager.magixFormat, message, source = sourceEndpointName, target = targetEndpointName)
}

/**
 * Subscribe on property change messages together with property values
 */
public fun <T> MagixEndpoint.controlsPropertyMessageFlow(
    endpointName: String,
    deviceName: Name,
    propertySpec: DevicePropertySpec<*, T>,
): Flow<Pair<PropertyChangedMessage, T>> {
    val subscription = subscribe(DeviceManager.magixFormat, originFilter = listOf(endpointName)).map { it.second }

    return subscription.filterIsInstance<PropertyChangedMessage>()
        .filter { message ->
            message.sourceDevice == deviceName && message.property == propertySpec.name
        }.map {
            it to propertySpec.converter.read(it.value)
        }
}