package space.kscience.controls.demo.collective

import kotlinx.serialization.Serializable
import space.kscience.kmath.geometry.Angle
import space.kscience.maps.coordinates.*
import kotlin.time.Duration
import kotlin.time.DurationUnit

@Serializable
data class GmcVelocity(val bearing: Angle, val velocity: Distance, val elevation: Distance = 0.kilometers){
    companion object{
        val zero = GmcVelocity(Angle.zero, 0.kilometers)
    }
}


fun Gmc.moveWith(velocity: GmcVelocity, duration: Duration): Gmc {
    val seconds = duration.toDouble(DurationUnit.SECONDS)

    return GeoEllipsoid.WGS84.curveInDirection(
        GmcPose(this, velocity.bearing),
        velocity.velocity * seconds,
    ).backward.coordinates
}