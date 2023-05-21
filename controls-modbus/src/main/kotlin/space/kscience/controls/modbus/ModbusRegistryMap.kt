package space.kscience.controls.modbus

import space.kscience.dataforge.io.IOFormat
import space.kscience.dataforge.io.IOReader


public sealed class ModbusRegistryKey {
    /**
     * Read-only boolean value
     */
    public class Coil(public val address: Int) : ModbusRegistryKey() {
        init {
            require(address in 1..9999) { "Coil address must be in 1..9999 range" }
        }
    }

    /**
     * Read-write boolean value
     */
    public class DiscreteInput(public val address: Int) : ModbusRegistryKey() {
        init {
            require(address in 10001..19999) { "DiscreteInput address must be in 10001..19999 range" }
        }
    }

    /**
     * Read-only binary value
     */
    public class InputRegister(public val address: Int) : ModbusRegistryKey() {
        init {
            require(address in 20001..29999) { "InputRegister address must be in 20001..29999 range" }
        }
    }

    public class InputRange<T>(public val address: Int, public val count: Int, public val format: IOReader<T>) {
        public val endAddress: Int get() = address + count

        init {
            require(address in 20001..29999) { "InputRange begin address is $address, but must be in 20001..29999 range" }
            require(endAddress in 20001..29999) { "InputRange end address is ${endAddress}, but must be in 20001..29999 range" }
        }
    }

    public class HoldingRegister(public val address: Int) : ModbusRegistryKey() {
        init {
            require(address in 30001..39999) { "HoldingRegister address must be in 30001..39999 range" }
        }
    }

    public class HoldingRange<T>(public val address: Int, public val count: Int, public val format: IOFormat<T>) {
        public val endAddress: Int get() = address + count

        init {
            require(address in 30001..39999) { "HoldingRange begin address is $address, but must be in 30001..39999 range" }
            require(endAddress in 30001..39999) { "HoldingRange end address is ${endAddress}, but must be in 30001..39999 range" }
        }
    }
}

public abstract class ModbusRegistryMap {
    protected fun coil(address: Int): ModbusRegistryKey.Coil = ModbusRegistryKey.Coil(address)

    protected fun coilByOffset(offset: Int): ModbusRegistryKey.Coil = ModbusRegistryKey.Coil(offset)

    protected fun discrete(address: Int): ModbusRegistryKey.DiscreteInput = ModbusRegistryKey.DiscreteInput(address)

    protected fun discreteByOffset(offset: Int): ModbusRegistryKey.DiscreteInput =
        ModbusRegistryKey.DiscreteInput(10000 + offset)

    protected fun input(address: Int): ModbusRegistryKey.InputRegister = ModbusRegistryKey.InputRegister(address)

    protected fun <T> input(address: Int, count: Int, reader: IOReader<T>): ModbusRegistryKey.InputRange<T> =
        ModbusRegistryKey.InputRange(address, count, reader)


    protected fun inputByOffset(offset: Int): ModbusRegistryKey.InputRegister =
        ModbusRegistryKey.InputRegister(20000 + offset)

    protected fun <T> inputByOffset(offset: Int, count: Int, reader: IOReader<T>): ModbusRegistryKey.InputRange<T> =
        ModbusRegistryKey.InputRange(20000 + offset, count, reader)

    protected fun register(address: Int): ModbusRegistryKey.HoldingRegister = ModbusRegistryKey.HoldingRegister(address)

    protected fun <T> register(address: Int, count: Int, format: IOFormat<T>): ModbusRegistryKey.HoldingRange<T> =
        ModbusRegistryKey.HoldingRange(address, count, format)

    protected fun registerByOffset(offset: Int): ModbusRegistryKey.HoldingRegister =
        ModbusRegistryKey.HoldingRegister(30000 + offset)

    protected fun <T> registerByOffset(offset: Int, count: Int, format: IOFormat<T>): ModbusRegistryKey.HoldingRange<T> =
        ModbusRegistryKey.HoldingRange(offset + 30000, count, format)
}
