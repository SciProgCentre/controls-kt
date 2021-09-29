package ru.mipt.npm.controls.opcua.client

import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider
import org.eclipse.milo.opcua.stack.client.security.DefaultClientCertificateValidator
import org.eclipse.milo.opcua.stack.core.security.DefaultTrustListManager
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.info
import space.kscience.dataforge.context.logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

public fun <T:Any> T?.toOptional(): Optional<T> = if(this == null) Optional.empty() else Optional.of(this)


internal fun Context.createMiloClient(
    endpointUrl: String, //"opc.tcp://localhost:12686/milo"
    securityPolicy: SecurityPolicy = SecurityPolicy.Basic256Sha256,
    identityProvider: IdentityProvider = AnonymousProvider(),
    endpointFilter: (EndpointDescription?) -> Boolean = { securityPolicy.uri == it?.securityPolicyUri }
): OpcUaClient {

    val securityTempDir: Path = Paths.get(System.getProperty("java.io.tmpdir"), "client", "security")
    Files.createDirectories(securityTempDir)
    check(Files.exists(securityTempDir)) { "Unable to create security dir: $securityTempDir" }

    val pkiDir: Path = securityTempDir.resolve("pki")
    logger.info { "Milo client security dir: ${securityTempDir.toAbsolutePath()}" }
    logger.info { "Security pki dir: ${pkiDir.toAbsolutePath()}" }

    //val loader: KeyStoreLoader = KeyStoreLoader().load(securityTempDir)
    val trustListManager = DefaultTrustListManager(pkiDir.toFile())
    val certificateValidator = DefaultClientCertificateValidator(trustListManager)

    return OpcUaClient.create(
        endpointUrl,
        { endpoints: List<EndpointDescription?> ->
            endpoints.firstOrNull(endpointFilter).toOptional()
        }
    ) { configBuilder: OpcUaClientConfigBuilder ->
        configBuilder
            .setApplicationName(LocalizedText.english("Controls.kt"))
            .setApplicationUri("urn:ru.mipt:npm:controls:opcua")
//            .setKeyPair(loader.getClientKeyPair())
//            .setCertificate(loader.getClientCertificate())
//            .setCertificateChain(loader.getClientCertificateChain())
            .setCertificateValidator(certificateValidator)
            .setIdentityProvider(identityProvider)
            .setRequestTimeout(uint(5000))
            .build()
    }
//        .apply {
//        addSessionInitializer(DataTypeDictionarySessionInitializer(MetaBsdParser()))
//    }
}