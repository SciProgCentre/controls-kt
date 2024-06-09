package space.kscience.controls.demo.collective

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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val deviceVelocity = 0.1.kilometers

private val center = Gmc.ofDegrees(55.925, 37.514)
private val radius = 0.01.degrees


internal fun generateModel(
    context: Context,
    size: Int = 50,
    reportInterval: Duration = 500.milliseconds,
    additionalConfiguration: CollectiveDeviceConfiguration.() -> Unit = {},
): DeviceCollectiveModel {
    val devices: List<CollectiveDeviceState> = List(size) { index ->
        val id = "device[$index]"

        VirtualDeviceState(
            id = id,
            Gmc(
                center.latitude + radius * Random.nextDouble(),
                center.longitude + radius * Random.nextDouble()
            )
        ) {
            deviceId = id
            description = "Virtual remote device $id"
            this.reportInterval = reportInterval.inWholeMilliseconds.toInt()
            additionalConfiguration()
        }
    }

    val model = DeviceCollectiveModel(context, devices, 0.2.kilometers)

    return model
}

fun CollectiveDevice.moveInCircles(): Job = launch {
    var bearing = Random.nextDouble(-PI, PI).radians
    write(CollectiveDevice.velocity, GmcVelocity(bearing, deviceVelocity))
    while (isActive) {
        delay(500)
        bearing += 5.degrees
        write(CollectiveDevice.velocity, GmcVelocity(bearing, deviceVelocity))
    }
}