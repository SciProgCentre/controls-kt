package space.kscience.controls.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.misc.PropertyHistory
import space.kscience.controls.misc.ValueWithTime
import space.kscience.dataforge.meta.MetaConverter

public fun <T> DeviceMessageStorage.propertyHistory(
    propertyName: String,
    converter: MetaConverter<T>,
): PropertyHistory<T> = object : PropertyHistory<T> {
    override fun flowHistory(from: Instant, until: Instant): Flow<ValueWithTime<T>> =
        read<PropertyChangedMessage>(from..until)
            .filter { it.property == propertyName }
            .map { ValueWithTime(converter.read(it.value), it.time) }
}