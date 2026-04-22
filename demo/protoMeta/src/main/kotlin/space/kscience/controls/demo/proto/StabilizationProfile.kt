package space.kscience.controls.demo.proto

import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.boolean
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.invoke

public data class StabilizationProfile(
    val pid: Pid = Pid(),
    val sensor: Sensor = Sensor(),
) {
    public data class Pid(
        val p: Double = 1.0,
        val i: Double = 0.1,
        val d: Double = 0.01,
    )

    public data class Sensor(
        val quaternion: Quaternion = Quaternion(),
        val healthy: Boolean = true,
    ) {
        public data class Quaternion(
            val w: Double = 1.0,
            val x: Double = 0.0,
            val y: Double = 0.0,
            val z: Double = 0.0,
        )
    }

    public companion object : MetaConverter<StabilizationProfile> {
        override fun readOrNull(source: Meta): StabilizationProfile {
            val pid = source["pid"]
            val sensor = source["sensor"]
            val q = sensor["quaternion"]
            return StabilizationProfile(
                pid = Pid(
                    p = pid["p"].double ?: 1.0,
                    i = pid["i"].double ?: 0.1,
                    d = pid["d"].double ?: 0.01,
                ),
                sensor = Sensor(
                    healthy = sensor["healthy"].boolean ?: true,
                    quaternion = Sensor.Quaternion(
                        w = q["w"].double ?: 1.0,
                        x = q["x"].double ?: 0.0,
                        y = q["y"].double ?: 0.0,
                        z = q["z"].double ?: 0.0,
                    ),
                ),
            )
        }

        override fun convert(obj: StabilizationProfile): Meta = Meta {
            "pid" put {
                "p" put obj.pid.p
                "i" put obj.pid.i
                "d" put obj.pid.d
            }
            "sensor" put {
                "healthy" put obj.sensor.healthy
                "quaternion" put {
                    "w" put obj.sensor.quaternion.w
                    "x" put obj.sensor.quaternion.x
                    "y" put obj.sensor.quaternion.y
                    "z" put obj.sensor.quaternion.z
                }
            }
        }
    }
}
