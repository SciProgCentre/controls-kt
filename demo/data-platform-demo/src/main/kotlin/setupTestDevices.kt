package space.kscience.controls.demo

import kotlinx.coroutines.*
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.DeviceGroup
import space.kscience.controls.constructor.MutableValueState
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.opcua.server.OpcUaServer
import space.kscience.controls.opcua.server.endpoint
import space.kscience.controls.opcua.server.serveDevices
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.names.NameToken
import space.kscience.dataforge.names.asName
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds


class TestDevice(context: Context, values: List<ValueState<Double>>) : DeviceConstructor(context) {
    init {
        val metaDescriptor = MetaDescriptor {
            valueType(ValueType.NUMBER)
        }
        values.forEachIndexed { index, value ->
            val descriptor = PropertyDescriptor(
                name = "test[$index]",
                description = "Test property $index",
                metaDescriptor = metaDescriptor
            )

            registerProperty(converter = MetaConverter.double, descriptor = descriptor, state = value)
        }
    }
}


fun DeviceManager.setupTestDevices(
    propertiesPerDevice: Int = 100,
    numberOfOpcDevices: Int = 10,
    numberOfModbusDevices: Int = 2,
    scope: CoroutineScope = context
): Job = scope.launch{
    val values = mutableListOf<MutableValueState<Double>>()

    //create opc device group
    val opcGroup = DeviceGroup(context, Meta.EMPTY)

    //fill opc device group
    repeat(numberOfOpcDevices) {
        val states = List(propertiesPerDevice) { MutableValueState(0.0) }
        values.addAll(states)
        val testDevice = TestDevice(context, states)

        opcGroup.install(NameToken("device", it.toString()).asName(), testDevice)
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


    launch {
        while (isActive) {
            values.forEachIndexed { index, value ->
                value.value = Random.nextDouble()
            }
            delay(1.seconds)
        }
    }



    coroutineContext[Job]?.invokeOnCompletion {
        opcServer.shutdown()
    }

}