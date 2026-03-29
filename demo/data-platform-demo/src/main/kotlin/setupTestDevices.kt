package space.kscience.controls.demo

import com.ghgande.j2mod.modbus.slave.ModbusSlaveFactory
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.controls.api.onLifecycleEvent
import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.DeviceGroup
import space.kscience.controls.constructor.MutableValueState
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.dataplatform.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.modbus.ModbusRegistryMap
import space.kscience.controls.modbus.bindProcessImage
import space.kscience.controls.opcua.server.OpcUaServer
import space.kscience.controls.opcua.server.endpoint
import space.kscience.controls.opcua.server.serveDevices
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.io.DoubleIOFormat
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.names.*
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

private class TestDeviceRegistryMap(names: List<Name>) : ModbusRegistryMap() {
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
        val deviceName = NameToken("device", deviceNum.toString()).asName()

        val states = buildMap {
            repeat(propertiesPerDevice) { propertyNum ->
                put(deviceName + "property[$propertyNum]", MutableValueState(0.0))
            }
        }
        properties.putAll(states)
        val testDevice = TestDevice(context, states)

        opcGroup.install(deviceName, testDevice)
    }

    install("opc", opcGroup)

    //start opc server
    val opcServer = OpcUaServer {
        setApplicationName(LocalizedText.english("center.sciprog.controls.demo"))

        endpoint {
            setBindPort(9091)
        }

    }.apply {
        serveDevices(context.request(DeviceManager))
        startup().join()
    }

    //register opc devices
    opcServer.serveDevices(context, opcGroup)

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

        val states = registryMap.keys.entries.associate { (name, _) ->
            name to MutableValueState(0.0)
        }

        properties.putAll(states)
        val testDevice = TestDevice(context, states)

        modbusGroup.install(NameToken("device", deviceNum.toString()).asName(), testDevice)

        val processImage = testDevice.bindProcessImage {
            registryMap.keys.forEach { (name, key) ->
                bind(key, name.last().toStringUnescaped(), MetaConverter.double)
            }
        }

        modbusSlave.addProcessImage(deviceNum + 1, processImage)
    }

    install("modbus", modbusGroup)

    modbusSlave.open()

    modbusGroup.onLifecycleEvent { event ->
        if (event == LifecycleState.STOPPED) {
            modbusSlave.close()
        }
    }

    return modbusGroup
}


fun DeviceManager.setupTestDevices(
    propertiesPerDevice: Int = 4,
    numberOfOpcDevices: Int = 4,
    numberOfModbusDevices: Int = 2,
    scope: CoroutineScope = context
): Job = scope.launch {
    val values = mutableMapOf<Name, MutableValueState<Double>>()


    setupOpcTestDevices(numberOfOpcDevices, propertiesPerDevice, values)

    val registryMap = TestDeviceRegistryMap(
        List(propertiesPerDevice) { NameToken("property", it.toString()).asName() }
    )

    setupModbusDevices(numberOfModbusDevices, registryMap, values)


    launch {
        while (isActive) {
            values.forEach { (name, value) ->
                value.value = Random.nextDouble()
            }
            delay(1.seconds)
        }
    }

    val opcSourceName = "opc".asName()
    val modbusSourceName = "modbus".asName()

    val sources = mapOf(
        opcSourceName to OpcUaConfig("opc.tcp://localhost:9091"),
        modbusSourceName to ModbusTcpConfig("localhost", 9093)
    )

    val timerName = "default".asName()

    val timers = mapOf(timerName to FixedRateTimer(1.seconds))

    val platformProperties = buildMap<Name, PlatformProperty> {
        repeat(numberOfOpcDevices) { opcDeviceNum ->
            repeat(propertiesPerDevice) { propertyNum ->
                put(
                    key = "opc[${opcDeviceNum}].property[${propertyNum}]".parseAsName(),
                    value = OpcPlatformProperty(
                        source = opcSourceName,
                        timer = timerName,
                        nodeId = "ns=2;s=opc/device[$opcDeviceNum]/property[$propertyNum]"
                    )
                )
            }
        }

        repeat(numberOfModbusDevices) { modbusDeviceNum ->

            registryMap.keys.forEach { (name, key) ->
                put(
                    key = "modbus[${modbusDeviceNum}]".parseAsName() + name,
                    value = ModbusPlatformProperty(
                        source = modbusSourceName,
                        timer = timerName,
                        reader = ModbusDoubleReader,
                        address = key.address,
                        unitId = modbusDeviceNum + 1
                    )
                )
            }
        }
    }

    val configuration = DataPlatformConfiguration(
        sources = sources,
        timers = timers,
        properties = platformProperties
    )

    println(Json { prettyPrint = true }.encodeToString(DataPlatformConfiguration.serializer(), configuration))

    val platformDevice = DataPlatformDevice(context, configuration)

    install("platform", platformDevice)

}