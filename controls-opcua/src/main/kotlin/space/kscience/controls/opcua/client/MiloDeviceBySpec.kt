package space.kscience.controls.opcua.client

import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy
import space.kscience.controls.spec.DeviceBySpec
import space.kscience.controls.spec.DeviceSpec
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Global
import space.kscience.dataforge.meta.*


public sealed class MiloIdentity: Scheme()

public class MiloUsername : MiloIdentity() {

    public var username: String by string{ error("Username not defined") }
    public var password: String by string{ error("Password not defined") }

    public companion object : SchemeSpec<MiloUsername>(::MiloUsername)
}

//public class MiloKeyPair : MiloIdentity() {
//
//    public companion object : SchemeSpec<MiloUsername>(::MiloUsername)
//}

public class MiloConfiguration : Scheme() {

    public var endpointUrl: String by string { error("Endpoint url is not defined") }

    public var username: MiloUsername? by specOrNull(MiloUsername)

    public var securityPolicy: SecurityPolicy by enum(SecurityPolicy.None)

    public companion object : SchemeSpec<MiloConfiguration>(::MiloConfiguration)
}

/**
 * A variant of [DeviceBySpec] that includes OPC-UA client
 */
public open class MiloDeviceBySpec<D : MiloDeviceBySpec<D>>(
    spec: DeviceSpec<D>,
    config: MiloConfiguration,
    context: Context = Global,
) : MiloDevice, DeviceBySpec<D>(spec, context, config.meta) {

    override val client: OpcUaClient by lazy {
        context.createMiloClient(
            config.endpointUrl,
            securityPolicy = config.securityPolicy,
            identityProvider = config.username?.let {
                UsernameProvider(it.username,it.password)
            } ?: AnonymousProvider()
        ).apply {
            connect().get()
        }
    }

    override fun close() {
        client.disconnect()
        super<DeviceBySpec>.close()
    }
}
