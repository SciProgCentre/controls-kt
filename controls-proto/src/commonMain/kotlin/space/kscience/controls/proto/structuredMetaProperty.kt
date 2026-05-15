package space.kscience.controls.proto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import space.kscience.controls.api.Device
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.controls.api.metaDescriptor
import space.kscience.controls.spec.DeviceSpec
import space.kscience.controls.spec.MutableDevicePropertySpec
import space.kscience.controls.spec.mutableProperty
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.invoke
import space.kscience.dataforge.meta.toJson
import space.kscience.dataforge.meta.toMeta
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.descriptors.node
import space.kscience.dataforge.misc.DFExperimental
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

private fun leafStructuredMetaDescriptor(valueType: ValueType, protocolType: String? = null): MetaDescriptor = MetaDescriptor {
    valueType(valueType)
    protocolType?.let { resolvedProtocolType ->
        attributes {
            ProtocolTypeHints.TYPE_ATTRIBUTE put resolvedProtocolType
            ProtocolTypeHints.LEGACY_RUST_TYPE_ATTRIBUTE put resolvedProtocolType
        }
    }
}

private fun unsupportedStructuredMetaDescriptor(descriptor: SerialDescriptor): Nothing = error(
    "Structured meta properties support only static serializable objects with primitive or enum leaves. " +
        "Unsupported serial kind '${descriptor.kind}' for '${descriptor.serialName}'.",
)

@OptIn(ExperimentalSerializationApi::class)
private fun SerialDescriptor.toStructuredMetaDescriptor(): MetaDescriptor = when (kind) {
    PrimitiveKind.BOOLEAN -> leafStructuredMetaDescriptor(ValueType.BOOLEAN, "bool")
    PrimitiveKind.BYTE,
    PrimitiveKind.SHORT,
    PrimitiveKind.INT -> leafStructuredMetaDescriptor(ValueType.NUMBER, "i32")
    PrimitiveKind.LONG -> leafStructuredMetaDescriptor(ValueType.NUMBER, "i64")
    PrimitiveKind.FLOAT -> leafStructuredMetaDescriptor(ValueType.NUMBER, "f32")
    PrimitiveKind.DOUBLE -> leafStructuredMetaDescriptor(ValueType.NUMBER, "f64")
    PrimitiveKind.CHAR,
    PrimitiveKind.STRING -> leafStructuredMetaDescriptor(ValueType.STRING, "string")
    SerialKind.ENUM -> leafStructuredMetaDescriptor(ValueType.STRING, "string")
    StructureKind.CLASS,
    StructureKind.OBJECT -> MetaDescriptor {
        repeat(elementsCount) { index ->
            node(getElementName(index), getElementDescriptor(index).toStructuredMetaDescriptor())
        }
    }
    StructureKind.LIST,
    StructureKind.MAP,
    SerialKind.CONTEXTUAL -> unsupportedStructuredMetaDescriptor(this)
    is PolymorphicKind -> unsupportedStructuredMetaDescriptor(this)
}

private fun <T> structuredMetaConverter(
    serializer: KSerializer<T>,
    json: Json = Json,
): MetaConverter<T> {
    val descriptor = serializer.descriptor.toStructuredMetaDescriptor()
    return object : MetaConverter<T> {
        override val descriptor: MetaDescriptor = descriptor

        override fun readOrNull(source: Meta): T = json.decodeFromJsonElement(serializer, source.toJson(descriptor))

        override fun convert(obj: T): Meta = json.encodeToJsonElement(serializer, obj).toMeta(descriptor)
    }
}

/**
 * Register a mutable complex property where conversion to and from Meta is handled internally
 * by a serializable [MetaConverter]. The converter descriptor is derived from the serializer,
 * so downstream code generators can build a static MCU-side structure for the object.
 */
@OptIn(DFExperimental::class)
public fun <T, D : Device> DeviceSpec<D>.mutableStructuredMetaProperty(
    serializer: KSerializer<T>,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> T?,
    write: suspend D.(propertyName: String, value: T) -> Unit,
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, MutableDevicePropertySpec<D, T>>> =
    mutableProperty(
        converter = structuredMetaConverter(serializer),
        descriptorBuilder = {
            metaDescriptor {
                attributes {
                    ProtocolTypeHints.TYPE_ATTRIBUTE put ProtocolTypeHints.STRUCTURED_META_TYPE
                    ProtocolTypeHints.LEGACY_RUST_TYPE_ATTRIBUTE put ProtocolTypeHints.STRUCTURED_META_TYPE
                }
            }
            descriptorBuilder()
        },
        name = name,
        read = read,
        write = write,
    )

@OptIn(DFExperimental::class)
public inline fun <reified T, D : Device> DeviceSpec<D>.mutableStructuredMetaProperty(
    noinline descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    noinline read: suspend D.(propertyName: String) -> T?,
    noinline write: suspend D.(propertyName: String, value: T) -> Unit,
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, MutableDevicePropertySpec<D, T>>> =
    mutableStructuredMetaProperty(
        serializer = serializer(),
        descriptorBuilder = descriptorBuilder,
        name = name,
        read = read,
        write = write,
    )

/**
 * A compact overload that does not require explicit [propertyName] handling in read/write lambdas.
 */
@OptIn(DFExperimental::class)
public fun <T, D : Device> DeviceSpec<D>.mutableStructuredMetaProperty(
    serializer: KSerializer<T>,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend D.() -> T?,
    write: suspend D.(value: T) -> Unit,
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, MutableDevicePropertySpec<D, T>>> =
    mutableStructuredMetaProperty(
        serializer = serializer,
        descriptorBuilder = descriptorBuilder,
        name = name,
        read = { _ -> read() },
        write = { _, value -> write(value) },
    )

@OptIn(DFExperimental::class)
public inline fun <reified T, D : Device> DeviceSpec<D>.mutableStructuredMetaProperty(
    noinline descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    noinline read: suspend D.() -> T?,
    noinline write: suspend D.(value: T) -> Unit,
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, MutableDevicePropertySpec<D, T>>> =
    mutableStructuredMetaProperty(
        serializer = serializer(),
        descriptorBuilder = descriptorBuilder,
        name = name,
        read = read,
        write = write,
    )
