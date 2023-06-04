package space.kscience.controls.modbus

import space.kscience.dataforge.io.IOFormat


public sealed class ModbusRegistryKey {
    public abstract val address: Int
    public open val count: Int = 1

    /**
     * Read-only boolean value
     */
    public data class Coil(override val address: Int) : ModbusRegistryKey() {
        init {
            require(address in 1..9999) { "Coil address must be in 1..9999 range" }
        }
    }

    /**
     * Read-write boolean value
     */
    public data class DiscreteInput(override val address: Int) : ModbusRegistryKey() {
        init {
            require(address in 10001..19999) { "DiscreteInput address must be in 10001..19999 range" }
        }
    }

    /**
     * Read-only binary value
     */
    public data class InputRegister(override val address: Int) : ModbusRegistryKey() {
        init {
            require(address in 20001..29999) { "InputRegister address must be in 20001..29999 range" }
        }
    }

    public data class InputRange<T>(
        override val address: Int,
        override val count: Int,
        public val format: IOFormat<T>,
    ) : ModbusRegistryKey() {
        public val endAddress: Int get() = address + count

        init {
            require(address in 20001..29999) { "InputRange begin address is $address, but must be in 20001..29999 range" }
            require(endAddress in 20001..29999) { "InputRange end address is ${endAddress}, but must be in 20001..29999 range" }
        }
    }

    public data class HoldingRegister(override val address: Int) : ModbusRegistryKey() {
        init {
            require(address in 30001..39999) { "HoldingRegister address must be in 30001..39999 range" }
        }
    }

    public data class HoldingRange<T>(
        override val address: Int,
        override val count: Int,
        public val format: IOFormat<T>,
    ) : ModbusRegistryKey() {
        public val endAddress: Int get() = address + count

        init {
            require(address in 30001..39999) { "HoldingRange begin address is $address, but must be in 30001..39999 range" }
            require(endAddress in 30001..39999) { "HoldingRange end address is ${endAddress}, but must be in 30001..39999 range" }
        }
    }
}

public abstract class ModbusRegistryMap {

    private val _entries: MutableMap<ModbusRegistryKey, String> = mutableMapOf<ModbusRegistryKey, String>()

    public val entries: Map<ModbusRegistryKey, String> get() = _entries

    protected fun <T : ModbusRegistryKey> register(key: T, description: String): T {
        _entries[key] = description
        return key
    }

    protected fun coil(address: Int, description: String = ""): ModbusRegistryKey.Coil =
        register(ModbusRegistryKey.Coil(address), description)

    protected fun coilByOffset(offset: Int, description: String = ""): ModbusRegistryKey.Coil =
        register(ModbusRegistryKey.Coil(offset), description)

    protected fun discrete(address: Int, description: String = ""): ModbusRegistryKey.DiscreteInput =
        register(ModbusRegistryKey.DiscreteInput(address), description)

    protected fun discreteByOffset(offset: Int, description: String = ""): ModbusRegistryKey.DiscreteInput =
        register(ModbusRegistryKey.DiscreteInput(10000 + offset), description)

    protected fun input(address: Int, description: String = ""): ModbusRegistryKey.InputRegister =
        register(ModbusRegistryKey.InputRegister(address), description)

    protected fun <T> input(
        address: Int,
        count: Int,
        reader: IOFormat<T>,
        description: String = "",
    ): ModbusRegistryKey.InputRange<T> =
        register(ModbusRegistryKey.InputRange(address, count, reader), description)

    protected fun inputByOffset(offset: Int, description: String = ""): ModbusRegistryKey.InputRegister =
        register(ModbusRegistryKey.InputRegister(20000 + offset), description)

    protected fun <T> inputByOffset(
        offset: Int,
        count: Int,
        reader: IOFormat<T>,
        description: String = "",
    ): ModbusRegistryKey.InputRange<T> =
        register(ModbusRegistryKey.InputRange(20000 + offset, count, reader), description)

    protected fun register(address: Int, description: String = ""): ModbusRegistryKey.HoldingRegister =
        register(ModbusRegistryKey.HoldingRegister(address), description)

    protected fun <T> register(
        address: Int,
        count: Int,
        format: IOFormat<T>,
        description: String = "",
    ): ModbusRegistryKey.HoldingRange<T> =
        register(ModbusRegistryKey.HoldingRange(address, count, format), description)

    protected fun registerByOffset(offset: Int, description: String = ""): ModbusRegistryKey.HoldingRegister =
        register(ModbusRegistryKey.HoldingRegister(30000 + offset), description)

    protected fun <T> registerByOffset(
        offset: Int,
        count: Int,
        format: IOFormat<T>,
        description: String = "",
    ): ModbusRegistryKey.HoldingRange<T> =
        register(ModbusRegistryKey.HoldingRange(offset + 30000, count, format), description)

    public companion object {
        public fun validate(map: ModbusRegistryMap) {
            map.entries.keys.sortedBy { it.address }.zipWithNext().forEach { (l, r) ->
                if (l.address + l.count > r.address) error("Key $l overlaps with key $r")
            }
        }

        public fun print(map: ModbusRegistryMap, to: Appendable = System.out) {
            map.entries.entries.sortedBy { it.key.address }.forEach { (key, description) ->
                val typeString = when (key) {
                    is ModbusRegistryKey.Coil -> "Coil"
                    is ModbusRegistryKey.DiscreteInput -> "Discrete"
                    is ModbusRegistryKey.HoldingRange<*>, is ModbusRegistryKey.HoldingRegister -> "Register"
                    is ModbusRegistryKey.InputRange<*>, is ModbusRegistryKey.InputRegister -> "Input"
                }
                val rangeString = if (key.count == 1) {
                    key.address.toString()
                } else {
                    "${key.address} - ${key.address + key.count}"
                }
                to.appendLine("${typeString}\t$rangeString\t$description")
            }
        }
    }
}


