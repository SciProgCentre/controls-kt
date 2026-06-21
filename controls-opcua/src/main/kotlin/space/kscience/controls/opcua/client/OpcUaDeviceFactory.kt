package space.kscience.controls.opcua.client

import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.sdk.client.OpcUaClientConfigBuilder
import org.eclipse.milo.opcua.sdk.client.identity.UsernameProvider
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy
import space.kscience.controls.spec.DeviceBase
import space.kscience.controls.spec.DeviceWithStateFactory
import space.kscience.dataforge.meta.*


public sealed class MiloIdentity : Scheme()

public class MiloUsername : MiloIdentity() {

    public var username: String by string { error("Username not defined") }
    public var password: String by string { error("Password not defined") }

    public companion object : SchemeSpec<MiloUsername>(::MiloUsername)
}

//public class MiloKeyPair : MiloIdentity() {
//
//    public companion object : SchemeSpec<MiloUsername>(::MiloUsername)
//}

public class MiloConfiguration : Scheme() {

    public var endpointUrl: String by string { error("Endpoint url is not defined") }

    public var username: MiloUsername? by schemeOrNull(MiloUsername)

    public var securityPolicy: SecurityPolicy by enum(SecurityPolicy.None)

    internal fun configureClient(builder: OpcUaClientConfigBuilder) {
        username?.let {
            builder.setIdentityProvider(UsernameProvider(it.username, it.password))
        }
    }

    public companion object : SchemeSpec<MiloConfiguration>(::MiloConfiguration)
}

public abstract class OpcUaDeviceFactory : DeviceWithStateFactory<OpcUaClient>() {
    context(device: DeviceBase)
    override suspend fun createState(): OpcUaClient {
        val config = MiloConfiguration.read(device.meta)
        return device.context.createOpcUaClient(
            config.endpointUrl,
            securityPolicy = config.securityPolicy,
            opcClientConfig = { config.configureClient(this) }
        ).apply {
            connect()
        }
    }

    context(device: DeviceBase)
    override suspend fun destroyState(state: OpcUaClient) {
        state.disconnect()
    }
}
