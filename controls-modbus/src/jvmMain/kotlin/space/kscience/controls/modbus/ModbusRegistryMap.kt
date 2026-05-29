package space.kscience.controls.modbus

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import space.kscience.dataforge.io.IOFormat


/**
 * Modbus registry key
 */
@Serializable
public sealed interface ModbusRegistryKey<T> {
    public val address: Int
    public val count: Int get() = 1

    /**
     * Read-only boolean value
     */
    @Serializable
    public data class Coil(override val address: Int) : ModbusRegistryKey<Boolean>

    /**
     * Read-write boolean value
     */
    @Serializable
    public data class DiscreteInput(override val address: Int) : ModbusRegistryKey<Boolean>

    @Serializable
    public sealed interface InputRegisterKey<T>: ModbusRegistryKey<T>

    /**
     * Read-only binary value
     */
    @Serializable
    public class InputRegister(override val address: Int) : InputRegisterKey<Short> {
        override fun toString(): String = "InputRegister(address=$address)"
    }

    /**
     * A range of read-only register encoding a single value
     */
    @Serializable
    public class InputRange<T>(
        override val address: Int,
        override val count: Int,
        public val format: IOFormat<T>,
    ) : InputRegisterKey<T> {
        public val endAddress: Int get() = address + count
        override fun toString(): String = "InputRange(count=$count, format=$format)"
    }

    @Serializable
    public sealed interface HoldingRegisterKey<T>: ModbusRegistryKey<T>

    /**
     * A single read-write register
     */
    @Serializable
    public class HoldingRegister(override val address: Int) : HoldingRegisterKey<Short>{
        override fun toString(): String = "HoldingRegister(address=$address)"
    }

    /**
     * A range of read-write registers encoding a single value
     */
    @Serializable
    public class HoldingRange<T>(
        override val address: Int,
        override val count: Int,
        public val format: IOFormat<T>,
    ) : HoldingRegisterKey<T> {
        public val endAddress: Int get() = address + count
        override fun toString(): String = "HoldingRange(count=$count, format=$format)"
    }
}

/**
 * A base class for modbus registers
 */
public abstract class ModbusRegistryMap {

    private val _entries: MutableMap<ModbusRegistryKey<*>, String> = mutableMapOf()

    public val entries: Map<ModbusRegistryKey<*>, String> get() = _entries

    protected fun <T : ModbusRegistryKey<*>> register(key: T, description: String): T {
        _entries[key] = description
        return key
    }

    /**
     * Register a [ModbusRegistryKey.Coil] key and return it
     */
    protected fun coil(address: Int, description: String = ""): ModbusRegistryKey.Coil =
        register(ModbusRegistryKey.Coil(address), description)


    /**
     * Register a [ModbusRegistryKey.DiscreteInput] key and return it
     */
    protected fun discrete(address: Int, description: String = ""): ModbusRegistryKey.DiscreteInput =
        register(ModbusRegistryKey.DiscreteInput(address), description)

    /**
     * Register a [ModbusRegistryKey.InputRegister] key and return it
     */
    protected fun input(address: Int, description: String = ""): ModbusRegistryKey.InputRegister =
        register(ModbusRegistryKey.InputRegister(address), description)

    /**
     * Register a [ModbusRegistryKey.InputRange] key and return it
     */
    protected fun <T> input(
        address: Int,
        count: Int,
        reader: IOFormat<T>,
        description: String = "",
    ): ModbusRegistryKey.InputRange<T> = register(ModbusRegistryKey.InputRange(address, count, reader), description)

    /**
     * Register a [ModbusRegistryKey.HoldingRegister] key and return it
     */
    protected fun register(address: Int, description: String = ""): ModbusRegistryKey.HoldingRegister =
        register(ModbusRegistryKey.HoldingRegister(address), description)

    /**
     * Register a [ModbusRegistryKey.HoldingRange] key and return it
     */
    protected fun <T> register(
        address: Int,
        count: Int,
        format: IOFormat<T>,
        description: String = "",
    ): ModbusRegistryKey.HoldingRange<T> = register(ModbusRegistryKey.HoldingRange(address, count, format), description)

    public companion object {

        /**
         * Validate the register map. Throw an error if the map is invalid
         */
        public fun validate(map: ModbusRegistryMap) {
            var lastCoil: ModbusRegistryKey.Coil? = null
            var lastDiscreteInput: ModbusRegistryKey.DiscreteInput? = null
            var lastInput: ModbusRegistryKey.InputRegisterKey<*>? = null
            var lastRegister: ModbusRegistryKey.HoldingRegisterKey<*>? = null
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

                    is ModbusRegistryKey.InputRegisterKey -> if (lastInput?.let { key.address >= it.address + it.count } != false) {
                        lastInput = key
                    } else {
                        error("Key $lastInput overlaps with key $key")
                    }

                    is ModbusRegistryKey.HoldingRegisterKey -> if (lastRegister?.let { key.address >= it.address + it.count } != false) {
                        lastRegister = key
                    } else {
                        error("Key $lastRegister overlaps with key $key")
                    }
                }
            }
        }

    }
}

private val ModbusRegistryKey<*>.sectionNumber
    get() = when (this) {
        is ModbusRegistryKey.Coil -> 1
        is ModbusRegistryKey.DiscreteInput -> 2
        is ModbusRegistryKey.InputRegister, is ModbusRegistryKey.InputRange<*> -> 3
        is ModbusRegistryKey.HoldingRegister, is ModbusRegistryKey.HoldingRange<*> -> 4
    }

public fun ModbusRegistryMap.print(to: Appendable = System.out) {
    ModbusRegistryMap.validate(this)
    entries.entries
        .sortedWith(
            Comparator.comparingInt<Map.Entry<ModbusRegistryKey<*>, String>> { it.key.sectionNumber }
                .thenComparingInt { it.key.address }
        )
        .forEach { (key, description) ->
            val typeString = when (key) {
                is ModbusRegistryKey.Coil -> "Coil"
                is ModbusRegistryKey.DiscreteInput -> "Discrete"
                is ModbusRegistryKey.HoldingRegisterKey -> "Register"
                is ModbusRegistryKey.InputRegisterKey -> "Input"
            }
            val rangeString = if (key.count == 1) {
                key.address.toString()
            } else {
                "${key.address} - ${key.address + key.count - 1}"
            }
            to.appendLine("${typeString}\t$rangeString\t$description")
        }
}

public fun ModbusRegistryMap.toJson(): JsonArray = buildJsonArray {
    ModbusRegistryMap.validate(this@toJson)
    entries.forEach { (key, description) ->

        val entry = buildJsonObject {
            put(
                "type",
                when (key) {
                    is ModbusRegistryKey.Coil -> "Coil"
                    is ModbusRegistryKey.DiscreteInput -> "Discrete"
                    is ModbusRegistryKey.HoldingRegisterKey -> "Register"
                    is ModbusRegistryKey.InputRegisterKey -> "Input"
                }
            )
            put("address", key.address)
            if (key.count > 1) {
                put("count", key.count)
            }
            put("description", description)
        }

        add(entry)
    }
}