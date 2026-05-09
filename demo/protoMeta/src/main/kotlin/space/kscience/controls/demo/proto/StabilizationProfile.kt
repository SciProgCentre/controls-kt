package space.kscience.controls.demo.proto

import kotlinx.serialization.Serializable

@Serializable
public data class StabilizationProfile(
    val pid: Pid = Pid(),
    val sensor: Sensor = Sensor(),
) {
    @Serializable
    public data class Pid(
        val p: Double = 1.0,
        val i: Double = 0.1,
        val d: Double = 0.01,
    )

    @Serializable
    public data class Sensor(
        val quaternion: Quaternion = Quaternion(),
        val healthy: Boolean = true,
    ) {
        @Serializable
        public data class Quaternion(
            val w: Double = 1.0,
            val x: Double = 0.0,
            val y: Double = 0.0,
            val z: Double = 0.0,
        )
    }
}
