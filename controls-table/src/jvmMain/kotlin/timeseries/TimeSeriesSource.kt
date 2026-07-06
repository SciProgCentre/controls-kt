package space.kscience.controls.tagtable.timeseries

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import space.kscience.controls.api.Device
import space.kscience.controls.api.propertyMessageFlow
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.name
import space.kscience.controls.storage.ValueHistory
import space.kscience.controls.storage.collectPropertyHistory
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.MetaConverter

/**
 * A source of time series values
 */
public interface TimeSeriesSource<T> {
    /**
     * Subscribe to updates of the source value
     *
     */
    public suspend fun subscribe(): Flow<ValueWithTime<T>>


    /**
     * An optional history of values. The clock in history is the same as in [subscribe].
     */
    public val history: ValueHistory<T>?
}

/**
 * A source of time series values from a device property
 */
public class PropertyTimeSeriesSource<T>(
    public val device: Device,
    public val propertyName: String,
    public val converter: MetaConverter<T>,
    override val history: ValueHistory<T> = device.collectPropertyHistory(device, propertyName, converter)
) : TimeSeriesSource<T> {
    override suspend fun subscribe(): Flow<ValueWithTime<T>> = device.propertyMessageFlow(propertyName).map {
        ValueWithTime(converter.read(it.value), it.time)
    }
}

/**
 * Create a time series source from a device property spec
 */
public fun <T, D: Device> PropertyTimeSeriesSource(
    device: D,
    propertySpec: DevicePropertySpec<T>
): PropertyTimeSeriesSource<T> = PropertyTimeSeriesSource(device, propertySpec.name, propertySpec.converter)