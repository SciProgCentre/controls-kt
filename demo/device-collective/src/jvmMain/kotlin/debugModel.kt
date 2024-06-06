package space.kscience.controls.demo.map

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import space.kscience.controls.spec.write
import space.kscience.dataforge.context.Context
import space.kscience.kmath.geometry.degrees
import space.kscience.kmath.geometry.radians
import space.kscience.maps.coordinates.Gmc
import space.kscience.maps.coordinates.kilometers
import kotlin.math.PI
import kotlin.random.Random

private val deviceVelocity = 0.1.kilometers

private val center = Gmc.ofDegrees(55.925, 37.514)
private val radius = 0.01.degrees


internal fun generateModel(context: Context): DeviceCollectiveModel {
    val devices: List<RemoteDeviceState> = buildList {
        repeat(100) {
            add(
                RemoteDeviceState(
                    "device[$it]",
                    Gmc(
                        center.latitude + radius * Random.nextDouble(),
                        center.longitude + radius * Random.nextDouble()
                    )
                )
            )
        }
    }

    val model = DeviceCollectiveModel(context, devices)

    return model
}

fun RemoteDevice.moveInCircles(): Job = launch {
    var bearing = Random.nextDouble(-PI, PI).radians
    write(RemoteDevice.velocity, GmcVelocity(bearing, deviceVelocity))
    while (isActive) {
        delay(500)
        bearing += 5.degrees
        write(RemoteDevice.velocity, GmcVelocity(bearing, deviceVelocity))
    }
}