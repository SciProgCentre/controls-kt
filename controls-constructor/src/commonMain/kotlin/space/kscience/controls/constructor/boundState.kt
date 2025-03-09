package space.kscience.controls.constructor

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import space.kscience.controls.api.Device
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.api.id
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.MutableDevicePropertySpec
import space.kscience.controls.spec.name
import space.kscience.dataforge.meta.MetaConverter


/**
 * A copy-free [DeviceState] bound to a device property
 */
private open class BoundDeviceState<T>(
    val converter: MetaConverter<T>,
    val device: Device,
    val propertyName: String,
    initialValue: T,
) : DeviceState<T> {

    override val valueFlow: StateFlow<T> = device.messageFlow.filterIsInstance<PropertyChangedMessage>().filter {
        it.property == propertyName
    }.mapNotNull {
        converter.read(it.value)
    }.stateIn(device.context, SharingStarted.Eagerly, initialValue)

    override val value: T get() = valueFlow.value
    override fun toString(): String =
        "BoundDeviceState(converter=$converter, device=${device.id}, propertyName='$propertyName')"


}

public fun <T> Device.propertyAsState(
    propertyName: String,
    metaConverter: MetaConverter<T>,
    initialValue: T,
): DeviceState<T> = BoundDeviceState(metaConverter, this, propertyName, initialValue)

/**
 * Bind a read-only [DeviceState] to a [Device] property
 */
public suspend fun <T> Device.propertyAsState(
    propertyName: String,
    metaConverter: MetaConverter<T>,
): DeviceState<T> = propertyAsState(
    propertyName,
    metaConverter,
    metaConverter.readOrNull(readProperty(propertyName)) ?: error("Conversion of property failed")
)

public suspend fun <D : Device, T> D.propertyAsState(
    propertySpec: DevicePropertySpec<D, T>,
): DeviceState<T> = propertyAsState(propertySpec.name, propertySpec.converter)

public fun <D : Device, T> D.propertyAsState(
    propertySpec: DevicePropertySpec<D, T>,
    initialValue: T,
): DeviceState<T> = propertyAsState(propertySpec.name, propertySpec.converter, initialValue)


private class MutableBoundDeviceState<T>(
    converter: MetaConverter<T>,
    device: Device,
    propertyName: String,
    initialValue: T,
) : BoundDeviceState<T>(converter, device, propertyName, initialValue), MutableDeviceState<T> {

    override var value: T
        get() = valueFlow.value
        set(newValue) {
            device.launch {
                device.writeProperty(propertyName, converter.convert(newValue))
            }
        }
}

public fun <T> Device.mutablePropertyAsState(
    propertyName: String,
    metaConverter: MetaConverter<T>,
    initialValue: T,
): MutableDeviceState<T> = MutableBoundDeviceState(metaConverter, this, propertyName, initialValue)

public suspend fun <T> Device.mutablePropertyAsState(
    propertyName: String,
    metaConverter: MetaConverter<T>,
): MutableDeviceState<T> {
    val initialValue = metaConverter.readOrNull(readProperty(propertyName)) ?: error("Conversion of property failed")
    return mutablePropertyAsState(propertyName, metaConverter, initialValue)
}

public suspend fun <D : Device, T> D.propertyAsState(
    propertySpec: MutableDevicePropertySpec<D, T>,
): MutableDeviceState<T> = mutablePropertyAsState(propertySpec.name, propertySpec.converter)

public fun <D : Device, T> D.propertyAsState(
    propertySpec: MutableDevicePropertySpec<D, T>,
    initialValue: T,
): MutableDeviceState<T> = mutablePropertyAsState(propertySpec.name, propertySpec.converter, initialValue)

