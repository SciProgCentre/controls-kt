package space.kscience.controls.modbus

import com.ghgande.j2mod.modbus.procimg.*
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.readByteBuffer
import io.ktor.utils.io.core.writeShort
import space.kscience.controls.api.Device
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.WritableDevicePropertySpec
import space.kscience.controls.spec.set
import space.kscience.controls.spec.useProperty


public class DeviceToModbusMapping<D : Device> private constructor(
    private val mapping: Map<DevicePropertySpec<D, *>, ModbusRegistryKey>,
) : Map<DevicePropertySpec<D, *>, ModbusRegistryKey> by mapping {
    public class Builder<D : Device> {
        private val mapping = HashMap<DevicePropertySpec<D, *>, ModbusRegistryKey>()

        public fun bind(propertySpec: DevicePropertySpec<D, Boolean>, key: ModbusRegistryKey.DiscreteInput) {
            mapping[propertySpec] = key
        }

        public fun bind(propertySpec: WritableDevicePropertySpec<D, Boolean>, key: ModbusRegistryKey.Coil) {
            mapping[propertySpec] = key
        }

        public fun bind(propertySpec: DevicePropertySpec<D, Short>, key: ModbusRegistryKey.InputRegister) {
            mapping[propertySpec] = key
        }

        public fun bind(propertySpec: WritableDevicePropertySpec<D, Short>, key: ModbusRegistryKey.HoldingRegister) {
            mapping[propertySpec] = key
        }

        public fun <T> bind(propertySpec: DevicePropertySpec<D, T>, key: ModbusRegistryKey.InputRange<T>) {
            mapping[propertySpec] = key
        }

        public fun <T> bind(propertySpec: WritableDevicePropertySpec<D, T>, key: ModbusRegistryKey.HoldingRange<T>) {
            mapping[propertySpec] = key
        }

        public fun build(): DeviceToModbusMapping<D> = DeviceToModbusMapping(mapping)
    }
}

public inline fun <D: Device> DeviceToModbusMapping(block: DeviceToModbusMapping.Builder<D>.()->Unit): DeviceToModbusMapping<D> =
    DeviceToModbusMapping.Builder<D>().apply(block).build()

@Suppress("UNCHECKED_CAST")
public fun <D : Device> D.toProcessImage(mapping: DeviceToModbusMapping<D>): ProcessImage {
    val image = SimpleProcessImage()
    mapping.forEach { (spec, key) ->
        when (key) {
            is ModbusRegistryKey.Coil -> {
                spec as WritableDevicePropertySpec<D, Boolean>
                val coil = ObservableDigitalOut()
                coil.addObserver { _, _ ->
                    set(spec, coil.isSet)
                }
                image.setDigitalOut(key.address, coil)
                useProperty(spec) { value ->
                    coil.set(value)
                }
            }

            is ModbusRegistryKey.DiscreteInput -> {
                spec as DevicePropertySpec<D, Boolean>
                val input = SimpleDigitalIn()
                image.setDigitalIn(key.address, input)
                useProperty(spec) { value ->
                    input.set(value)
                }
            }

            is ModbusRegistryKey.HoldingRegister -> {
                spec as WritableDevicePropertySpec<D, Short>
                val register = ObservableRegister()
                register.addObserver { _, _ ->
                    set(spec, register.toShort())
                }
                image.setRegister(key.address, register)
                useProperty(spec) { value ->
                    register.setValue(value)
                }
            }

            is ModbusRegistryKey.InputRegister -> {
                spec as DevicePropertySpec<D, Short>
                val input = SimpleInputRegister()
                image.setRegister(key.address, input)
                useProperty(spec) { value ->
                    input.setValue(value)
                }
            }

            is ModbusRegistryKey.HoldingRange<*> -> {
                spec as WritableDevicePropertySpec<D, Any?>
                key as ModbusRegistryKey.HoldingRange<Any?>
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
                        set(spec, key.format.readObject(packet))
                    }
                    image.setRegister(key.address + index, register)
                }

                useProperty(spec) { value ->
                    val packet = buildPacket {
                        key.format.writeObject(this, value)
                    }.readByteBuffer()
                    registers.forEachIndexed { index, observableRegister ->
                        observableRegister.setValue(packet.getShort(index * 2))
                    }
                }
            }

            is ModbusRegistryKey.InputRange<*> -> {
                spec as DevicePropertySpec<D, Any?>
                key as ModbusRegistryKey.InputRange<Any?>
                val registers = List(key.count) {
                    SimpleInputRegister()
                }

                useProperty(spec) { value ->
                    val packet = buildPacket {
                        key.format.writeObject(this, value)
                    }.readByteBuffer()
                    registers.forEachIndexed { index, register ->
                        register.setValue(packet.getShort(index * 2))
                    }
                }
            }
        }
    }
    return image
}

public inline fun <D : Device> D.toProcessImage(block: DeviceToModbusMapping.Builder<D>.()->Unit): ProcessImage =
    toProcessImage(DeviceToModbusMapping(block))