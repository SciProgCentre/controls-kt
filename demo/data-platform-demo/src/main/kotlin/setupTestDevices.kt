package space.kscience.controls.demo

import com.ghgande.j2mod.modbus.slave.ModbusSlaveFactory
import kotlinx.coroutines.*
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.controls.api.onLifecycleEvent
import space.kscience.controls.constructor.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.installNode
import space.kscience.controls.modbus.ModbusRegistryMap
import space.kscience.controls.modbus.bindProcessImage
import space.kscience.controls.opcua.server.OpcUaServer
import space.kscience.controls.opcua.server.endpoint
import space.kscience.controls.opcua.server.serveDevices
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.io.DoubleIOFormat
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.last
import space.kscience.dataforge.names.parseAsName
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class TestDevice(context: Context, val values: Map<Name, ValueState<Double>>) : DeviceConstructor(context) {
    init {
        val metaDescriptor = MetaDescriptor {
            valueType(ValueType.NUMBER)
        }
        values.forEach { (key, state) ->
            val descriptor = PropertyDescriptor(
                name = key.last().toStringUnescaped(),
                description = "Test property $key",
                metaDescriptor = metaDescriptor
            )

            registerProperty(converter = MetaConverter.double, descriptor = descriptor, state = state)
        }
    }
}

internal class TestDeviceRegistryMap(names: List<String>) : ModbusRegistryMap() {
    val keys = names.mapIndexed { index, name ->
        name to input(address = index * 4, count = 4, reader = DoubleIOFormat)
    }.toMap()
}


private fun DeviceManager.setupOpcTestDevices(
    numberOfOpcDevices: Int,
    propertiesPerDevice: Int,
    properties: MutableMap<Name, MutableValueState<Double>>
): DeviceGroup {

    //create opc device group
    val opcGroup = DeviceGroup(context, Meta.EMPTY)
    //fill opc device group
    repeat(numberOfOpcDevices) { deviceNum ->
        val deviceName = "device[${deviceNum}]"

        val states = buildMap {
            repeat(propertiesPerDevice) { propertyNum ->
                val state = MutableValueState(0.0)
                put("opc.${deviceName}.property[$propertyNum]".parseAsName(false), state)
            }
        }
        properties.putAll(states)
        val testDevice = TestDevice(context, states)

        opcGroup.install(deviceName, testDevice)
    }

    installNode("opc", opcGroup)

    //start opc server
    val opcServer = OpcUaServer {
        setApplicationName(LocalizedText.english("center.sciprog.controls.demo"))

        endpoint {
            setBindPort(9091)
        }

    }.apply {
        serveDevices(context, opcGroup)
        startup().join()
    }

    opcGroup.onLifecycleEvent { event ->
        if (event == LifecycleState.STOPPED) {
            opcServer.shutdown()
        }
    }

    return opcGroup
}

private fun DeviceManager.setupModbusDevices(
    numberOfModbusDevices: Int,
    registryMap: TestDeviceRegistryMap,
    properties: MutableMap<Name, MutableValueState<Double>>
): DeviceGroup {
    //create opc device group
    val modbusGroup = DeviceGroup(context, Meta.EMPTY)

    val modbusSlave = ModbusSlaveFactory.createTCPSlave(9093, 2)

    //fill opc device group
    repeat(numberOfModbusDevices) { deviceNum ->

        val states = registryMap.keys.entries.associate { (keyName, _) ->
            "modbus.device[${deviceNum}].$keyName".parseAsName(false) to MutableValueState(0.0)
        }

        properties.putAll(states)
        val testDevice = TestDevice(context, states)

        modbusGroup.install("device[${deviceNum}]", testDevice)

        val processImage = testDevice.bindProcessImage {
            registryMap.keys.forEach { (name, key) ->
                bind(key, name, MetaConverter.double)
            }
        }

        modbusSlave.addProcessImage(deviceNum + 1, processImage)
    }

    installNode("modbus", modbusGroup)

    modbusSlave.open()

    modbusGroup.onLifecycleEvent { event ->
        if (event == LifecycleState.STOPPED) {
            modbusSlave.close()
        }
    }

    return modbusGroup
}


internal fun DeviceManager.setupTestDevices(
    propertiesPerDevice: Int,
    numberOfOpcDevices: Int,
    numberOfModbusDevices: Int,
    registryMap: TestDeviceRegistryMap,
    scope: CoroutineScope = context
): Job = scope.launch {
    val values = mutableMapOf<Name, MutableValueState<Double>>()


    setupOpcTestDevices(numberOfOpcDevices, propertiesPerDevice, values)

    setupModbusDevices(numberOfModbusDevices, registryMap, values)


    launch {
        while (isActive) {
            values.forEach { (name, value) ->
                value.value += Random.nextDouble(-1.0,1.0)
            }
            delay(1.seconds)
        }
    }
}