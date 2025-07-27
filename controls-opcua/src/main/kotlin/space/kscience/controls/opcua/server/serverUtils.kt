package space.kscience.controls.opcua.server

import org.eclipse.milo.opcua.sdk.server.EndpointConfig
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig
import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfigBuilder
import org.eclipse.milo.opcua.stack.transport.server.OpcServerTransportFactory
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransport
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransportConfig
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransportConfigBuilder


public fun OpcUaServer(
    transportConfig: OpcTcpServerTransportConfigBuilder.() -> Unit = {},
    serverConfig: OpcUaServerConfigBuilder.() -> Unit
): OpcUaServer {
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

    val config = OpcUaServerConfig.builder().apply(serverConfig).build()
    val transportBuilder: OpcServerTransportFactory = OpcServerTransportFactory { transportProfile ->
        val transportConfig = OpcTcpServerTransportConfig.newBuilder().apply(transportConfig).build()
        OpcTcpServerTransport(transportConfig)
    }

    return OpcUaServer(config, transportBuilder)
}

public fun OpcUaServerConfigBuilder.endpoint(block: EndpointConfig.Builder.() -> Unit) {
    val endpoint = EndpointConfig.Builder().apply(block).build()
    setEndpoints(setOf(endpoint))
}
