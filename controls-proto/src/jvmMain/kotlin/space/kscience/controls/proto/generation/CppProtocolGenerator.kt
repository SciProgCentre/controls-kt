package space.kscience.controls.proto.generation

import space.kscience.controls.spec.DeviceSpec

public data class CppBackendOptions(
    val protoSources: List<ProtocolProtoSource> = listOf(ProtocolProtoSources.bundledDataForgeMeta()),
    val nanopbTextMaxLength: Int = 63,
    val nanopbCppCmakeSupport: Boolean = true,
) : ProtocolBackendOptions {
    init {
        require(nanopbTextMaxLength > 0) { "nanopb text max length must be positive" }
    }
}

public object CppProtocolGenerator : ProtocolGenerator {
    override val language: ProtocolLanguage get() = ProtocolLanguage.CPP

    private data class CppContext(
        val moduleName: String,
        val namespaceName: String,
        val cSourceName: String,
        val headerName: String,
        val sourceName: String,
        val properties: List<CppPropertyInfo>,
        val nanopbCppCmakeSupport: Boolean,
    ) {
        val readableProperties: List<CppPropertyInfo> get() = properties.filter { it.readable }
        val writableProperties: List<CppPropertyInfo> get() = properties.filter { it.writable }
        val upperName: String get() = moduleName.uppercase()
    }

    private data class CppPropertyInfo(
        val name: String,
        val cName: String,
        val enumSuffix: String,
        val cppName: String,
        val type: ProtocolPropertyType,
        val readable: Boolean,
        val writable: Boolean,
        val rootAliasName: String?,
    ) {
        val getRequestVariant: String get() = "Get$cppName"
        val setRequestVariant: String get() = "Set$cppName"
        val responseVariant: String get() = cppName
        val unionFieldName: String get() = cName
    }

    override fun generate(
        deviceSpec: DeviceSpec<*>,
        options: ProtocolGenerationOptions,
    ): GeneratedProtocolPackage {
        val backendOptions = options.cppBackendOptions()
        val context = deviceSpec.cppGenerationContext(options.moduleName, backendOptions)
        val cPackage = CProtocolGenerator.generate(
            deviceSpec = deviceSpec,
            options = ProtocolGenerationOptions(
                moduleName = options.moduleName,
                delivery = ProtocolDelivery.SOURCE_FILES,
                backend = CBackendOptions(
                    protoSources = backendOptions.protoSources,
                    nanopbTextMaxLength = backendOptions.nanopbTextMaxLength,
                ),
            ),
        )

        val cFiles = cPackage.files.mapNotNull { file ->
            when (file.relativePath) {
                "CMakeLists.txt", "README.md" -> null
                context.headerName -> GeneratedProtocolFile(context.headerName, mergeHeader(context, file.content))
                else -> file
            }
        }

        return GeneratedProtocolPackage(
            language = language,
            files = cFiles + listOf(
                GeneratedProtocolFile(context.sourceName, generateSource(context)),
                GeneratedProtocolFile("CMakeLists.txt", generateCMakeLists(context)),
                GeneratedProtocolFile("README.md", generateReadme(context)),
            ),
        )
    }

    private fun ProtocolGenerationOptions.cppBackendOptions(): CppBackendOptions = when (val backendOptions = backend) {
        is CppBackendOptions -> backendOptions
        DefaultProtocolBackendOptions -> CppBackendOptions()
        else -> error("C++ generator does not accept backend options: ${backendOptions::class.simpleName}")
    }

    private fun DeviceSpec<*>.cppGenerationContext(moduleName: String, options: CppBackendOptions): CppContext {
        val cModuleName = moduleName.toCIdentifier("device")
        val properties = toProtocolSchema().properties.map { property ->
            val cName = property.name.toCIdentifier("property")
            CppPropertyInfo(
                name = property.name,
                cName = cName,
                enumSuffix = cName.uppercase(),
                cppName = property.name.toCppTypeName("Property"),
                type = property.type,
                readable = property.readable,
                writable = property.writable,
                rootAliasName = when (property.type) {
                    is ProtocolPropertyType.Scalar -> null
                    is ProtocolPropertyType.StructuredMeta -> property.type.model.rootNode.name.toCppTypeName("Model")
                },
            )
        }

        return CppContext(
            moduleName = cModuleName,
            namespaceName = cModuleName.toCppNamespaceIdentifier("device"),
            cSourceName = "${cModuleName}_protocol.c",
            headerName = "${cModuleName}_protocol.h",
            sourceName = "${cModuleName}_protocol.cpp",
            properties = properties,
            nanopbCppCmakeSupport = options.nanopbCppCmakeSupport,
        )
    }

    private fun mergeHeader(context: CppContext, cHeader: String): String {
        val cHeaderFooter = "#ifdef __cplusplus\n}\n#endif\n\n#endif\n"
        require(cHeader.endsWith(cHeaderFooter)) {
            "Unexpected C header layout: cannot append generated C++ API"
        }
        return cHeader.removeSuffix(cHeaderFooter) +
            "#ifdef __cplusplus\n}\n#endif\n\n" +
            generateCppHeaderSection(context) +
            "#endif\n"
    }

    private fun generateCppHeaderSection(context: CppContext): String = buildString {
        appendLine("#ifdef __cplusplus")
        appendLine()
        appendLine("#include <cstddef>")
        appendLine("#include <cstdint>")
        appendLine("#include <type_traits>")
        appendLine()
        appendLine("namespace ${context.namespaceName} {")
        appendLine()
        appendTypeAliases(context)
        appendRequestKind(context)
        appendResponseKind(context)
        appendRequestClass(context)
        appendResponseClass(context)
        appendHandleMessageApi(context)
        appendLine("} // namespace ${context.namespaceName}")
        appendLine()
        appendLine("#endif")
        appendLine()
    }

    private fun StringBuilder.appendTypeAliases(context: CppContext) {
        appendLine("using String = ${context.moduleName}_string_t;")
        context.properties.forEach { property ->
            val model = property.type as? ProtocolPropertyType.StructuredMeta ?: return@forEach
            model.model.rootNode.structuredPaths(listOf(model.model.rootNode.name)).forEach { path ->
                appendLine("using ${path.toCppAliasName()} = ${context.moduleName}_${path.toCPathIdentifier()}_t;")
            }
        }
        appendLine()
    }

    private fun StringBuilder.appendRequestKind(context: CppContext) {
        appendLine("enum class RequestKind {")
        context.readableProperties.forEach {
            appendLine("    ${it.getRequestVariant} = ${context.upperName}_REQUEST_GET_${it.enumSuffix},")
        }
        context.writableProperties.forEach {
            appendLine("    ${it.setRequestVariant} = ${context.upperName}_REQUEST_SET_${it.enumSuffix},")
        }
        appendLine("};")
        appendLine()
    }

    private fun StringBuilder.appendResponseKind(context: CppContext) {
        appendLine("enum class ResponseKind {")
        appendLine("    None = ${context.upperName}_RESPONSE_NONE,")
        context.properties.forEach {
            appendLine("    ${it.responseVariant} = ${context.upperName}_RESPONSE_${it.enumSuffix},")
        }
        appendLine("};")
        appendLine()
    }

    private fun StringBuilder.appendRequestClass(context: CppContext) {
        appendLine("class Request {")
        appendLine("public:")
        appendLine("    explicit Request(const ${context.moduleName}_request_t &raw) noexcept;")
        appendLine()
        appendLine("    RequestKind kind() const noexcept;")
        appendLine("    const ${context.moduleName}_request_t &raw() const noexcept;")
        context.writableProperties.forEach { property ->
            appendLine("    ${property.cppValueType()} ${property.cName}() const noexcept;")
        }
        appendLine()
        appendLine("private:")
        appendLine("    ${context.moduleName}_request_t raw_;")
        appendLine("};")
        appendLine()
    }

    private fun StringBuilder.appendResponseClass(context: CppContext) {
        appendLine("class Response {")
        appendLine("public:")
        appendLine("    static Response none() noexcept;")
        context.properties.forEach { property ->
            appendLine("    static Response ${property.cName}(${property.cppParameterType()} value) noexcept;")
        }
        appendLine()
        appendLine("    ResponseKind kind() const noexcept;")
        appendLine("    const ${context.moduleName}_response_t &raw() const noexcept;")
        appendLine()
        appendLine("private:")
        appendLine("    explicit Response(${context.moduleName}_response_t raw) noexcept;")
        appendLine()
        appendLine("    ${context.moduleName}_response_t raw_;")
        appendLine("};")
        appendLine()
    }

    private fun StringBuilder.appendHandleMessageApi(context: CppContext) {
        appendLine("using RequestHandler = Response (*)(const Request &request, void *user_data);")
        appendLine()
        appendLine("int handle_message(")
        appendLine("    const std::uint8_t *input,")
        appendLine("    std::size_t input_size,")
        appendLine("    std::uint8_t *output,")
        appendLine("    std::size_t output_capacity,")
        appendLine("    RequestHandler handler,")
        appendLine("    void *user_data = nullptr")
        appendLine(") noexcept;")
        appendLine()
        appendLine("namespace detail {")
        appendLine("template <typename Handler>")
        appendLine("Response call_handler(const Request &request, void *user_data) {")
        appendLine("    return (*static_cast<Handler *>(user_data))(request);")
        appendLine("}")
        appendLine("} // namespace detail")
        appendLine()
        appendLine("template <typename Handler>")
        appendLine("int handle_message(")
        appendLine("    const std::uint8_t *input,")
        appendLine("    std::size_t input_size,")
        appendLine("    std::uint8_t *output,")
        appendLine("    std::size_t output_capacity,")
        appendLine("    Handler &&handler")
        appendLine(") {")
        appendLine("    using HandlerStorage = typename std::decay<Handler>::type;")
        appendLine("    HandlerStorage handler_storage = static_cast<Handler &&>(handler);")
        appendLine("    return handle_message(")
        appendLine("        input,")
        appendLine("        input_size,")
        appendLine("        output,")
        appendLine("        output_capacity,")
        appendLine("        &detail::call_handler<HandlerStorage>,")
        appendLine("        &handler_storage")
        appendLine("    );")
        appendLine("}")
        appendLine()
    }

    private fun generateSource(context: CppContext): String = buildString {
        appendLine("/* Generated by controls-proto. Do not edit by hand. */")
        appendLine("#include \"${context.headerName}\"")
        appendLine()
        appendLine("namespace ${context.namespaceName} {")
        appendLine()
        appendRequestImplementation(context)
        appendResponseImplementation(context)
        appendHandleMessageImplementation(context)
        appendLine("} // namespace ${context.namespaceName}")
    }

    private fun StringBuilder.appendRequestImplementation(context: CppContext) {
        appendLine("Request::Request(const ${context.moduleName}_request_t &raw) noexcept : raw_(raw) {}")
        appendLine()
        appendLine("RequestKind Request::kind() const noexcept {")
        appendLine("    return static_cast<RequestKind>(raw_.kind);")
        appendLine("}")
        appendLine()
        appendLine("const ${context.moduleName}_request_t &Request::raw() const noexcept {")
        appendLine("    return raw_;")
        appendLine("}")
        appendLine()
        context.writableProperties.forEach { property ->
            appendLine("${property.cppValueType()} Request::${property.cName}() const noexcept {")
            appendLine("    return raw_.value.${property.unionFieldName};")
            appendLine("}")
            appendLine()
        }
    }

    private fun StringBuilder.appendResponseImplementation(context: CppContext) {
        appendLine("Response::Response(${context.moduleName}_response_t raw) noexcept : raw_(raw) {}")
        appendLine()
        appendLine("Response Response::none() noexcept {")
        appendLine("    return Response(${context.moduleName}_no_response());")
        appendLine("}")
        appendLine()
        context.properties.forEach { property ->
            appendLine("Response Response::${property.cName}(${property.cppParameterType()} value) noexcept {")
            appendLine("    return Response(${context.moduleName}_response_${property.cName}(value));")
            appendLine("}")
            appendLine()
        }
        appendLine("ResponseKind Response::kind() const noexcept {")
        appendLine("    return static_cast<ResponseKind>(raw_.kind);")
        appendLine("}")
        appendLine()
        appendLine("const ${context.moduleName}_response_t &Response::raw() const noexcept {")
        appendLine("    return raw_;")
        appendLine("}")
        appendLine()
    }

    private fun StringBuilder.appendHandleMessageImplementation(context: CppContext) {
        appendLine("namespace {")
        appendLine("struct HandlerContext {")
        appendLine("    RequestHandler handler;")
        appendLine("    void *user_data;")
        appendLine("};")
        appendLine()
        appendLine("${context.moduleName}_response_t invoke_request_handler(")
        appendLine("    const ${context.moduleName}_request_t *request,")
        appendLine("    void *user_data")
        appendLine(") {")
        appendLine("    HandlerContext *context = static_cast<HandlerContext *>(user_data);")
        appendLine("    return context->handler(Request(*request), context->user_data).raw();")
        appendLine("}")
        appendLine("} // namespace")
        appendLine()
        appendLine("int handle_message(")
        appendLine("    const std::uint8_t *input,")
        appendLine("    std::size_t input_size,")
        appendLine("    std::uint8_t *output,")
        appendLine("    std::size_t output_capacity,")
        appendLine("    RequestHandler handler,")
        appendLine("    void *user_data")
        appendLine(") noexcept {")
        appendLine("    if (handler == nullptr) return ${context.upperName}_PROTOCOL_ERROR_DECODE_REQUEST;")
        appendLine("    HandlerContext context = { handler, user_data };")
        appendLine("    return ${context.moduleName}_handle_message(")
        appendLine("        input,")
        appendLine("        input_size,")
        appendLine("        output,")
        appendLine("        output_capacity,")
        appendLine("        invoke_request_handler,")
        appendLine("        &context")
        appendLine("    );")
        appendLine("}")
        appendLine()
    }

    private fun generateCMakeLists(context: CppContext): String = buildString {
        appendLine("cmake_minimum_required(VERSION 3.16)")
        appendLine()
        appendLine("project(${context.moduleName}_protocol C CXX)")
        appendLine()
        appendLine("set(${context.upperName}_PROTOCOL_CPM_VERSION \"0.40.8\" CACHE STRING \"CPM.cmake version\")")
        appendLine("set(${context.upperName}_PROTOCOL_NANOPB_GIT_TAG \"master\" CACHE STRING \"nanopb git tag or branch\")")
        appendLine("set(${context.upperName}_PROTOCOL_NANOPB_CPP_GIT_TAG \"master\" CACHE STRING \"nanopb_cpp git tag or branch\")")
        appendLine()
        appendLine("set(CPM_DOWNLOAD_LOCATION \"\${CMAKE_CURRENT_BINARY_DIR}/cmake/CPM_\${${context.upperName}_PROTOCOL_CPM_VERSION}.cmake\")")
        appendLine("if(NOT EXISTS \"\${CPM_DOWNLOAD_LOCATION}\")")
        appendLine("    file(DOWNLOAD")
        appendLine("        \"https://github.com/cpm-cmake/CPM.cmake/releases/download/v\${${context.upperName}_PROTOCOL_CPM_VERSION}/CPM.cmake\"")
        appendLine("        \"\${CPM_DOWNLOAD_LOCATION}\"")
        appendLine("        TLS_VERIFY ON")
        appendLine("    )")
        appendLine("endif()")
        appendLine("include(\"\${CPM_DOWNLOAD_LOCATION}\")")
        appendLine()
        appendLine("CPMAddPackage(")
        appendLine("    NAME lib_nanopb")
        appendLine("    GITHUB_REPOSITORY nanopb/nanopb")
        appendLine("    GIT_TAG \${${context.upperName}_PROTOCOL_NANOPB_GIT_TAG}")
        appendLine("    DOWNLOAD_ONLY YES")
        appendLine(")")
        appendLine()
        appendLine("if(CPM_lib_nanopb_SOURCE)")
        appendLine("    set(NANOPB_SRC_ROOT_FOLDER \"\${CPM_lib_nanopb_SOURCE}\" CACHE PATH \"Path to nanopb source checkout\" FORCE)")
        appendLine("elseif(lib_nanopb_SOURCE_DIR)")
        appendLine("    set(NANOPB_SRC_ROOT_FOLDER \"\${lib_nanopb_SOURCE_DIR}\" CACHE PATH \"Path to nanopb source checkout\" FORCE)")
        appendLine("else()")
        appendLine("    message(FATAL_ERROR \"CPM did not provide nanopb source path\")")
        appendLine("endif()")
        appendLine()
        appendLine("set(CPM_lib_nanopb_SOURCE \"\${NANOPB_SRC_ROOT_FOLDER}\" CACHE PATH \"Path to nanopb source checkout\" FORCE)")
        appendLine()
        appendLine("CPMAddPackage(")
        appendLine("    NAME lib_nanopb_cpp")
        appendLine("    GITHUB_REPOSITORY nanopb/nanopb_cpp")
        appendLine("    GIT_TAG \${${context.upperName}_PROTOCOL_NANOPB_CPP_GIT_TAG}")
        appendLine(")")
        appendLine()
        appendLine("list(APPEND CMAKE_MODULE_PATH \"\${NANOPB_SRC_ROOT_FOLDER}/extra\")")
        appendLine("find_package(Nanopb REQUIRED)")
        appendLine()
        appendLine("set(NANOPB_DEPENDS \"\${CMAKE_CURRENT_SOURCE_DIR}/proto/meta.options\")")
        appendLine("NANOPB_GENERATE_CPP(TARGET ${context.moduleName}_meta_proto \"\${CMAKE_CURRENT_SOURCE_DIR}/proto/meta.proto\")")
        appendLine()
        appendLine("add_library(${context.moduleName}_protocol_c STATIC")
        appendLine("    ${context.cSourceName}")
        appendLine(")")
        appendLine()
        appendLine("target_compile_features(${context.moduleName}_protocol_c PUBLIC c_std_99)")
        appendLine("target_compile_options(${context.moduleName}_protocol_c")
        appendLine("    PRIVATE")
        appendLine("        \$<$<C_COMPILER_ID:Clang,GNU>:-Wno-unused-function>")
        appendLine("        \$<$<C_COMPILER_ID:Clang,GNU>:-Wno-unused-variable>")
        appendLine(")")
        appendLine("target_include_directories(${context.moduleName}_protocol_c")
        appendLine("    PUBLIC")
        appendLine("        \"\${CMAKE_CURRENT_SOURCE_DIR}\"")
        appendLine("        \"\${CMAKE_CURRENT_BINARY_DIR}\"")
        appendLine(")")
        appendLine("target_link_libraries(${context.moduleName}_protocol_c")
        appendLine("    PUBLIC")
        appendLine("        ${context.moduleName}_meta_proto")
        appendLine(")")
        appendLine()
        appendLine("add_library(${context.moduleName}_protocol STATIC")
        appendLine("    ${context.sourceName}")
        appendLine(")")
        appendLine()
        appendLine("target_compile_features(${context.moduleName}_protocol PUBLIC cxx_std_11)")
        appendLine("target_include_directories(${context.moduleName}_protocol")
        appendLine("    PUBLIC")
        appendLine("        \"\${CMAKE_CURRENT_SOURCE_DIR}\"")
        appendLine(")")
        appendLine("target_link_libraries(${context.moduleName}_protocol")
        appendLine("    PUBLIC")
        appendLine("        ${context.moduleName}_protocol_c")
        appendLine(")")
        if (context.nanopbCppCmakeSupport) {
            appendLine()
            appendLine("if(TARGET nanopb_cpp)")
            appendLine("    target_compile_definitions(${context.moduleName}_protocol")
            appendLine("        PUBLIC")
            appendLine("            ${context.upperName}_USE_NANOPB_CPP=1")
            appendLine("    )")
            appendLine("    target_link_libraries(${context.moduleName}_protocol")
            appendLine("        PUBLIC")
            appendLine("            nanopb_cpp")
            appendLine("    )")
            appendLine("endif()")
        }
        appendLine()
        appendLine("install(TARGETS ${context.moduleName}_protocol ${context.moduleName}_protocol_c ARCHIVE DESTINATION lib)")
        appendLine("install(FILES")
        appendLine("    \"\${CMAKE_CURRENT_SOURCE_DIR}/${context.headerName}\"")
        appendLine("    \"\${CMAKE_CURRENT_BINARY_DIR}/meta.pb.h\"")
        appendLine("    DESTINATION include")
        appendLine(")")
    }

    private fun generateReadme(context: CppContext): String = """
# ${context.moduleName}_protocol

Generated C++ protocol code for host-to-device messages.

The package contains one public header with a C API and a C++ wrapper API:

- `${context.headerName}`: C API plus C++ API guarded by `#ifdef __cplusplus`.
- `${context.cSourceName}`: generated C/nanopb codec.
- `${context.sourceName}`: C++ wrapper implementation.
- `proto/meta.proto` / `proto/meta.options`: protobuf schema and nanopb options.
- `CMakeLists.txt`: CMake integration.

## Dependencies

The C++ package uses CPM.cmake by default. It downloads `nanopb_cpp`,
and `nanopb_cpp` resolves nanopb through CPM.

```sh
cmake -S . -B build
cmake --build build
```

If you already have a local nanopb checkout, pass it through CPM:

```sh
git clone https://github.com/nanopb/nanopb.git /path/to/nanopb
cmake -S . -B build -DCPM_lib_nanopb_SOURCE=/path/to/nanopb
cmake --build build
```

## User API

```cpp
#include "${context.headerName}"

std::uint8_t response_buffer[256];

int written = ${context.namespaceName}::handle_message(
    request_bytes,
    request_size,
    response_buffer,
    sizeof(response_buffer),
    [](const ${context.namespaceName}::Request &request) {
        switch (request.kind()) {
        case ${context.namespaceName}::RequestKind::GetVoltage:
            return ${context.namespaceName}::Response::voltage(24.5);
        default:
            return ${context.namespaceName}::Response::none();
        }
    }
);
```

Return contract: `> 0` means response bytes written, `0` means no response, `< 0` is an error code.
""".trimIndent() + "\n"

    private fun ProtocolMetaNode.structuredPaths(path: List<String>): List<List<String>> =
        children.filter { it.children.isNotEmpty() }.flatMap { child ->
            child.structuredPaths(path + child.name)
        } + listOf(path)

    private fun List<String>.toCPathIdentifier(): String = joinToString(separator = "_") { it.toCIdentifier("model") }

    private fun List<String>.toCppAliasName(): String = joinToString(separator = "") { it.toCppTypeName("Model") }

    private fun CppPropertyInfo.cppValueType(): String = when (val propertyType = type) {
        is ProtocolPropertyType.Scalar -> propertyType.scalar.cppType()
        is ProtocolPropertyType.StructuredMeta -> "const ${requireNotNull(rootAliasName)} &"
    }

    private fun CppPropertyInfo.cppParameterType(): String = when (val propertyType = type) {
        is ProtocolPropertyType.Scalar -> propertyType.scalar.cppType()
        is ProtocolPropertyType.StructuredMeta -> "const ${requireNotNull(rootAliasName)} &"
    }

    private fun ProtocolScalar.cppType(): String = when (this) {
        ProtocolScalar.INT32 -> "std::int32_t"
        ProtocolScalar.INT64 -> "std::int64_t"
        ProtocolScalar.FLOAT32 -> "float"
        ProtocolScalar.FLOAT64 -> "double"
        ProtocolScalar.BOOLEAN -> "bool"
        ProtocolScalar.STRING -> "String"
    }

    private val cKeywords = setOf(
        "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else",
        "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long", "register",
        "restrict", "return", "short", "signed", "sizeof", "static", "struct", "switch", "typedef",
        "union", "unsigned", "void", "volatile", "while", "_Bool",
    )

    private val cppKeywords = cKeywords + setOf(
        "alignas", "alignof", "and", "and_eq", "asm", "bitand", "bitor", "bool", "catch",
        "char16_t", "char32_t", "class", "compl", "constexpr", "const_cast", "decltype", "delete",
        "dynamic_cast", "explicit", "export", "false", "friend", "mutable", "namespace", "new",
        "noexcept", "not", "not_eq", "nullptr", "operator", "or", "or_eq", "private", "protected",
        "public", "reinterpret_cast", "static_assert", "static_cast", "template", "this",
        "thread_local", "throw", "true", "try", "typeid", "typename", "using", "virtual", "wchar_t",
        "xor", "xor_eq",
    )

    private fun String.toCIdentifier(fallback: String): String {
        val identifier = toSnakeCase()
            .map { char -> if (char.isLetterOrDigit() || char == '_') char else '_' }
            .joinToString(separator = "")
            .trim('_')
            .ifBlank { fallback }
            .let { if (it.first().isDigit()) "_$it" else it }
        return if (identifier in cKeywords) "${identifier}_value" else identifier
    }

    private fun String.toCppNamespaceIdentifier(fallback: String): String {
        val identifier = toCIdentifier(fallback)
        return if (identifier in cppKeywords) "${identifier}_protocol" else identifier
    }

    private fun String.toCppTypeName(fallback: String): String {
        val identifier = identifierWords()
            .joinToString(separator = "") { word -> word.replaceFirstChar { it.uppercaseChar() } }
            .ifBlank { fallback }
            .let { if (it.first().isDigit()) "_$it" else it }
        return if (identifier in cppKeywords) "${identifier}Value" else identifier
    }

    private fun String.toSnakeCase(): String {
        if (isBlank()) return ""
        val builder = StringBuilder()
        forEachIndexed { index, char ->
            when {
                char.isUpperCase() -> {
                    if (index > 0 && builder.lastOrNull() != '_') builder.append('_')
                    builder.append(char.lowercaseChar())
                }
                char.isLetterOrDigit() -> builder.append(char.lowercaseChar())
                builder.lastOrNull() != '_' -> builder.append('_')
            }
        }
        return builder.toString().trim('_')
    }

    private fun String.identifierWords(): List<String> = toSnakeCase()
        .split('_')
        .filter { it.isNotBlank() }
}
