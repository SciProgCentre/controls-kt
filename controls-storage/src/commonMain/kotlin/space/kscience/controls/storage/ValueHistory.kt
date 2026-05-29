package space.kscience.controls.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import space.kscience.controls.api.Device
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.name
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.names.Name
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * An interface for a device property history.
 */
public interface ValueHistory<T> {
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
public class CollectedValueHistory<T>(
    public val scope: CoroutineScope,
    eventFlow: Flow<DeviceMessage>,
    public val propertyName: String,
    public val converter: MetaConverter<T>,
    public val deviceName: Name = Name.EMPTY,
    maxSize: Int = 1000,
) : ValueHistory<T> {

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
    propertyName: String,
    converter: MetaConverter<T>,
    deviceName: Name = Name.EMPTY,
    maxSize: Int = 1000,
): ValueHistory<T> = CollectedValueHistory(scope, messageFlow, propertyName, converter, deviceName, maxSize)

public fun <D : Device, T> D.collectPropertyHistory(
    scope: CoroutineScope = this,
    spec: DevicePropertySpec<T>,
    deviceName: Name = Name.EMPTY,
    maxSize: Int = 1000,
): ValueHistory<T> = collectPropertyHistory(scope, spec.name, spec.converter, deviceName, maxSize)