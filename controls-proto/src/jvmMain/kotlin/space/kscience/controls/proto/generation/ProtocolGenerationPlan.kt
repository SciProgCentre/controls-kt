package space.kscience.controls.proto.generation

import space.kscience.controls.spec.DeviceSpec
import java.io.File

public data class ProtocolGenerationTarget(
    val language: ProtocolLanguage,
    val outputDirectory: File,
    val options: ProtocolGenerationOptions = ProtocolGenerationOptions(),
    val cleanOutputDirectory: Boolean = false,
) {
    public companion object {
        public fun rust(
            outputDirectory: File,
            moduleName: String = "device",
            delivery: ProtocolDelivery = ProtocolDelivery.SOURCE_FILES,
            packageName: String? = null,
            runtime: RustProtocolRuntime = RustProtocolRuntime.outDirModule(),
            protoSources: List<ProtocolProtoSource> = listOf(ProtocolProtoSources.bundledDataForgeMeta()),
            generateBuildScript: Boolean? = null,
            cleanOutputDirectory: Boolean = false,
        ): ProtocolGenerationTarget = ProtocolGenerationTarget(
            language = ProtocolLanguage.RUST,
            outputDirectory = outputDirectory,
            options = ProtocolGenerationOptions(
                moduleName = moduleName,
                delivery = delivery,
                packageName = packageName,
                backend = RustBackendOptions(
                    runtime = runtime,
                    protoSources = protoSources,
                    generateBuildScript = generateBuildScript,
                ),
            ),
            cleanOutputDirectory = cleanOutputDirectory,
        )

        public fun rustCrate(
            outputDirectory: File,
            moduleName: String = "device",
            crateName: String = "${moduleName}_protocol",
            runtime: RustProtocolRuntime = RustProtocolRuntime.outDirModule(),
            protoSources: List<ProtocolProtoSource> = listOf(ProtocolProtoSources.bundledDataForgeMeta()),
            generateBuildScript: Boolean? = true,
            cleanOutputDirectory: Boolean = true,
        ): ProtocolGenerationTarget = rust(
            outputDirectory = outputDirectory,
            moduleName = moduleName,
            delivery = ProtocolDelivery.LIBRARY_PACKAGE,
            packageName = crateName,
            runtime = runtime,
            protoSources = protoSources,
            generateBuildScript = generateBuildScript,
            cleanOutputDirectory = cleanOutputDirectory,
        )
    }
}

public data class ProtocolGenerationPlan(
    val deviceSpec: DeviceSpec<*>,
    val targets: List<ProtocolGenerationTarget>,
) {
    init {
        require(targets.isNotEmpty()) { "Protocol generation plan must contain at least one target" }
    }

    public fun generate(log: (String) -> Unit = {}): List<GeneratedProtocolPackage> =
        targets.map { target ->
            val generatedPackage = ProtocolGenerators
                .forLanguage(target.language)
                .generate(deviceSpec, target.options)

            if (target.cleanOutputDirectory) {
                target.outputDirectory.cleanGeneratedOutputDirectory()
            }
            generatedPackage.writeTo(target.outputDirectory)

            val deliveryName = target.options.delivery.name.lowercase().replace('_', ' ')
            log("Generated ${target.language.name.lowercase()} $deliveryName into ${target.outputDirectory.path}")
            generatedPackage.files.forEach { generatedFile ->
                log("  ${generatedFile.relativePath}")
            }

            generatedPackage
        }
}

public class ProtocolGenerationPlanBuilder internal constructor() {
    private val targets: MutableList<ProtocolGenerationTarget> = mutableListOf()

    public fun rust(
        outputDirectory: File,
        moduleName: String = "device",
        delivery: ProtocolDelivery = ProtocolDelivery.SOURCE_FILES,
        packageName: String? = null,
        runtime: RustProtocolRuntime = RustProtocolRuntime.outDirModule(),
        protoSources: List<ProtocolProtoSource> = listOf(ProtocolProtoSources.bundledDataForgeMeta()),
        generateBuildScript: Boolean? = null,
        cleanOutputDirectory: Boolean = false,
    ) {
        targets += ProtocolGenerationTarget.rust(
            outputDirectory = outputDirectory,
            moduleName = moduleName,
            delivery = delivery,
            packageName = packageName,
            runtime = runtime,
            protoSources = protoSources,
            generateBuildScript = generateBuildScript,
            cleanOutputDirectory = cleanOutputDirectory,
        )
    }

    public fun rustCrate(
        outputDirectory: File,
        moduleName: String = "device",
        crateName: String = "${moduleName}_protocol",
        runtime: RustProtocolRuntime = RustProtocolRuntime.outDirModule(),
        protoSources: List<ProtocolProtoSource> = listOf(ProtocolProtoSources.bundledDataForgeMeta()),
        generateBuildScript: Boolean? = true,
        cleanOutputDirectory: Boolean = true,
    ) {
        targets += ProtocolGenerationTarget.rustCrate(
            outputDirectory = outputDirectory,
            moduleName = moduleName,
            crateName = crateName,
            runtime = runtime,
            protoSources = protoSources,
            generateBuildScript = generateBuildScript,
            cleanOutputDirectory = cleanOutputDirectory,
        )
    }

    public fun target(target: ProtocolGenerationTarget) {
        targets += target
    }

    internal fun build(deviceSpec: DeviceSpec<*>): ProtocolGenerationPlan = ProtocolGenerationPlan(
        deviceSpec = deviceSpec,
        targets = targets.toList(),
    )
}

public fun protocolGeneration(
    deviceSpec: DeviceSpec<*>,
    init: ProtocolGenerationPlanBuilder.() -> Unit,
): ProtocolGenerationPlan {
    val builder = ProtocolGenerationPlanBuilder()
    builder.init()
    return builder.build(deviceSpec)
}

private fun File.cleanGeneratedOutputDirectory() {
    if (!exists()) return

    val canonicalDirectory = canonicalFile
    val currentDirectory = File(".").canonicalFile
    require(canonicalDirectory != currentDirectory) {
        "Refusing to clean the current working directory as a generated protocol output"
    }
    require(canonicalDirectory.parentFile != null) {
        "Refusing to clean a filesystem root as a generated protocol output"
    }

    deleteRecursively()
}
