package ru.mipt.npm.controls.demo.car

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.mipt.npm.controls.api.DeviceMessage
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.controls.spec.DeviceBySpec
import ru.mipt.npm.controls.spec.doRecurring
import ru.mipt.npm.magix.api.MagixEndpoint
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class MagixVirtualCar(private val magixEndpoint: MagixEndpoint<DeviceMessage>, context: Context, meta: Meta)
    : DeviceBySpec<MagixVirtualCar>(IVirtualCar, context, meta), IVirtualCar {
    override var speedState: Vector2D = Vector2D()
    override var locationState: Vector2D = Vector2D()
    override var accelerationState: Vector2D = Vector2D()

    private suspend fun MagixEndpoint<DeviceMessage>.startMagixVirtualCarUpdate() {
        launch {
            subscribe().collect { magix ->
                (magix.payload as? PropertyChangedMessage)?.let { message ->
                    if (message.sourceDevice == Name.parse("virtual-car")) {
                        when (message.property) {
                            "speed" -> speedState = Vector2D.metaToObject(message.value)
                            "location" -> locationState = Vector2D.metaToObject(message.value)
                            "acceleration" -> accelerationState = Vector2D.metaToObject(message.value)
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun open() {
        super<DeviceBySpec>.open()

        launch {
            magixEndpoint.startMagixVirtualCarUpdate()
        }

        //starting regular updates
        doRecurring(Duration.milliseconds(100)) {
            IVirtualCar.speed.read()
            IVirtualCar.location.read()
            IVirtualCar.acceleration.read()
        }
    }
}

class MagixVirtualCarFactory(private val magixEndpoint: MagixEndpoint<DeviceMessage>) : Factory<MagixVirtualCar> {
    override fun invoke(meta: Meta, context: Context): MagixVirtualCar = MagixVirtualCar(magixEndpoint, context, meta)
}
