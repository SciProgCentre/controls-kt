package space.kscience.controls.modbus

import com.fazecast.jSerialComm.SerialPort
import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import com.ghgande.j2mod.modbus.facade.ModbusSerialMaster
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import com.ghgande.j2mod.modbus.net.AbstractSerialConnection
import com.ghgande.j2mod.modbus.util.SerialParameters
import space.kscience.controls.spec.DeviceBase
import space.kscience.controls.spec.DeviceFactory
import space.kscience.dataforge.meta.*

public class ModbusTCPConfiguration : Scheme() {
    init {
        meta["type"] = ModbusDeviceFactory.TYPE_TCP
    }

    public val address: String by string { error("Address is not defined") }

    public val port: Int by int(502)

    public val reconnect: Boolean by boolean(true)

    public val timeout: Int by int(3000)

    public companion object : SchemeSpec<ModbusTCPConfiguration>(::ModbusTCPConfiguration)
}

public class ModbusRTUConfiguration : Scheme() {

    init {
        meta["type"] = ModbusDeviceFactory.TYPE_RTU
    }

    public var portName: String by string { error("Port name is not defined") }
    public var baudRate: Int by int(9600)
    public var flowControl: Int by int(SerialPort.FLOW_CONTROL_DISABLED)
    public var dataBits: Int by int(8)
    public var stopBits: Int by int(AbstractSerialConnection.ONE_STOP_BIT)
    public var parity: Int by int(AbstractSerialConnection.NO_PARITY)

    public var timeout: Int by int(3000)

    public companion object : SchemeSpec<ModbusRTUConfiguration>(::ModbusRTUConfiguration)
}


public abstract class ModbusDeviceFactory : DeviceFactory<AbstractModbusMaster>() {

    override suspend fun DeviceBase.createState(): AbstractModbusMaster = when (meta["type"].string) {
        TYPE_TCP -> {
            val configuration = ModbusTCPConfiguration.read(meta)
            ModbusTCPMaster(
                /* addr = */ configuration.address,
                /* port = */ configuration.port,
                /* timeout = */ configuration.timeout,
                /* reconnect = */ configuration.reconnect
            )
        }

        TYPE_RTU -> {
            val configuration = ModbusRTUConfiguration.read(meta)
            ModbusSerialMaster(
                SerialParameters().apply {
                    baudRate = configuration.baudRate
                    flowControlIn = configuration.flowControl
                    flowControlOut = configuration.flowControl
                    databits = configuration.dataBits
                    stopbits = configuration.stopBits
                    parity = configuration.parity
                },
                configuration.timeout
            )
        }

        else -> error("Unknown modbus type ${meta["type"]}")
    }.also {
        it.connect()
    }

    override suspend fun DeviceBase.destroyState(state: AbstractModbusMaster) {
        state.disconnect()
    }


    public companion object {
        public const val TYPE_TCP: String = "modbus.tcp"
        public const val TYPE_RTU: String = "modbus.rtu"
    }
}