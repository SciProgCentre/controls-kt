package space.kscience.controls.api

import kotlinx.serialization.Serializable
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.descriptors.MetaDescriptorBuilder

//TODO check proper builders

/**
 * A descriptor for a property
 */
@Serializable
public class PropertyDescriptor(
    public val name: String,
    public val description: String? = null,
    public val metaDescriptor: MetaDescriptor = MetaDescriptor(),
    public val readable: Boolean = true,
    public val mutable: Boolean = false,
)

/**
 * A builder for PropertyDescriptor
 */
public class PropertyDescriptorBuilder(public val name: String) {
    public var description: String? = null
    public var metaDescriptor: MetaDescriptor = MetaDescriptor()
    public var readable: Boolean = true
    public var mutable: Boolean = false

    /**
     * Configure the metaDescriptor using a block
     */
    public fun metaDescriptor(block: MetaDescriptorBuilder.() -> Unit) {
        metaDescriptor = MetaDescriptor {
            from(metaDescriptor)
            block()
        }
    }

    /**
     * Build the PropertyDescriptor
     */
    public fun build(): PropertyDescriptor = PropertyDescriptor(
        name = name,
        description = description,
        metaDescriptor = metaDescriptor,
        readable = readable,
        mutable = mutable
    )
}

/**
 * Create a PropertyDescriptor using a builder
 */
public fun propertyDescriptor(name: String, builder: PropertyDescriptorBuilder.() -> Unit = {}): PropertyDescriptor =
    PropertyDescriptorBuilder(name).apply(builder).build()

/**
 * A descriptor for an action
 */
@Serializable
public class ActionDescriptor(
    public val name: String,
    public val description: String? = null,
    public val inputMetaDescriptor: MetaDescriptor = MetaDescriptor(),
    public val outputMetaDescriptor: MetaDescriptor = MetaDescriptor(),
)

/**
 * A builder for ActionDescriptor
 */
public class ActionDescriptorBuilder(public val name: String) {
    public var description: String? = null
    public var inputMetaDescriptor: MetaDescriptor = MetaDescriptor()
    public var outputMetaDescriptor: MetaDescriptor = MetaDescriptor()

    /**
     * Configure the inputMetaDescriptor using a block
     */
    public fun inputMeta(block: MetaDescriptorBuilder.() -> Unit) {
        inputMetaDescriptor = MetaDescriptor {
            from(inputMetaDescriptor)
            block()
        }
    }

    /**
     * Configure the outputMetaDescriptor using a block
     */
    public fun outputMeta(block: MetaDescriptorBuilder.() -> Unit) {
        outputMetaDescriptor = MetaDescriptor {
            from(outputMetaDescriptor)
            block()
        }
    }

    /**
     * Build the ActionDescriptor
     */
    public fun build(): ActionDescriptor = ActionDescriptor(
        name = name,
        description = description,
        inputMetaDescriptor = inputMetaDescriptor,
        outputMetaDescriptor = outputMetaDescriptor
    )
}

/**
 * Create an ActionDescriptor using a builder
 */
public fun actionDescriptor(name: String, builder: ActionDescriptorBuilder.() -> Unit = {}): ActionDescriptor =
    ActionDescriptorBuilder(name).apply(builder).build()
