package space.kscience.controls.proto.generation

import space.kscience.controls.proto.ProtocolTypeHints
import space.kscience.controls.spec.DeviceSpec
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import java.io.File

public enum class ProtocolLanguage {
    RUST,
    C,
    CPP,
}

public enum class ProtocolDelivery {
    SOURCE_FILES,
    LIBRARY_PACKAGE,
}

public sealed interface ProtocolBackendOptions

public object DefaultProtocolBackendOptions : ProtocolBackendOptions

public data class RustCargoDependency(
    val crateName: String,
    val path: String? = null,
    val version: String? = null,
) {
    init {
        require(crateName.isNotBlank()) { "Rust dependency crate name must not be blank" }
        require(path == null || path.isNotBlank()) { "Rust dependency path must not be blank" }
        require(version == null || version.isNotBlank()) { "Rust dependency version must not be blank" }
        require(path != null || version != null) {
            "Rust dependency '$crateName' must define either path or version"
        }
    }
}

public data class RustOutDirProtoModule(
    val moduleName: String = "proto",
    val generatedFileName: String = "meta.rs",
) {
    init {
        require(moduleName.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) {
            "Rust generated proto module name must be a valid Rust identifier"
        }
        require(generatedFileName.isNotBlank()) { "Generated proto file name must not be blank" }
        require(!generatedFileName.contains('/')) { "Generated proto file name must not contain path separators" }
        require(!generatedFileName.contains('\\')) { "Generated proto file name must not contain path separators" }
    }
}

public data class RustProtocolRuntime(
    val protoModulePath: String,
    val cargoDependency: RustCargoDependency? = null,
    val outDirModule: RustOutDirProtoModule? = null,
) {
    init {
        require(protoModulePath.isNotBlank()) { "Rust proto module path must not be blank" }
    }

    public companion object {
        public fun externalCrate(
            crateName: String,
            cratePath: String? = null,
            version: String? = null,
            protoModulePath: String = "$crateName::space_::kscience_::dataforge_::io_::proto_",
        ): RustProtocolRuntime = RustProtocolRuntime(
            protoModulePath = protoModulePath,
            cargoDependency = RustCargoDependency(
                crateName = crateName,
                path = cratePath,
                version = version,
            ),
        )

        @Deprecated("Use externalCrate(crateName, cratePath, version, protoModulePath) instead.")
        public fun dataForgeCrate(
            crateName: String = "dataforge_proto",
            cratePath: String? = null,
            version: String? = null,
            protoModulePath: String = "$crateName::space_::kscience_::dataforge_::io_::proto_",
        ): RustProtocolRuntime = externalCrate(
            crateName = crateName,
            cratePath = cratePath,
            version = version,
            protoModulePath = protoModulePath,
        )

        public fun localModule(
            protoModulePath: String = "crate::dataforge_proto::space_::kscience_::dataforge_::io_::proto_",
        ): RustProtocolRuntime = RustProtocolRuntime(
            protoModulePath = protoModulePath,
            cargoDependency = null,
        )

        public fun outDirModule(
            moduleName: String = "proto",
            generatedFileName: String = "meta.rs",
            protoModulePath: String = "$moduleName::space_::kscience_::dataforge_::io_::proto_",
        ): RustProtocolRuntime = RustProtocolRuntime(
            protoModulePath = protoModulePath,
            cargoDependency = null,
            outDirModule = RustOutDirProtoModule(
                moduleName = moduleName,
                generatedFileName = generatedFileName,
            ),
        )
    }
}

public data class RustBackendOptions(
    val runtime: RustProtocolRuntime = RustProtocolRuntime.outDirModule(),
    val protoSources: List<ProtocolProtoSource> = listOf(ProtocolProtoSources.bundledDataForgeMeta()),
    val generateBuildScript: Boolean? = null,
) : ProtocolBackendOptions

public data class ProtocolGenerationOptions(
    val moduleName: String = "device",
    val delivery: ProtocolDelivery = ProtocolDelivery.SOURCE_FILES,
    val packageName: String? = null,
    val backend: ProtocolBackendOptions = DefaultProtocolBackendOptions,
) {
    init {
        require(moduleName.isNotBlank()) { "Generated protocol module name must not be blank" }
        require(packageName == null || packageName.isNotBlank()) {
            "Generated protocol package name must not be blank"
        }
    }
}

public data class GeneratedProtocolFile(
    val relativePath: String,
    val content: String,
) {
    init {
        require(relativePath.isNotBlank()) { "Generated file path must not be blank" }
        require(!File(relativePath).isAbsolute) { "Generated file path must be relative: $relativePath" }
        require(relativePath.split('/', '\\').none { it == ".." }) {
            "Generated file path must not escape the output directory: $relativePath"
        }
    }
}

public data class GeneratedProtocolPackage(
    val language: ProtocolLanguage,
    val files: List<GeneratedProtocolFile>,
) {
    public fun file(relativePath: String): GeneratedProtocolFile? = files.firstOrNull { it.relativePath == relativePath }

    public fun writeTo(directory: File) {
        files.forEach { generatedFile ->
            val target = File(directory, generatedFile.relativePath)
            target.parentFile?.mkdirs()
            target.writeText(generatedFile.content)
        }
    }
}

public interface ProtocolGenerator {
    public val language: ProtocolLanguage

    public fun generate(
        deviceSpec: DeviceSpec<*>,
        options: ProtocolGenerationOptions = ProtocolGenerationOptions(),
    ): GeneratedProtocolPackage
}

public object ProtocolGenerators {
    public val rust: ProtocolGenerator get() = RustProtocolGenerator
    public val c: ProtocolGenerator get() = CProtocolGenerator

    public fun forLanguage(language: ProtocolLanguage): ProtocolGenerator = when (language) {
        ProtocolLanguage.RUST -> rust
        ProtocolLanguage.C -> c
        ProtocolLanguage.CPP -> unsupportedGenerator(language)
    }

    private fun unsupportedGenerator(language: ProtocolLanguage): Nothing = error(
        "Protocol generator for $language is not implemented yet. " +
            "The common schema/API is ready for this backend.",
    )
}

internal enum class ProtocolScalar {
    INT32,
    INT64,
    FLOAT32,
    FLOAT64,
    BOOLEAN,
    STRING;

    companion object {
        fun fromTypeHint(typeHint: String?): ProtocolScalar? = when (typeHint?.lowercase()) {
            "int", "i32" -> INT32
            "long", "i64" -> INT64
            "float", "f32" -> FLOAT32
            "double", "f64" -> FLOAT64
            "bool", "boolean" -> BOOLEAN
            "string", "utf8" -> STRING
            else -> null
        }

        fun fromConverter(converter: MetaConverter<*>): ProtocolScalar = when (converter) {
            MetaConverter.double -> FLOAT64
            MetaConverter.string -> STRING
            else -> STRING
        }

        fun fromValueTypes(valueTypes: List<ValueType>): ProtocolScalar = fromValueTypesOrNull(valueTypes) ?: STRING

        fun fromValueTypesOrNull(valueTypes: List<ValueType>?): ProtocolScalar? = when {
            valueTypes == null -> null
            valueTypes.contains(ValueType.BOOLEAN) -> BOOLEAN
            valueTypes.contains(ValueType.STRING) -> STRING
            valueTypes.contains(ValueType.NUMBER) -> FLOAT64
            else -> null
        }
    }
}

internal data class ProtocolSchema(
    val properties: List<ProtocolProperty>,
)

internal data class ProtocolProperty(
    val name: String,
    val type: ProtocolPropertyType,
    val readable: Boolean,
    val writable: Boolean,
)

internal sealed class ProtocolPropertyType {
    data class Scalar(val scalar: ProtocolScalar) : ProtocolPropertyType()
    data class StructuredMeta(val model: ProtocolStructuredModel) : ProtocolPropertyType()
}

internal data class ProtocolStructuredModel(
    val propertyName: String,
    val rootNode: ProtocolMetaNode,
)

internal data class ProtocolMetaNode(
    val name: String,
    val scalar: ProtocolScalar?,
    val children: List<ProtocolMetaNode>,
)

internal fun DeviceSpec<*>.toProtocolSchema(): ProtocolSchema = ProtocolSchema(
    properties = properties.values
        .filter { !it.descriptor.name.startsWith("@") }
        .map { propertySpec ->
            val propertyName = propertySpec.descriptor.name
            val type = propertySpec.toProtocolPropertyType(propertyName)
            ProtocolProperty(
                name = propertyName,
                type = type,
                readable = propertySpec.descriptor.readable,
                writable = propertySpec.descriptor.mutable,
            )
        },
)

private fun DevicePropertySpec<*, *>.toProtocolPropertyType(propertyName: String): ProtocolPropertyType {
    val typeHint = descriptor.metaDescriptor.protocolTypeHint()
    if (typeHint.equals(ProtocolTypeHints.STRUCTURED_META_TYPE, ignoreCase = true)) {
        return ProtocolPropertyType.StructuredMeta(
            model = descriptor.metaDescriptor.toProtocolStructuredModel(propertyName),
        )
    }

    val scalar = ProtocolScalar.fromTypeHint(typeHint)
        ?: ProtocolScalar.fromValueTypesOrNull(descriptor.metaDescriptor.valueTypes)
        ?: ProtocolScalar.fromConverter(converter)
    return ProtocolPropertyType.Scalar(scalar)
}

private fun MetaDescriptor.toProtocolStructuredModel(propertyName: String): ProtocolStructuredModel {
    val rootNode = ProtocolMetaNode(
        name = propertyName,
        scalar = null,
        children = nodes.entries.map { (childName, childDescriptor) ->
            childDescriptor.toProtocolMetaNode(childName)
        },
    )
    return ProtocolStructuredModel(propertyName, rootNode)
}

private fun MetaDescriptor.toProtocolMetaNode(nodeName: String): ProtocolMetaNode {
    val childNodes = nodes.entries.map { (childName, childDescriptor) ->
        childDescriptor.toProtocolMetaNode(childName)
    }
    val scalar = if (childNodes.isEmpty()) {
        resolveProtocolScalar()
    } else {
        null
    }
    return ProtocolMetaNode(
        name = nodeName,
        scalar = scalar,
        children = childNodes,
    )
}

private fun MetaDescriptor.resolveProtocolScalar(): ProtocolScalar {
    val typeHint = protocolTypeHint()
    ProtocolScalar.fromTypeHint(typeHint)?.let { return it }
    return ProtocolScalar.fromValueTypes(valueTypes ?: emptyList())
}

private fun MetaDescriptor.protocolTypeHint(): String? =
    attributes[ProtocolTypeHints.TYPE_ATTRIBUTE].string
        ?: attributes[ProtocolTypeHints.LEGACY_RUST_TYPE_ATTRIBUTE].string
