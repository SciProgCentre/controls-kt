package ru.mipt.npm.controls.demo.car

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.mipt.npm.controls.api.DeviceMessage
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.rsocket.rSocketWithWebSockets
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.Name
import kotlin.time.ExperimentalTime

class MagixVirtualCar(context: Context, meta: Meta)
    : VirtualCar(context, meta) {

    private suspend fun MagixEndpoint<DeviceMessage>.startMagixVirtualCarUpdate() {
        launch {
            subscribe().collect { magix ->
                (magix.payload as? PropertyChangedMessage)?.let { message ->
                    if (message.sourceDevice == Name.parse("virtual-car")) {
                        when (message.property) {
                            "acceleration" -> IVirtualCar.acceleration.write(Vector2D.metaToObject(message.value))
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun open() {
        super.open()

        val magixEndpoint = MagixEndpoint.rSocketWithWebSockets(
            meta["magixServerHost"].string ?: "localhost",
            DeviceMessage.serializer()
        )

        launch {
            magixEndpoint.startMagixVirtualCarUpdate()
        }
    }

    companion object : Factory<MagixVirtualCar> {
        override fun invoke(meta: Meta, context: Context): MagixVirtualCar = MagixVirtualCar(context, meta)
    }
}
