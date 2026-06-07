package space.kscience.controls.api

import kotlinx.serialization.Serializable
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.descriptors.MetaDescriptorBuilder

//TODO add proper builders

/**
 * A common interface for property and action descriptors
 */
public sealed interface DeviceElementDescriptor

/**
 * A descriptor for property
 */
@Serializable
public class PropertyDescriptor(
    public val name: String,
    public var description: String? = null,
    public var metaDescriptor: MetaDescriptor = MetaDescriptor(),
    public var readable: Boolean = true,
    public var mutable: Boolean = false,
) : DeviceElementDescriptor {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PropertyDescriptor

        if (readable != other.readable) return false
        if (mutable != other.mutable) return false
        if (name != other.name) return false
        if (metaDescriptor != other.metaDescriptor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = readable.hashCode()
        result = 31 * result + mutable.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + metaDescriptor.hashCode()
        return result
    }
}

public fun PropertyDescriptor.metaDescriptor(block: MetaDescriptorBuilder.() -> Unit) {
    metaDescriptor = MetaDescriptor {
        from(metaDescriptor)
        block()
    }
}

/**
 * Sets the value type and additional types for a property descriptor.
 *
 * @param valueType The main value type to be assigned to the property descriptor.
 * @param otherTypes Additional value types to be assigned to the property descriptor.
 */
public fun PropertyDescriptor.valueType(valueType: ValueType, vararg otherTypes: ValueType) {
    metaDescriptor {
        valueType(valueType, *otherTypes)
    }
}

/**
 * A descriptor for property
 */
@Serializable
public class ActionDescriptor(
    public val name: String,
    public var description: String? = null,
    public var inputMetaDescriptor: MetaDescriptor = MetaDescriptor(),
    public var outputMetaDescriptor: MetaDescriptor = MetaDescriptor()
) : DeviceElementDescriptor {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ActionDescriptor

        if (name != other.name) return false
        if (inputMetaDescriptor != other.inputMetaDescriptor) return false
        if (outputMetaDescriptor != other.outputMetaDescriptor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + inputMetaDescriptor.hashCode()
        result = 31 * result + outputMetaDescriptor.hashCode()
        return result
    }
}
