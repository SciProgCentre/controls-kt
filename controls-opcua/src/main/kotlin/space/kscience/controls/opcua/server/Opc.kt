package space.kscience.controls.opcua.server

import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode
import space.kscience.controls.instant
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.long
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.plus
import kotlin.time.Instant

public object Opc {
    public val opcAuxiliaryKey: Name = Name.of("@opc")
    public val opcStatusKey: Name = Name.of("status")
    public val opcSourceTimeKey: Name = Name.of("sourceTime")
    public val opcSourcePicosecondsKey: Name = Name.of("sourcePicoseconds")
    public val opcServerTimeKey: Name = Name.of("serverTime")
    public val opcServerPicosecondsKey: Name = Name.of("serverPicoseconds")

    /**
     * Returns OPC-UA status code of the argument if it is defined
     */
    public fun getOpcStatus(arg: Meta): StatusCode? = arg[opcAuxiliaryKey + opcStatusKey].long?.let { StatusCode(it) }

    /**
     * Returns OPC-UA source time of the argument as an instant if it is defined
     */
    public fun getSourceTime(arg: Meta): Instant? = arg[opcAuxiliaryKey + opcSourceTimeKey].instant

    /**
     * Returns OPC-UA server time of the argument as an instant if it is defined
     */
    public fun getServerTime(arg: Meta): Instant? = arg[opcAuxiliaryKey + opcServerTimeKey].instant
}