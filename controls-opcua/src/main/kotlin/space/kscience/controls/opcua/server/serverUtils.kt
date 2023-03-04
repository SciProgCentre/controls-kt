package space.kscience.controls.opcua.server

import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfigBuilder
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration

public fun OpcUaServer(block: OpcUaServerConfigBuilder.() -> Unit): OpcUaServer {
//        .setProductUri(DemoServer.PRODUCT_URI)
//        .setApplicationUri("${DemoServer.APPLICATION_URI}:$applicationUuid")
//        .setApplicationName(LocalizedText.english("Eclipse Milo OPC UA Demo Server"))
//        .setBuildInfo(buildInfo())
//        .setTrustListManager(trustListManager)
//        .setCertificateManager(certificateManager)
//        .setCertificateValidator(certificateValidator)
//        .setIdentityValidator(identityValidator)
//        .setEndpoints(endpoints)
//        .setLimits(ServerLimits)

    val config = OpcUaServerConfig.builder().apply(block)

    return OpcUaServer(config.build())
}

public fun OpcUaServerConfigBuilder.endpoint(block: EndpointConfiguration.Builder.() -> Unit) {
    val endpoint = EndpointConfiguration.Builder().apply(block).build()
    setEndpoints(setOf(endpoint))
}
