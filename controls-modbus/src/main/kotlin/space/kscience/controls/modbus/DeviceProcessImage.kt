package space.kscience.controls.modbus

import com.ghgande.j2mod.modbus.procimg.*
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.readByteBuffer
import io.ktor.utils.io.core.writeShort
import kotlinx.coroutines.launch
import space.kscience.controls.api.Device
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.WritableDevicePropertySpec
import space.kscience.controls.spec.set
import space.kscience.controls.spec.useProperty


public class DeviceProcessImageBuilder<D : Device> internal constructor(
    private val device: D,
    public val image: ProcessImageImplementation,
) {

    public fun bind(
        key: ModbusRegistryKey.Coil,
        block: D.(ObservableDigitalOut) -> Unit = {},
    ): ObservableDigitalOut {
        val coil = ObservableDigitalOut()
        device.block(coil)
        image.addDigitalOut(key.address, coil)
        return coil
    }

    public fun bind(
        key: ModbusRegistryKey.Coil,
        propertySpec: WritableDevicePropertySpec<D, Boolean>,
    ): ObservableDigitalOut = bind(key) { coil ->
        coil.addObserver { _, _ ->
            device[propertySpec] = coil.isSet
        }
        device.useProperty(propertySpec) { value ->
            coil.set(value)
        }
    }

    public fun bind(
        key: ModbusRegistryKey.DiscreteInput,
        block: D.(SimpleDigitalIn) -> Unit = {},
    ): DigitalIn {
        val input = SimpleDigitalIn()
        device.block(input)
        image.addDigitalIn(key.address, input)
        return input
    }

    public fun bind(
        key: ModbusRegistryKey.DiscreteInput,
        propertySpec: DevicePropertySpec<D, Boolean>,
    ): DigitalIn = bind(key) { input ->
        device.useProperty(propertySpec) { value ->
            input.set(value)
        }
    }

    public fun bind(
        key: ModbusRegistryKey.InputRegister,
        block: D.(SimpleInputRegister) -> Unit = {},
    ): SimpleInputRegister {
        val input = SimpleInputRegister()
        device.block(input)
        image.addInputRegister(key.address, input)
        return input
    }

    public fun bind(
        key: ModbusRegistryKey.InputRegister,
        propertySpec: DevicePropertySpec<D, Short>,
    ): SimpleInputRegister = bind(key) { input ->
        device.useProperty(propertySpec) { value ->
            input.setValue(value)
        }
    }

    public fun bind(
        key: ModbusRegistryKey.HoldingRegister,
        block: D.(ObservableRegister) -> Unit = {},
    ): ObservableRegister {
        val register = ObservableRegister()
        device.block(register)
        image.addRegister(key.address, register)
        return register
    }

    public fun bind(
        key: ModbusRegistryKey.HoldingRegister,
        propertySpec: WritableDevicePropertySpec<D, Short>,
    ): ObservableRegister = bind(key) { register ->
        register.addObserver { _, _ ->
            device[propertySpec] = register.toShort()
        }
        device.useProperty(propertySpec) { value ->
            register.setValue(value)
        }
    }

    public fun <T> bind(key: ModbusRegistryKey.InputRange<T>, propertySpec: DevicePropertySpec<D, T>) {
        val registers = List(key.count) {
            SimpleInputRegister()
        }

        registers.forEachIndexed { index, register ->
            image.addInputRegister(key.address + index, register)
        }

        device.useProperty(propertySpec) { value ->
            val packet = buildPacket {
                key.format.writeObject(this, value)
            }.readByteBuffer()
            registers.forEachIndexed { index, register ->
                register.setValue(packet.getShort(index * 2))
            }
        }
    }

    public fun <T> bind(key: ModbusRegistryKey.HoldingRange<T>, propertySpec: WritableDevicePropertySpec<D, T>) {
        val registers = List(key.count) {
            ObservableRegister()
        }
        registers.forEachIndexed { index, register ->
            register.addObserver { _, _ ->
                val packet = buildPacket {
                    registers.forEach { value ->
                        writeShort(value.toShort())
                    }
                }
                device[propertySpec] = key.format.readObject(packet)
            }
            image.addRegister(key.address + index, register)
        }

        device.useProperty(propertySpec) { value ->
            val packet = buildPacket {
                key.format.writeObject(this, value)
            }.readByteBuffer()
            registers.forEachIndexed { index, observableRegister ->
                observableRegister.setValue(packet.getShort(index * 2))
            }
        }
    }

    public fun bindAction(
        key: ModbusRegistryKey.Coil,
        action: suspend D.(Boolean) -> Unit,
    ): ObservableDigitalOut {
        val coil = ObservableDigitalOut()
        coil.addObserver { _, _ ->
            device.launch {
                device.action(coil.isSet)
            }
        }
        image.addDigitalOut(key.address, coil)
        return coil
    }

    public fun bindAction(
        key: ModbusRegistryKey.HoldingRegister,
        action: suspend D.(Short) -> Unit,
    ): ObservableRegister {
        val register = ObservableRegister()
        register.addObserver { _, _ ->

            with(device) {
                launch {
                    action(register.toShort())
                }
            }
        }
        image.addRegister(key.address, register)
        return register
    }

    public fun <T> bindAction(
        key: ModbusRegistryKey.HoldingRange<T>,
        action: suspend D.(T) -> Unit,
    ): List<ObservableRegister> {
        val registers = List(key.count) {
            ObservableRegister()
        }
        registers.forEachIndexed { index, register ->
            register.addObserver { _, _ ->
                val packet = buildPacket {
                    registers.forEach { value ->
                        writeShort(value.toShort())
                    }
                }
                device.launch {
                    device.action(key.format.readObject(packet))
                }
            }
            image.addRegister(key.address + index, register)
        }

        return registers
    }

}

/**
 * Bind the device to Modbus slave (server) image.
 */
public fun <D : Device> D.bindProcessImage(
    openOnBind: Boolean = true,
    binding: DeviceProcessImageBuilder<D>.() -> Unit,
): ProcessImage {
    val image = SimpleProcessImage()
    DeviceProcessImageBuilder(this, image).apply(binding)
    if (openOnBind) {
        launch {
            open()
        }
    }
    return image
}