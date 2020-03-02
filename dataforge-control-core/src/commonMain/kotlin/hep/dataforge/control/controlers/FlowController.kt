package hep.dataforge.control.controlers

import hep.dataforge.control.api.Device
import hep.dataforge.control.api.PropertyChangeListener
import hep.dataforge.control.controlers.DeviceMessage.Companion.EVENT_STATUS
import hep.dataforge.io.Envelope
import hep.dataforge.io.SimpleEnvelope
import hep.dataforge.meta.MetaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.io.Closeable
import kotlinx.io.EmptyBinary

class FlowController<D : Device>(val device: D, val target: String, val scope: CoroutineScope) : PropertyChangeListener,
    Closeable {

    init {
        if (device.listener != null) error("Can't attach controller to $device, the controller is already attached")
        device.listener = this
    }

    private val outputChannel = Channel<Envelope>(CONFLATED)
    private val inputChannel = Channel<Envelope>(CONFLATED)

    val input: SendChannel<Envelope> get() = inputChannel
    val output = outputChannel.consumeAsFlow()

    init {
        scope.launch {
            while (!inputChannel.isClosedForSend) {
                val request = inputChannel.receive()
                val response = device.respond(target, request)
                outputChannel.send(response)
            }
        }
    }

    override fun propertyChanged(propertyName: String, value: MetaItem<*>) {
        scope.launch {
            val changeMeta = DevicePropertyMessage.ok {
                this.source = target
                status = EVENT_STATUS
                property {
                    name = propertyName
                    this.value = value
                }
            }
            outputChannel.send(SimpleEnvelope(changeMeta, EmptyBinary))
        }
    }

    override fun close() {
        outputChannel.cancel()
    }

}