package storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import space.kscience.controls.api.DeviceMessageSource
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.ValueStateProvider
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.parseAsName
import kotlin.time.Instant


/**
 * An interface for replaying device messages or data from storage
 */
public interface Replay : DeviceMessageSource, AutoCloseable {

    /**
     * Start playback.
     *
     * @param from The time to start playback from. If null, playback starts from the beginning.
     * @param useOriginalTime If true, use the original timestamp of the messages, otherwise use the current time.
     * @param timeScale The scale factor for adjusting the playback speed.
     */
    public suspend fun play(
        from: Instant? = null,
        useOriginalTime: Boolean = false,
        timeScale: Double = 1.0,
    )

    /**
     * Stop playback
     */
    public suspend fun stop()
}

/**
 * Create a [ValueStateProvider] from a [DeviceMessageSource]
 */
public fun DeviceMessageSource.asValueStateProvider(
    scope: CoroutineScope
): ValueStateProvider = ValueStateProvider { context, parameters->
    val deviceName = parameters["deviceName"].string?.parseAsName() ?: error("Device name is not specified")
    val propertyName = parameters["propertyName"].string ?: error("Property name is not specified")
    val defaultValue = parameters["defaultValue"] ?: Meta.EMPTY

    val defaultValueWithTime = ValueWithTime(defaultValue, Instant.DISTANT_PAST)

    val valueFlow: StateFlow<ValueWithTime<Meta>> = messageFlow.filterIsInstance<PropertyChangedMessage>().filter {
        it.sourceDevice == deviceName && it.property == propertyName
    }.map {
        ValueWithTime(it.value, it.time)
    }.stateIn(scope, SharingStarted.Eagerly,defaultValueWithTime)

    object : ValueState<Meta>{
        override val valueWithTime: ValueWithTime<Meta>
            get() = valueFlow.value

        override fun subscribeWithTime(): Flow<ValueWithTime<Meta>>  = valueFlow

        override fun toString(): String = "ValueState.fromDeviceMessageSource($deviceName, $propertyName)"

    }
}