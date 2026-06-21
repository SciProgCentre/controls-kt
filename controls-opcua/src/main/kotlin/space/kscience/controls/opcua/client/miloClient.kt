package space.kscience.controls.opcua.client

import kotlinx.coroutines.future.await
import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.sdk.client.OpcUaClientConfigBuilder
import org.eclipse.milo.opcua.sdk.client.identity.AnonymousProvider
import org.eclipse.milo.opcua.stack.core.security.DefaultClientCertificateValidator
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateQuarantine
import org.eclipse.milo.opcua.stack.core.security.MemoryTrustListManager
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription
import org.eclipse.milo.opcua.stack.transport.client.tcp.OpcTcpClientTransportConfigBuilder
import space.kscience.controls.opcua.server.fromOpc
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import java.util.*
import kotlin.time.toKotlinInstant

internal fun <T : Any> T?.toOptional(): Optional<T> = Optional.ofNullable(this)


internal fun Context.createOpcUaClient(
    endpointUrl: String, //"opc.tcp://localhost:12686/milo"
    securityPolicy: SecurityPolicy = SecurityPolicy.Basic256Sha256,
    endpointFilter: (EndpointDescription?) -> Boolean = { securityPolicy.uri == it?.securityPolicyUri },
    opcClientConfig: OpcUaClientConfigBuilder.() -> Unit,
): OpcUaClient {

//    val securityTempDir: Path = Paths.get(System.getProperty("java.io.tmpdir"), "client", "security")
//    Files.createDirectories(securityTempDir)
//    check(Files.exists(securityTempDir)) { "Unable to create security dir: $securityTempDir" }
//
//    val pkiDir: Path = securityTempDir.resolve("pki")
//    logger.info { "Milo client security dir: ${securityTempDir.toAbsolutePath()}" }
//    logger.info { "Security pki dir: ${pkiDir.toAbsolutePath()}" }

    //val loader: KeyStoreLoader = KeyStoreLoader().load(securityTempDir)
    val trustListManager = MemoryTrustListManager()
    val certificateQuarantine = MemoryCertificateQuarantine()
    val certificateValidator = DefaultClientCertificateValidator(trustListManager, certificateQuarantine)

    return OpcUaClient.create(
        endpointUrl,
        { endpoints: List<EndpointDescription?> ->
            endpoints.firstOrNull(endpointFilter).toOptional()
        },
        { transportConfigBuilder: OpcTcpClientTransportConfigBuilder ->

        },
    ) { configBuilder: OpcUaClientConfigBuilder ->
        configBuilder
            .setApplicationName(LocalizedText.english("Controls-kt"))
            .setApplicationUri("urn:space.kscience:controls:opcua")
//            .setKeyPair(loader.getClientKeyPair())
//            .setCertificate(loader.getClientCertificate())
//            .setCertificateChain(loader.getClientCertificateChain())
            .setCertificateValidator(certificateValidator)
            .setIdentityProvider(AnonymousProvider())
            .setRequestTimeout(uint(5000))
            .apply(opcClientConfig)
            .build()
    }
//        .apply {
//        addSessionInitializer(DataTypeDictionarySessionInitializer(MetaBsdParser()))
//    }
}


/**
 * Read OPC-UA value as [Meta] with timestamp
 */
public suspend fun OpcUaClient.readMetaWithTime(
    nodeId: NodeId,
    maxAge: Double = 500.0
): ValueWithTime<Meta> {
    val data: DataValue = readValuesAsync(maxAge, TimestampsToReturn.Server, listOf(nodeId)).await().first()
    val time = data.serverTime ?: error("No server time provided")
    val meta: Meta = Meta.fromOpc(data.value.value)

    return ValueWithTime(meta, time.javaInstant.toKotlinInstant())
}

/**
 * Read multiple OPC-UA values as [Meta] in a single request
 */
public suspend fun OpcUaClient.readMultipleMetaWithTime(
    nodeIds: List<NodeId>,
    maxAge: Double = 500.0
): List<ValueWithTime<Meta>> = readValuesAsync(maxAge, TimestampsToReturn.Server, nodeIds)
    .await()
    .map {
        ValueWithTime(
            value = Meta.fromOpc(it.value.value),
            time = (it.serverTime ?: error("No server time provided")).javaInstant.toKotlinInstant()
        )
    }


/**
 * Read OPC-UA value with timestamp
 * @param T the type of property to read. The value is coerced to it.
 */
public suspend inline fun <reified T : Any> OpcUaClient.readOpcWithTime(
    nodeId: NodeId,
    converter: MetaConverter<T>,
    maxAge: Double = 500.0
): ValueWithTime<T> {
    val data: DataValue = readValuesAsync(maxAge, TimestampsToReturn.Server, listOf(nodeId)).await().first()
    val time = data.serverTime ?: error("No server time provided")
    val res: T = data.value.value as? T ?: converter.read(Meta.fromOpc(data.value.value))
    return ValueWithTime(res, time.javaInstant.toKotlinInstant())
}

/**
 * Read and coerce value from OPC-UA
 */
public suspend inline fun <reified T> OpcUaClient.readOpc(
    nodeId: NodeId,
    converter: MetaConverter<T>,
    magAge: Double = 500.0
): T {
    val data: DataValue = readValuesAsync(magAge, TimestampsToReturn.Neither, listOf(nodeId)).await().first()

    val content = data.value.value
    if (content is T) return content
    val meta: Meta = Meta.fromOpc(content)

    return converter.readOrNull(Meta.fromOpc(data.value.value))
        ?: error("Meta $meta could not be converted to ${T::class}")
}

public suspend inline fun <reified T> OpcUaClient.writeOpc(
    nodeId: NodeId,
    converter: MetaConverter<T>,
    value: T
): StatusCode {
    val meta = converter.convert(value)
    return writeValuesAsync(listOf(nodeId), listOf(DataValue(Variant(meta)))).await().first()
}