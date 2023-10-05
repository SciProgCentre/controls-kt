package space.kscience.controls.modbus

import space.kscience.dataforge.io.IOFormat


public sealed class ModbusRegistryKey {
    public abstract val address: Int
    public open val count: Int = 1


    /**
     * Read-only boolean value
     */
    public data class Coil(override val address: Int) : ModbusRegistryKey()

    /**
     * Read-write boolean value
     */
    public data class DiscreteInput(override val address: Int) : ModbusRegistryKey()

    /**
     * Read-only binary value
     */
    public open class InputRegister(override val address: Int) : ModbusRegistryKey() {
        override fun toString(): String = "InputRegister(address=$address)"
    }

    public class InputRange<T>(
        address: Int,
        override val count: Int,
        public val format: IOFormat<T>,
    ) : InputRegister(address) {
        public val endAddress: Int get() = address + count
        override fun toString(): String = "InputRange(count=$count, format=$format)"


    }

    public open class HoldingRegister(override val address: Int) : ModbusRegistryKey() {
        override fun toString(): String = "HoldingRegister(address=$address)"
    }

    public class HoldingRange<T>(
        address: Int,
        override val count: Int,
        public val format: IOFormat<T>,
    ) : HoldingRegister(address) {
        public val endAddress: Int get() = address + count
        override fun toString(): String = "HoldingRange(count=$count, format=$format)"


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


    protected fun discrete(address: Int, description: String = ""): ModbusRegistryKey.DiscreteInput =
        register(ModbusRegistryKey.DiscreteInput(address), description)

    protected fun input(address: Int, description: String = ""): ModbusRegistryKey.InputRegister =
        register(ModbusRegistryKey.InputRegister(address), description)

    protected fun <T> input(
        address: Int,
        count: Int,
        reader: IOFormat<T>,
        description: String = "",
    ): ModbusRegistryKey.InputRange<T> =
        register(ModbusRegistryKey.InputRange(address, count, reader), description)

    protected fun register(address: Int, description: String = ""): ModbusRegistryKey.HoldingRegister =
        register(ModbusRegistryKey.HoldingRegister(address), description)

    protected fun <T> register(
        address: Int,
        count: Int,
        format: IOFormat<T>,
        description: String = "",
    ): ModbusRegistryKey.HoldingRange<T> =
        register(ModbusRegistryKey.HoldingRange(address, count, format), description)

    public companion object {
        public fun validate(map: ModbusRegistryMap) {
            var lastCoil: ModbusRegistryKey.Coil? = null
            var lastDiscreteInput: ModbusRegistryKey.DiscreteInput? = null
            var lastInput: ModbusRegistryKey.InputRegister? = null
            var lastRegister: ModbusRegistryKey.HoldingRegister? = null
            map.entries.keys.sortedBy { it.address }.forEach { key ->
                when (key) {
                    is ModbusRegistryKey.Coil -> if (lastCoil?.let { key.address >= it.address + it.count } != false) {
                        lastCoil = key
                    } else {
                        error("Key $lastCoil overlaps with key $key")
                    }

                    is ModbusRegistryKey.DiscreteInput -> if (lastDiscreteInput?.let { key.address >= it.address + it.count } != false) {
                        lastDiscreteInput = key
                    } else {
                        error("Key $lastDiscreteInput overlaps with key $key")
                    }

                    is ModbusRegistryKey.InputRegister -> if (lastInput?.let { key.address >= it.address + it.count } != false) {
                        lastInput = key
                    } else {
                        error("Key $lastInput overlaps with key $key")
                    }

                    is ModbusRegistryKey.HoldingRegister -> if (lastRegister?.let { key.address >= it.address + it.count } != false) {
                        lastRegister = key
                    } else {
                        error("Key $lastRegister overlaps with key $key")
                    }
                }
            }
        }

        private val ModbusRegistryKey.sectionNumber
            get() = when (this) {
                is ModbusRegistryKey.Coil -> 1
                is ModbusRegistryKey.DiscreteInput -> 2
                is ModbusRegistryKey.HoldingRegister -> 4
                is ModbusRegistryKey.InputRegister -> 3
            }

        public fun print(map: ModbusRegistryMap, to: Appendable = System.out) {
            validate(map)
            map.entries.entries
                .sortedWith(
                    Comparator.comparingInt<Map.Entry<ModbusRegistryKey, String>?> { it.key.sectionNumber }
                        .thenComparingInt { it.key.address }
                )
                .forEach { (key, description) ->
                    val typeString = when (key) {
                        is ModbusRegistryKey.Coil -> "Coil"
                        is ModbusRegistryKey.DiscreteInput -> "Discrete"
                        is ModbusRegistryKey.HoldingRegister -> "Register"
                        is ModbusRegistryKey.InputRegister -> "Input"
                    }
                    val rangeString = if (key.count == 1) {
                        key.address.toString()
                    } else {
                        "${key.address} - ${key.address + key.count - 1}"
                    }
                    to.appendLine("${typeString}\t$rangeString\t$description")
                }
        }
    }
}


