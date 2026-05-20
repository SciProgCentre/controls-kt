package space.kscience.controls.constructor

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.kscience.controls.api.CachingDevice
import space.kscience.controls.api.Device
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.api.id
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.name
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.MetaConverter


/**
 * A copy-free [ValueState] bound to a device property
 */
private open class PropertyValueState<T>(
    val converter: MetaConverter<T>,
    val device: Device,
    val propertyName: String,
    initialValue: T,
) : ValueState<T> {

    val valueFlowWithTime: StateFlow<ValueWithTime<T>> =
        device.messageFlow.filterIsInstance<PropertyChangedMessage>().filter {
            it.property == propertyName
        }.mapNotNull {
            val value = converter.read(it.value) ?: return@mapNotNull null
            ValueWithTime(value, it.time)
        }.stateIn(device.context, SharingStarted.Eagerly, ValueWithTime(initialValue, device.clock.now()))

    override fun subscribeWithTime(): StateFlow<ValueWithTime<T>> = valueFlowWithTime

    override val valueWithTime: ValueWithTime<T> get() = valueFlowWithTime.value

    override fun toString(): String =
        "PropertyValueState(converter=$converter, device=${device.id}, propertyName='$propertyName')"
}

/**
 * Read device property as a [ValueState]. Use [initialValue] as a starting value if the device does not provide current value.
 */
public fun <T> Device.propertyAsState(
    propertyName: String,
    metaConverter: MetaConverter<T>,
    initialValue: T,
): ValueState<T> {
    if (propertyDescriptors.find { it.name == propertyName } == null) error("Property '$propertyName' not found in device ${this.id}")
    return when (this) {
        //avoid creating wrapper ValueState for DeviceGroup
        is DeviceGroup -> propertyAsState(propertyName, metaConverter)

        //use cached value if available
        is CachingDevice -> PropertyValueState(
            converter = metaConverter,
            device = this,
            propertyName = propertyName,
            initialValue = getCachedProperty(propertyName)?.let { metaConverter.read(it) } ?: initialValue
        )

        else -> PropertyValueState(metaConverter, this, propertyName, initialValue)
    }
}

/**
 * Bind a read-only [ValueState] to a [Device] property
 */
public suspend fun <T> Device.propertyAsState(
    propertyName: String,
    metaConverter: MetaConverter<T>,
): ValueState<T> = propertyAsState(
    propertyName,
    metaConverter,
    metaConverter.readOrNull(readProperty(propertyName)) ?: error("Conversion of property failed")
)

public suspend fun <T> Device.propertyAsState(
    propertySpec: DevicePropertySpec<T>,
): ValueState<T> = propertyAsState(propertySpec.name, propertySpec.converter)

public fun <T> Device.propertyAsState(
    propertySpec: DevicePropertySpec<T>,
    initialValue: T,
): ValueState<T> = propertyAsState(propertySpec.name, propertySpec.converter, initialValue)


private class MutablePropertyValueState<T>(
    converter: MetaConverter<T>,
    device: Device,
    propertyName: String,
    initialValue: T,
) : PropertyValueState<T>(converter, device, propertyName, initialValue), MutableValueState<T> {

    override var value: T
        get() = valueFlowWithTime.value.value
        set(newValue) {
            device.launch {
                device.writeProperty(propertyName, converter.convert(newValue))
            }
        }

    override suspend fun emit(value: T) {
        withContext(device.coroutineContext.minusKey(Job)) {
            device.writeProperty(propertyName, converter.convert(value))
        }
    }
}

public fun <T> Device.mutablePropertyAsState(
    propertyName: String,
    metaConverter: MetaConverter<T>,
    initialValue: T,
): MutableValueState<T> = MutablePropertyValueState(metaConverter, this, propertyName, initialValue)

public suspend fun <T> Device.mutablePropertyAsState(
    propertyName: String,
    metaConverter: MetaConverter<T>,
): MutableValueState<T> {
    val initialValue = metaConverter.readOrNull(readProperty(propertyName)) ?: error("Conversion of property failed")
    return mutablePropertyAsState(propertyName, metaConverter, initialValue)
}

public suspend fun <T> Device.mutablePropertyAsState(
    propertySpec: DevicePropertySpec<T>,
): MutableValueState<T> = mutablePropertyAsState(propertySpec.name, propertySpec.converter)

public fun <T> Device.mutablePropertyAsState(
    propertySpec: DevicePropertySpec<T>,
    initialValue: T,
): MutableValueState<T> = mutablePropertyAsState(propertySpec.name, propertySpec.converter, initialValue)

