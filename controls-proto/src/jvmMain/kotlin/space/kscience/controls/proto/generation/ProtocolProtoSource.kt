package space.kscience.controls.proto.generation

import java.io.File
import java.net.URI
import java.security.MessageDigest

public sealed interface ProtocolProtoSource {
    public val relativePath: String

    public fun load(): GeneratedProtocolFile
}

public data class InlineProtocolProtoSource(
    override val relativePath: String,
    val content: String,
) : ProtocolProtoSource {
    override fun load(): GeneratedProtocolFile = GeneratedProtocolFile(relativePath, content)
}

public data class LocalProtocolProtoSource(
    override val relativePath: String,
    val file: File,
) : ProtocolProtoSource {
    override fun load(): GeneratedProtocolFile = GeneratedProtocolFile(
        relativePath = relativePath,
        content = file.readText(),
    )
}

public data class ResourceProtocolProtoSource(
    override val relativePath: String,
    val resourcePath: String,
) : ProtocolProtoSource {
    override fun load(): GeneratedProtocolFile {
        val content = checkNotNull(
            ResourceProtocolProtoSource::class.java.classLoader.getResource(resourcePath),
        ) {
            "Bundled proto resource '$resourcePath' was not found"
        }.readText()

        return GeneratedProtocolFile(relativePath, content)
    }
}

public data class RemotePinnedProtocolProtoSource(
    override val relativePath: String,
    val url: String,
    val sha256: String,
    val cacheDirectory: File = File(System.getProperty("user.home"), ".cache/controls-proto/proto"),
) : ProtocolProtoSource {
    private val expectedSha256: String = sha256.normalizedSha256()

    init {
        require(url.isNotBlank()) { "Remote proto URL must not be blank" }
        require(expectedSha256.matches(Regex("[0-9a-f]{64}"))) {
            "Remote proto SHA-256 must contain 64 hexadecimal characters"
        }
    }

    override fun load(): GeneratedProtocolFile {
        cacheDirectory.mkdirs()
        val cacheFile = cacheDirectory.resolve("${url.sha256Hex().take(16)}-$expectedSha256.proto")

        if (cacheFile.isFile) {
            val cachedContent = cacheFile.readText()
            if (cachedContent.sha256Hex() == expectedSha256) {
                return GeneratedProtocolFile(relativePath, cachedContent)
            }
        }

        val content = try {
            URI(url.toRawContentUrl()).toURL().readText()
        } catch (cause: Exception) {
            error(
                "Could not download proto source '$url'. " +
                    "Use a local proto file or run with network access. Cause: ${cause.message}",
            )
        }

        val actualSha256 = content.sha256Hex()
        require(actualSha256 == expectedSha256) {
            "Downloaded proto source '$url' has SHA-256 $actualSha256, expected $expectedSha256"
        }

        cacheFile.writeText(content)
        return GeneratedProtocolFile(relativePath, content)
    }
}

public object ProtocolProtoSources {
    public const val DATAFORGE_META_RESOURCE: String = "space/kscience/controls/proto/dataforge/meta.proto"

    public fun bundledDataForgeMeta(
        relativePath: String = "proto/meta.proto",
    ): ProtocolProtoSource = ResourceProtocolProtoSource(
        relativePath = relativePath,
        resourcePath = DATAFORGE_META_RESOURCE,
    )

    public fun localFile(
        file: File,
        relativePath: String = "proto/${file.name}",
    ): ProtocolProtoSource = LocalProtocolProtoSource(
        relativePath = relativePath,
        file = file,
    )

    public fun remotePinned(
        url: String,
        sha256: String,
        relativePath: String = "proto/${url.substringAfterLast('/').substringBefore('?')}",
        cacheDirectory: File = File(System.getProperty("user.home"), ".cache/controls-proto/proto"),
    ): ProtocolProtoSource = RemotePinnedProtocolProtoSource(
        relativePath = relativePath,
        url = url,
        sha256 = sha256,
        cacheDirectory = cacheDirectory,
    )

    public fun inline(
        relativePath: String,
        content: String,
    ): ProtocolProtoSource = InlineProtocolProtoSource(
        relativePath = relativePath,
        content = content,
    )
}

private fun String.normalizedSha256(): String = removePrefix("sha256:").lowercase()

private fun String.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun String.toRawContentUrl(): String {
    if (!startsWith("https://github.com/") || !contains("/blob/")) return this

    val path = removePrefix("https://github.com/")
    val parts = path.split('/')
    if (parts.size < 5) return this

    val owner = parts[0]
    val repository = parts[1]
    val branch = parts[3]
    val filePath = parts.drop(4).joinToString(separator = "/")
    return "https://raw.githubusercontent.com/$owner/$repository/$branch/$filePath"
}
