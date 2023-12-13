package space.kscience.controls.modbus

import com.ghgande.j2mod.modbus.procimg.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import space.kscience.controls.api.Device
import space.kscience.controls.ports.readShort
import space.kscience.controls.spec.*
import space.kscience.dataforge.io.Binary


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
        propertySpec: MutableDevicePropertySpec<D, Boolean>,
    ): ObservableDigitalOut = bind(key) { coil ->
        coil.addObserver { _, _ ->
            device.writeAsync(propertySpec, coil.isSet)
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
        propertySpec: MutableDevicePropertySpec<D, Short>,
    ): ObservableRegister = bind(key) { register ->
        register.addObserver { _, _ ->
            device.writeAsync(propertySpec, register.toShort())
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
            val binary = Binary {
                key.format.writeTo(this, value)
            }
            registers.forEachIndexed { index, register ->
                register.setValue(binary.readShort(index * 2))
            }
        }
    }

    /**
     * Trigger [block] if one of register changes.
     */
    private fun List<ObservableRegister>.onChange(block: suspend (Buffer) -> Unit) {
        var ready = false

        forEach { register ->
            register.addObserver { _, _ ->
                ready = true
            }
        }

        device.launch {
            val builder = Buffer()
            while (isActive) {
                delay(1)
                if (ready) {
                    val packet = builder.apply {
                        forEach { value ->
                            writeShort(value.toShort())
                        }
                    }
                    block(packet)
                    ready = false
                }
            }
        }
    }

    public fun <T> bind(key: ModbusRegistryKey.HoldingRange<T>, propertySpec: MutableDevicePropertySpec<D, T>) {
        val registers = List(key.count) {
            ObservableRegister()
        }

        registers.forEachIndexed { index, register ->
            image.addRegister(key.address + index, register)
        }

        registers.onChange { packet ->
            device.write(propertySpec, key.format.readFrom(packet))
        }

        device.useProperty(propertySpec) { value ->
            val binary = Binary {
                key.format.writeTo(this, value)
            }
            registers.forEachIndexed { index, observableRegister ->
                observableRegister.setValue(binary.readShort(index * 2))
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
            image.addRegister(key.address + index, register)
        }

        registers.onChange { packet ->
            device.launch {
                device.action(key.format.readFrom(packet))
            }
        }

        return registers
    }

}

/**
 * Bind the device to Modbus slave (server) image.
 */
public fun <D : Device> D.bindProcessImage(
    unitId: Int = 0,
    openOnBind: Boolean = true,
    binding: DeviceProcessImageBuilder<D>.() -> Unit,
): ProcessImage {
    val image = SimpleProcessImage(unitId)
    DeviceProcessImageBuilder(this, image).apply(binding)
    image.setLocked(true)
    if (openOnBind) {
        launch {
            start()
        }
    }
    return image
}