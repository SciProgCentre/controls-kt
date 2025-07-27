package space.kscience.controls.opcua.client

import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.sdk.client.OpcUaClientConfigBuilder
import org.eclipse.milo.opcua.sdk.client.identity.AnonymousProvider
import org.eclipse.milo.opcua.stack.core.security.DefaultClientCertificateValidator
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateQuarantine
import org.eclipse.milo.opcua.stack.core.security.MemoryTrustListManager
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription
import org.eclipse.milo.opcua.stack.transport.client.tcp.OpcTcpClientTransportConfigBuilder
import space.kscience.dataforge.context.Context
import java.util.*

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