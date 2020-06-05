package hep.dataforge.control.controlers

import hep.dataforge.io.Envelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.io.Closeable

@ExperimentalCoroutinesApi
class MessageFlow(
    val controller: MessageController,
    val scope: CoroutineScope
) : Closeable, MessageConsumer {

    init {
        if (controller.messageListener != null) error("Can't attach controller to $controller, the controller is already attached")
        controller.messageListener = this
    }

    private val outputChannel = Channel<Envelope>(CONFLATED)
    private val inputChannel = Channel<Envelope>(CONFLATED)

    val input: SendChannel<Envelope> get() = inputChannel
    val output: Flow<Envelope> = outputChannel.consumeAsFlow()

    init {
        scope.launch {
            while (!inputChannel.isClosedForSend) {
                val request = inputChannel.receive()
                val response = controller.respond(request)
                outputChannel.send(response)
            }
        }
    }

    override fun consume(message: Envelope) {
        scope.launch {
            outputChannel.send(message)
        }
    }

    override fun close() {
        outputChannel.cancel()
    }
}

@ExperimentalCoroutinesApi
fun MessageController.flow(scope: CoroutineScope = device.scope): MessageFlow {
    return MessageFlow(this, scope).also {
        this@flow.messageListener = it
    }
}