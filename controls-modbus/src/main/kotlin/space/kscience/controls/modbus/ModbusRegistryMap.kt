package space.kscience.controls.modbus


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

    public class InputRegister(public val address: Int) : ModbusRegistryKey() {
        init {
            require(address in 20001..29999) { "InputRegister address must be in 20001..29999 range" }
        }
    }

    public class HoldingRegister(public val address: Int) : ModbusRegistryKey() {
        init {
            require(address in 30001..39999) { "HoldingRegister address must be in 30001..39999 range" }
        }
    }
}

public abstract class ModbusRegistryMap {
    protected fun coil(address: Int): ModbusRegistryKey.Coil = ModbusRegistryKey.Coil(address)

    protected fun discrete(address: Int): ModbusRegistryKey.DiscreteInput = ModbusRegistryKey.DiscreteInput(address)

    protected fun input(address: Int): ModbusRegistryKey.InputRegister = ModbusRegistryKey.InputRegister(address)

    protected fun register(address: Int): ModbusRegistryKey.HoldingRegister = ModbusRegistryKey.HoldingRegister(address)
}
