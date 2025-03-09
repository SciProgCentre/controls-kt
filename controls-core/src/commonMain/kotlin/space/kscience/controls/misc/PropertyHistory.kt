package space.kscience.controls.misc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import space.kscience.controls.api.Device
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.name
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.names.Name

/**
 * An interface for device property history.
 */
public interface PropertyHistory<T> {
    /**
     * Flow property values filtered by a time range. The implementation could flow it as a chunk or provide paging.
     * So the resulting flow is allowed to suspend.
     *
     * If [until] is in the future, the resulting flow is potentially unlimited.
     * Theoretically, it could be also unlimited if the event source keeps producing new event with timestamp in a given range.
     */
    public fun flowHistory(
        from: Instant = Instant.DISTANT_PAST,
        until: Instant = Clock.System.now(),
    ): Flow<ValueWithTime<T>>
}

/**
 * An in-memory property values history collector
 */
public class CollectedPropertyHistory<T>(
    public val scope: CoroutineScope,
    eventFlow: Flow<DeviceMessage>,
    public val deviceName: Name,
    public val propertyName: String,
    public val converter: MetaConverter<T>,
    maxSize: Int = 1000,
) : PropertyHistory<T> {

    private val store: SharedFlow<ValueWithTime<T>> = eventFlow
        .filterIsInstance<PropertyChangedMessage>()
        .filter { it.sourceDevice == deviceName && it.property == propertyName }
        .map { ValueWithTime(converter.read(it.value), it.time) }
        .shareIn(scope, started = SharingStarted.Eagerly, replay = maxSize)

    override fun flowHistory(from: Instant, until: Instant): Flow<ValueWithTime<T>> =
        store.filter { it.time in from..until }
}

/**
 * Collect and store in memory device property changes for a given property
 */
public fun <T> Device.collectPropertyHistory(
    scope: CoroutineScope = this,
    deviceName: Name,
    propertyName: String,
    converter: MetaConverter<T>,
    maxSize: Int = 1000,
): PropertyHistory<T> = CollectedPropertyHistory(scope, messageFlow, deviceName, propertyName, converter, maxSize)

public fun <D : Device, T> D.collectPropertyHistory(
    scope: CoroutineScope = this,
    deviceName: Name,
    spec: DevicePropertySpec<D, T>,
    maxSize: Int = 1000,
): PropertyHistory<T> = collectPropertyHistory(scope, deviceName, spec.name, spec.converter, maxSize)