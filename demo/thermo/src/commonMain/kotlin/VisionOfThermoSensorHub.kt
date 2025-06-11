package center.sciprog.controls.demo.thermo

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import space.kscience.dataforge.meta.listOfSerializable
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.visionforge.AbstractVision


@Serializable
data class ThermoSensorVisionState(
    val temperature: Double,
    val status: ThermoSensorStatus,
    val history: Map<Instant, Double>,
)

@Serializable
@SerialName("controls.thermo")
class VisionOfThermoSensorHub: AbstractVision() {
    @OptIn(DFExperimental::class)
    var positions by properties.listOfSerializable<ThermoSensorVisionState>()
}