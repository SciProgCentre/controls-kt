@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package space.kscience.controls.proto.generation

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import space.kscience.controls.api.Device
import space.kscience.controls.spec.DeviceSpec
import space.kscience.controls.proto.mutableStructuredMetaProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class TestQuaternion(
    val w: Double = 1.0,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
)

private object TestQuaternionSerializer : KSerializer<TestQuaternion> {
    override val descriptor = buildClassSerialDescriptor("TestQuaternion") {
        element<Double>("w")
        element<Double>("x")
        element<Double>("y")
        element<Double>("z")
    }

    override fun serialize(encoder: Encoder, value: TestQuaternion) {
        error("Not used in generator tests")
    }

    override fun deserialize(decoder: Decoder): TestQuaternion = error("Not used in generator tests")
}

private data class TestSensor(
    val quaternion: TestQuaternion = TestQuaternion(),
    val healthy: Boolean = true,
)

private object TestSensorSerializer : KSerializer<TestSensor> {
    override val descriptor = buildClassSerialDescriptor("TestSensor") {
        element("quaternion", TestQuaternionSerializer.descriptor)
        element<Boolean>("healthy")
    }

    override fun serialize(encoder: Encoder, value: TestSensor) {
        error("Not used in generator tests")
    }

    override fun deserialize(decoder: Decoder): TestSensor = error("Not used in generator tests")
}

private data class TestPid(
    val p: Double = 1.0,
    val i: Double = 0.1,
    val d: Double = 0.01,
)

private object TestPidSerializer : KSerializer<TestPid> {
    override val descriptor = buildClassSerialDescriptor("TestPid") {
        element<Double>("p")
        element<Double>("i")
        element<Double>("d")
    }

    override fun serialize(encoder: Encoder, value: TestPid) {
        error("Not used in generator tests")
    }

    override fun deserialize(decoder: Decoder): TestPid = error("Not used in generator tests")
}

private data class TestStabilizationProfile(
    val pid: TestPid = TestPid(),
    val sensor: TestSensor = TestSensor(),
)

private object TestStabilizationProfileSerializer : KSerializer<TestStabilizationProfile> {
    override val descriptor = buildClassSerialDescriptor("TestStabilizationProfile") {
        element("pid", TestPidSerializer.descriptor)
        element("sensor", TestSensorSerializer.descriptor)
    }

    override fun serialize(encoder: Encoder, value: TestStabilizationProfile) {
        error("Not used in generator tests")
    }

    override fun deserialize(decoder: Decoder): TestStabilizationProfile = error("Not used in generator tests")
}

private fun emptyDeviceSpec(): DeviceSpec<Device> = object : DeviceSpec<Device>() {}

class GeneratorTest {
    @Test
    fun generateAndSave() {
        val code = RustProtocolGenerator.generateDeviceHandler(emptyDeviceSpec())
        val file = kotlin.io.path.createTempFile(prefix = "meta_decoder_", suffix = ".rs").toFile()
        file.deleteOnExit()
        file.writeText(code)
        println("Saved generated Rust code to ${file.absolutePath}")
    }

    @Test
    fun testRustGeneration() {
        val code = RustProtocolGenerator.generateDeviceHandler(emptyDeviceSpec())

        assertTrue(code.contains("use micropb::{MessageDecode, MessageEncode, PbDecoder, PbEncoder, PbWrite}"), "Should import micropb codecs")
        assertTrue(code.contains("enum ProtocolError"), "Should contain an internal protocol error type")
        assertTrue(!code.contains("pub enum ProtocolError"), "Protocol errors should not be part of the simple public API")
        assertTrue(code.contains("pub mod error_code"), "Should group return codes in a module")
        assertTrue(code.contains("pub const NO_RESPONSE: isize = 0;"), "Should expose a simple no-response code")
        assertTrue(code.contains("pub const DECODE_REQUEST: isize = -1;"), "Should expose numeric protocol error codes")
        assertTrue(code.contains("pub enum HostRequest"), "Should expose typed host requests")
        assertTrue(code.contains("pub enum HostResponse"), "Should expose typed host responses")
        assertTrue(code.contains("fn insert_meta_value"), "Should contain meta insert helper")
        assertTrue(code.contains("struct SliceWriter"), "Should contain a fixed-buffer encoder helper")
        assertTrue(!code.contains("pub struct SliceWriter"), "Fixed-buffer writer should not be public API")
        assertTrue(code.contains("fn write_response_message"), "Should contain a fixed-buffer response writer helper")
        assertTrue(code.contains("fn decode_host_request"), "Should contain the private request decoder")
        assertTrue(!code.contains("pub fn decode_host_request"), "Request decoder should not be public by default")
        assertTrue(code.contains("fn encode_host_response"), "Should contain the private response encoder")
        assertTrue(!code.contains("pub fn encode_host_response"), "Response encoder should not be public by default")
        assertTrue(code.contains("envelope.r#dataBytes = Vec::new();"), "Response data field should stay empty")
        assertTrue(code.contains("fn handle_host_message"), "Should contain the internal callback-based message handler")
        assertTrue(!code.contains("pub fn handle_host_message"), "Host-specific handler alias should not be public API")
        assertTrue(code.contains("fn try_handle_host_message"), "Should contain the internal detailed fallible message handler")
        assertTrue(!code.contains("pub fn try_handle_host_message"), "Fallible handler should stay internal by default")
        assertTrue(code.contains("fn handle_message"), "Should expose the main handler")
        assertTrue(!code.contains("fn try_handle_message"), "Should not generate a second public fallible alias")
        assertTrue(code.contains("OutputBufferTooSmall"), "Protocol errors should describe an undersized output buffer")
        assertTrue(code.contains("Result<usize, ProtocolError>"), "Internal handler should return bytes written or a protocol error")
        assertTrue(code.contains("impl FnMut(HostRequest) -> Option<HostResponse>"), "Callback should internalize user errors and optionally produce a response")
        assertTrue(code.contains("None => return Ok(error_code::NO_RESPONSE as usize),"), "Handler should treat a missing callback response as no reply")
        assertTrue(code.contains("match try_handle_host_message(buffer, output, on_request)"), "Simple handler should delegate to the fallible implementation")
        assertTrue(code.contains("Err(error) => protocol_error_code(error),"), "Public handler should convert internal errors to numeric codes")
        assertTrue(code.contains("pub fn handle_message(buffer: &[u8], output: &mut [u8], on_request: impl FnMut(HostRequest) -> Option<HostResponse>) -> isize"), "Public handler should return response length or a negative error code")
        assertTrue(!code.contains("defmt::"), "Generated protocol library should not hard-wire logging")
        assertTrue(code.contains("let mut envelope: ProtoEnvelope"), "Should decode protobuf envelope")
        assertTrue(code.contains("match method"), "Should dispatch by request method")
        assertTrue(!code.contains("pub trait HostMessageHandler"), "Callback API should not require a generated trait")
        assertTrue(!code.contains("pub struct GeneratedDeviceState"), "Callback API should not force a shared state struct")
        assertTrue(!code.contains("HandleHostMessageError"), "Public API should not expose a generic callback error type")
        assertTrue(!code.contains("/*USER CODE*/"), "Library-style generation should not require manual code injection")
        assertTrue(code.contains("POST request has no known fields to apply") || code.contains("meta.items.get("), "Should include POST meta-based handling path")
        
        println("Generated code snippet:\n${code.take(500)}...")
    }

    @Test
    fun testMetaStructGeneration() {
        val spec = object : DeviceSpec<Device>() {
            val stabilizationProfile by mutableStructuredMetaProperty(
                serializer = TestStabilizationProfileSerializer,
                read = { TestStabilizationProfile() },
                write = { _, _ -> },
            )
        }

        val code = RustProtocolGenerator.generateDeviceHandler(spec)

        assertTrue(code.contains("pub mod stabilization_profile"), "Should group generated meta code into a module")
        assertTrue(!code.contains("pub type StabilizationProfileModel"), "Structured meta root should not get a duplicate public alias")
        assertTrue(code.contains("pub struct Model"), "Should generate root model struct inside the module")
        assertTrue(code.contains("pub struct ModelPid"), "Should generate nested pid struct inside the module")
        assertTrue(code.contains("pub struct ModelSensorQuaternion"), "Should generate deep nested struct inside the module")
        assertTrue(code.contains("#[derive(Debug, Default, Clone)]"), "Generated structs should be cloneable for callback payloads")
        assertTrue(!code.contains("pub struct GeneratedDeviceState"), "Should not generate a device-wide storage wrapper")
        assertTrue(!code.contains("pub trait HostMessageHandler"), "Should not generate a callback trait")
        assertTrue(code.contains("SetStabilizationProfile(stabilization_profile::Model)"), "HostRequest should expose a typed setter for structured meta")
        assertTrue(code.contains("StabilizationProfile(stabilization_profile::Model)"), "HostResponse should expose a typed structured meta response")
        assertTrue(code.contains("fn read_stabilization_profile_meta"), "Should generate meta read wrapper")
        assertTrue(!code.contains("pub fn read_stabilization_profile_meta"), "Meta read wrapper should stay internal")
        assertTrue(code.contains("fn write_stabilization_profile_meta"), "Should generate meta write wrapper")
        assertTrue(!code.contains("pub fn write_stabilization_profile_meta"), "Meta write wrapper should stay internal")
        assertTrue(code.contains("let value = match read_stabilization_profile_meta(meta, \"stabilizationProfile\") {"), "Request decoder should decode structured meta payloads")
        assertTrue(code.contains("(HostRequest::SetStabilizationProfile(_), HostResponse::StabilizationProfile(value)) => {"), "Response encoder should validate structured meta request/response pairs")
        assertTrue(code.contains("insert_meta_node(&mut response_meta, \"stabilizationProfile\", write_stabilization_profile_meta(&value));"), "Response encoder should serialize structured meta values")
        assertTrue(!code.contains("NoResponse"), "No-response flow should use Option<HostResponse> instead of a response enum variant")
        assertTrue(code.indexOf("pub p: f64,") < code.indexOf("pub i: f64,"), "Pid fields should preserve serializer order")
        assertTrue(code.indexOf("pub i: f64,") < code.indexOf("pub d: f64,"), "Pid fields should preserve serializer order")
    }

    @Test
    fun testSplitRustGeneration() {
        val spec = object : DeviceSpec<Device>() {
            val stabilizationProfile by mutableStructuredMetaProperty(
                serializer = TestStabilizationProfileSerializer,
                read = { TestStabilizationProfile() },
                write = { _, _ -> },
            )
        }

        val apiCode = RustProtocolGenerator.generateDeviceApiModule(spec, "device_codec")
        val codecCode = RustProtocolGenerator.generateDeviceCodecModule(spec, "device_support")
        val supportCode = RustProtocolGenerator.generateDeviceSupportModule(spec)

        assertTrue(apiCode.contains("#[path = \"device_codec.rs\"]"), "API file should include the codec file through a path attribute")
        assertTrue(apiCode.contains("pub use device_codec::{"), "API file should re-export codec symbols")
        assertTrue(apiCode.contains("handle_message"), "API file should re-export the main handler")
        assertTrue(apiCode.contains("error_code"), "API file should re-export the error-code module")
        assertTrue(!apiCode.contains("handle_host_message"), "API file should hide the host-specific implementation alias")
        assertTrue(!apiCode.contains("try_handle_host_message"), "API file should hide the internal fallible handler")
        assertTrue(!apiCode.contains("try_handle_message"), "API file should not expose a fallible alias")
        assertTrue(!apiCode.contains("ProtocolError"), "API file should hide internal protocol errors")
        assertTrue(apiCode.contains("stabilization_profile"), "API file should re-export generated structured-meta modules")
        assertTrue(!apiCode.contains("StabilizationProfileModel"), "API file should not re-export duplicate structured-meta aliases")
        assertTrue(!apiCode.contains("decode_host_request"), "API file should hide low-level request decoding")
        assertTrue(!apiCode.contains("encode_host_response"), "API file should hide low-level response encoding")
        assertTrue(!apiCode.contains("use micropb"), "API file should stay free of bulky runtime imports")
        assertTrue(!apiCode.contains("let mut envelope"), "API file should not inline codec implementation details")

        assertTrue(codecCode.contains("#[path = \"device_support.rs\"]"), "Codec file should declare the support file through a path attribute")
        assertTrue(codecCode.contains("mod device_support;"), "Codec file should declare the support module")
        assertTrue(codecCode.contains("pub use device_support::*;"), "Codec file should re-export generated helper types internally")
        assertTrue(codecCode.contains("enum ProtocolError"), "Codec file should define the protocol error type")
        assertTrue(!codecCode.contains("pub enum ProtocolError"), "Codec protocol error should stay internal")
        assertTrue(codecCode.contains("pub mod error_code"), "Codec file should group return codes in a module")
        assertTrue(codecCode.contains("pub const NO_RESPONSE: isize = 0;"), "Codec file should expose no-response code")
        assertTrue(codecCode.contains("pub const DECODE_REQUEST: isize = -1;"), "Codec file should expose numeric error code constants")
        assertTrue(codecCode.contains("pub enum HostRequest"), "Codec file should define the request enum")
        assertTrue(codecCode.contains("pub enum HostResponse"), "Codec file should define the response enum")
        assertTrue(!codecCode.contains("fn insert_meta_value"), "Codec file should not inline support helpers")
        assertTrue(!codecCode.contains("pub mod stabilization_profile"), "Codec file should not inline generated structs")
        assertTrue(codecCode.contains("fn decode_host_request"), "Codec file should contain the request decoder")
        assertTrue(!codecCode.contains("pub fn decode_host_request"), "Codec request decoder should stay private")
        assertTrue(codecCode.contains("fn encode_host_response"), "Codec file should contain the response encoder")
        assertTrue(!codecCode.contains("pub fn encode_host_response"), "Codec response encoder should stay private")
        assertTrue(codecCode.contains("fn handle_host_message"), "Codec file should contain the internal callback handler")
        assertTrue(!codecCode.contains("pub fn handle_host_message"), "Codec host-specific handler alias should stay internal")
        assertTrue(codecCode.contains("fn try_handle_host_message"), "Codec file should contain the internal fallible callback handler")
        assertTrue(!codecCode.contains("pub fn try_handle_host_message"), "Codec fallible callback handler should stay internal")
        assertTrue(codecCode.contains("fn handle_message"), "Codec file should expose the main handler")
        assertTrue(!codecCode.contains("fn try_handle_message"), "Codec file should not expose a fallible compatibility alias")
        assertTrue(codecCode.contains("Result<usize, ProtocolError>"), "Codec file should expose a detailed buffer-writing API")
        assertTrue(codecCode.contains("pub fn handle_message(buffer: &[u8], output: &mut [u8], on_request: impl FnMut(HostRequest) -> Option<HostResponse>) -> isize"), "Codec file should expose a simple C-style handler")
        assertTrue(codecCode.contains("Err(error) => protocol_error_code(error),"), "Codec file should map errors to numeric codes")
        assertTrue(!codecCode.contains("defmt::"), "Codec file should not hard-wire a logger")
        assertTrue(!codecCode.contains("HandleHostMessageError"), "Codec file should not expose a generic callback error type")
        assertTrue(!codecCode.contains("pub trait HostMessageHandler"), "Codec file should not define a trait in the callback API")
        assertTrue(!codecCode.contains("pub struct GeneratedDeviceState"), "Codec file should not define a combined state struct")
        assertTrue(!codecCode.contains("/*USER CODE*/"), "Codec file should no longer require manual edits")

        assertTrue(supportCode.contains("use super::*;"), "Support module should import shared runtime definitions from handler")
        assertTrue(supportCode.contains("fn insert_meta_value"), "Support module should contain helper functions")
        assertTrue(supportCode.contains("pub(super) fn insert_meta_value"), "Support helpers should be visible only to the generated parent module")
        assertTrue(supportCode.contains("struct SliceWriter"), "Support module should contain the fixed-buffer writer")
        assertTrue(!supportCode.contains("pub struct SliceWriter"), "Fixed-buffer writer should stay internal")
        assertTrue(supportCode.contains("fn write_response_message"), "Support module should contain the fixed-buffer response encoder")
        assertTrue(supportCode.contains("pub mod stabilization_profile"), "Support module should contain generated structs")
        assertTrue(!supportCode.contains("pub type StabilizationProfileModel"), "Support module should not expose a duplicate root alias")
    }

    @Test
    fun testProtocolGeneratorPackageApi() {
        val spec = object : DeviceSpec<Device>() {
            val stabilizationProfile by mutableStructuredMetaProperty(
                serializer = TestStabilizationProfileSerializer,
                read = { TestStabilizationProfile() },
                write = { _, _ -> },
            )
        }

        val generator = ProtocolGenerators.forLanguage(ProtocolLanguage.RUST)
        val generatedPackage = generator.generate(spec, ProtocolGenerationOptions(moduleName = "demo_device"))

        assertEquals(ProtocolLanguage.RUST, generatedPackage.language)
        assertEquals(
            listOf("proto/meta.proto", "demo_device.rs", "demo_device_codec.rs", "demo_device_support.rs"),
            generatedPackage.files.map { it.relativePath },
        )
        assertTrue(generatedPackage.file("proto/meta.proto")?.content?.contains("message ProtoMeta") == true)
        assertTrue(generatedPackage.file("proto/meta.proto")?.content?.contains("message ProtoEnvelope") == true)
        assertTrue(generatedPackage.file("demo_device.rs")?.content?.contains("pub use demo_device_codec::{") == true)
        assertTrue(generatedPackage.file("demo_device_codec.rs")?.content?.contains("pub enum HostRequest") == true)
        assertTrue(generatedPackage.file("demo_device_support.rs")?.content?.contains("pub mod stabilization_profile") == true)
    }

    @Test
    fun testCProtocolGeneratorPackageApi() {
        val spec = object : DeviceSpec<Device>() {
            val stabilizationProfile by mutableStructuredMetaProperty(
                serializer = TestStabilizationProfileSerializer,
                read = { TestStabilizationProfile() },
                write = { _, _ -> },
            )
        }

        val generatedPackage = ProtocolGenerators
            .forLanguage(ProtocolLanguage.C)
            .generate(spec, ProtocolGenerationOptions(moduleName = "demo_device"))

        assertEquals(ProtocolLanguage.C, generatedPackage.language)
        assertEquals(
            listOf(
                "proto/meta.proto",
                "proto/meta.options",
                "CMakeLists.txt",
                "demo_device_protocol.h",
                "demo_device_protocol.c",
                "README.md",
            ),
            generatedPackage.files.map { it.relativePath },
        )
        assertTrue(generatedPackage.file("proto/meta.proto")?.content?.contains("message ProtoMeta") == true)
        assertTrue(generatedPackage.file("proto/meta.options")?.content?.contains("*.ProtoMeta.items type:FT_CALLBACK") == true)
        assertTrue(generatedPackage.file("CMakeLists.txt")?.content?.contains("find_package(Nanopb REQUIRED)") == true)
        assertTrue(generatedPackage.file("CMakeLists.txt")?.content?.contains("NANOPB_GENERATE_CPP(TARGET demo_device_meta_proto") == true)
        assertTrue(generatedPackage.file("demo_device_protocol.h")?.content?.contains("demo_device_handle_message") == true)
        assertTrue(generatedPackage.file("demo_device_protocol.h")?.content?.contains("demo_device_stabilization_profile_t") == true)
        assertTrue(generatedPackage.file("demo_device_protocol.c")?.content?.contains("#include \"pb_decode.h\"") == true)
        assertTrue(generatedPackage.file("demo_device_protocol.c")?.content?.contains("pb_decode(&stream, DEMO_DEVICE_PROTO_ENVELOPE_FIELDS") == true)
        assertTrue(generatedPackage.file("demo_device_protocol.c")?.content?.contains("demo_device_stabilization_profile_decode_item") == true)
        assertTrue(generatedPackage.file("README.md")?.content?.contains("This C backend uses nanopb") == true)
    }

    @Test
    fun testRustCrateDeliveryPackageApi() {
        val spec = object : DeviceSpec<Device>() {
            val stabilizationProfile by mutableStructuredMetaProperty(
                serializer = TestStabilizationProfileSerializer,
                read = { TestStabilizationProfile() },
                write = { _, _ -> },
            )
        }

        val generatedPackage = RustProtocolGenerator.generate(
            spec,
            ProtocolGenerationOptions(
                moduleName = "device",
                delivery = ProtocolDelivery.LIBRARY_PACKAGE,
                packageName = "demo_device_protocol",
            ),
        )

        assertEquals(
            listOf(
                "Cargo.toml",
                "build.rs",
                "proto/meta.proto",
                "src/lib.rs",
                "src/device_codec.rs",
                "src/device_support.rs",
                "README.md",
            ),
            generatedPackage.files.map { it.relativePath },
        )
        assertTrue(generatedPackage.file("Cargo.toml")?.content?.contains("name = \"demo_device_protocol\"") == true)
        assertTrue(generatedPackage.file("Cargo.toml")?.content?.contains("micropb = { version = \"*\", features = [\"alloc\"] }") == true)
        assertTrue(generatedPackage.file("Cargo.toml")?.content?.contains("defmt") == false, "Generated protocol crate should not force a logger")
        assertTrue(generatedPackage.file("Cargo.toml")?.content?.contains("dataforge_proto =") == false)
        assertTrue(generatedPackage.file("Cargo.toml")?.content?.contains("build = \"build.rs\"") == true)
        assertTrue(generatedPackage.file("Cargo.toml")?.content?.contains("micropb-gen = \"*\"") == true)
        assertTrue(generatedPackage.file("build.rs")?.content?.contains("compile_protos(PROTO_FILES, out_dir.join(\"meta.rs\"))") == true)
        assertTrue(generatedPackage.file("build.rs")?.content?.contains("\"proto/meta.proto\"") == true)
        assertTrue(generatedPackage.file("proto/meta.proto")?.content?.contains("message ProtoMeta") == true)
        assertTrue(generatedPackage.file("proto/meta.proto")?.content?.contains("message ProtoEnvelope") == true)
        assertTrue(generatedPackage.file("src/lib.rs")?.content?.contains("#![no_std]") == true)
        assertTrue(generatedPackage.file("src/lib.rs")?.content?.contains("#[path = \"device_codec.rs\"]") == true)
        assertTrue(generatedPackage.file("src/lib.rs")?.content?.contains("mod device_codec;") == true)
        assertTrue(generatedPackage.file("src/lib.rs")?.content?.contains("pub mod device;") == false)
        assertTrue(generatedPackage.file("src/lib.rs")?.content?.contains("handle_message") == true)
        assertTrue(generatedPackage.file("src/lib.rs")?.content?.contains("error_code") == true)
        assertTrue(generatedPackage.file("src/lib.rs")?.content?.contains("stabilization_profile") == true)
        assertTrue(generatedPackage.file("src/device.rs") == null)
        assertTrue(generatedPackage.file("src/device_codec.rs")?.content?.contains("mod proto {") == true)
        assertTrue(generatedPackage.file("src/device_codec.rs")?.content?.contains("include!(concat!(env!(\"OUT_DIR\"), \"/meta.rs\"));") == true)
        assertTrue(generatedPackage.file("src/device_codec.rs")?.content?.contains("use proto::space_::kscience_::dataforge_::io_::proto_::{ProtoMeta, ProtoEnvelope}") == true)
        assertTrue(generatedPackage.file("src/device_support.rs")?.content?.contains("pub mod stabilization_profile") == true)
        assertTrue(generatedPackage.file("README.md")?.content?.contains("Generated Rust protocol crate") == true)
        assertTrue(generatedPackage.file("README.md")?.content?.contains("`proto/meta.proto`") == true)
        assertTrue(generatedPackage.file("README.md")?.content?.contains("does not generate or own MCU-specific linker/build behavior") == true)
        assertTrue(generatedPackage.file("README.md")?.content?.contains("This package includes `build.rs`") == true)
        assertTrue(generatedPackage.file("README.md")?.content?.contains("OUT_DIR/meta.rs") == true)
    }

    @Test
    fun testExplicitRustRuntimeCrateDependency() {
        val generatedPackage = RustProtocolGenerator.generate(
            emptyDeviceSpec(),
            ProtocolGenerationOptions(
                moduleName = "device",
                delivery = ProtocolDelivery.LIBRARY_PACKAGE,
                packageName = "demo_device_protocol",
                backend = RustBackendOptions(
                    runtime = RustProtocolRuntime.externalCrate(
                        crateName = "dataforge_proto",
                        cratePath = "../real_dataforge_proto",
                    ),
                ),
            ),
        )

        assertTrue(generatedPackage.file("Cargo.toml")?.content?.contains("dataforge_proto = { path = \"../real_dataforge_proto\" }") == true)
        assertTrue(generatedPackage.file("build.rs") == null)
        assertTrue(generatedPackage.file("src/device_codec.rs")?.content?.contains("use dataforge_proto::space_::kscience_::dataforge_::io_::proto_::{ProtoMeta, ProtoEnvelope}") == true)
    }

    @Test
    fun testCustomProtoSourceCanBeInstalled() {
        val generatedPackage = RustProtocolGenerator.generate(
            emptyDeviceSpec(),
            ProtocolGenerationOptions(
                moduleName = "device",
                backend = RustBackendOptions(
                    runtime = RustProtocolRuntime.localModule(),
                    protoSources = listOf(
                        ProtocolProtoSources.inline(
                            relativePath = "proto/custom.proto",
                            content = "syntax = \"proto3\";\nmessage Custom {}\n",
                        ),
                    ),
                ),
            ),
        )

        assertTrue(generatedPackage.file("proto/custom.proto")?.content?.contains("message Custom") == true)
        assertTrue(generatedPackage.file("proto/meta.proto") == null)
    }

    @Test
    fun testProtocolGenerationPlanWritesToConfiguredPath() {
        val outputDirectory = kotlin.io.path.createTempDirectory(prefix = "protocol_generation_").toFile()
        outputDirectory.deleteOnExit()

        val plan = ProtocolGenerationPlan(
            deviceSpec = emptyDeviceSpec(),
            targets = listOf(
                ProtocolGenerationTarget.rust(
                    outputDirectory = outputDirectory,
                    moduleName = "demo_device",
                ),
            ),
        )

        val generatedPackages = plan.generate()

        assertEquals(1, generatedPackages.size)
        assertTrue(outputDirectory.resolve("proto/meta.proto").isFile)
        assertTrue(outputDirectory.resolve("demo_device.rs").isFile)
        assertTrue(outputDirectory.resolve("demo_device_codec.rs").isFile)
        assertTrue(outputDirectory.resolve("demo_device_support.rs").isFile)
    }

    @Test
    fun testProtocolGenerationPlanWritesRustCrateToConfiguredPath() {
        val outputDirectory = kotlin.io.path.createTempDirectory(prefix = "protocol_crate_generation_").toFile()
        outputDirectory.deleteOnExit()

        val plan = ProtocolGenerationPlan(
            deviceSpec = emptyDeviceSpec(),
            targets = listOf(
                ProtocolGenerationTarget.rustCrate(
                    outputDirectory = outputDirectory,
                    moduleName = "device",
                    crateName = "demo_device_protocol",
                ),
            ),
        )

        val generatedPackages = plan.generate()

        assertEquals(1, generatedPackages.size)
        assertTrue(outputDirectory.resolve("Cargo.toml").isFile)
        assertTrue(outputDirectory.resolve("build.rs").isFile)
        assertTrue(outputDirectory.resolve("proto/meta.proto").isFile)
        assertTrue(outputDirectory.resolve("src/lib.rs").isFile)
        assertTrue(!outputDirectory.resolve("src/device.rs").exists())
        assertTrue(outputDirectory.resolve("src/device_codec.rs").isFile)
        assertTrue(outputDirectory.resolve("src/device_support.rs").isFile)
        assertTrue(outputDirectory.resolve("README.md").isFile)
    }

    @Test
    fun testProtocolGenerationPlanWritesCToConfiguredPath() {
        val outputDirectory = kotlin.io.path.createTempDirectory(prefix = "protocol_c_generation_").toFile()
        outputDirectory.deleteOnExit()

        val plan = ProtocolGenerationPlan(
            deviceSpec = emptyDeviceSpec(),
            targets = listOf(
                ProtocolGenerationTarget.c(
                    outputDirectory = outputDirectory,
                    moduleName = "device",
                ),
            ),
        )

        val generatedPackages = plan.generate()

        assertEquals(1, generatedPackages.size)
        assertTrue(outputDirectory.resolve("proto/meta.proto").isFile)
        assertTrue(outputDirectory.resolve("proto/meta.options").isFile)
        assertTrue(outputDirectory.resolve("CMakeLists.txt").isFile)
        assertTrue(outputDirectory.resolve("device_protocol.h").isFile)
        assertTrue(outputDirectory.resolve("device_protocol.c").isFile)
        assertTrue(outputDirectory.resolve("README.md").isFile)
    }
}
