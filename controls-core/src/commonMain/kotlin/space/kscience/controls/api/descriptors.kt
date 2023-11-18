package space.kscience.controls.api

import kotlinx.serialization.Serializable
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
    public var mutable: Boolean = false
)

public fun PropertyDescriptor.metaDescriptor(block: MetaDescriptorBuilder.()->Unit){
    metaDescriptor = MetaDescriptor(block)
}

/**
 * A descriptor for property
 */
@Serializable
public class ActionDescriptor(public val name: String) {
    public var description: String? = null
}

