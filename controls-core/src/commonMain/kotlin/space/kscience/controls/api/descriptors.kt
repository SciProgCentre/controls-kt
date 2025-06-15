package space.kscience.controls.api

import kotlinx.serialization.Serializable
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.descriptors.MetaDescriptorBuilder

//TODO add proper builders

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
)

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
)
