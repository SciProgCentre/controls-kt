package hep.dataforge.control.demo

import hep.dataforge.control.base.DeviceBase
import hep.dataforge.control.base.mutableProperty
import hep.dataforge.control.base.property
import hep.dataforge.meta.Meta
import kotlinx.coroutines.CoroutineScope
import java.time.Instant
import kotlin.math.cos
import kotlin.math.sin

class VirtualDevice(val meta: Meta, override val scope: CoroutineScope) : DeviceBase() {

    var scale by mutableProperty {
        getDouble {
            200.0
        }.virtualSet()
    }

    val sin by property {
        getDouble {
            val time = Instant.now()
            sin(time.toEpochMilli().toDouble() / (scale ?: 1000.0))
        }
    }

    val cos by property {
        getDouble {
            val time = Instant.now()
            cos(time.toEpochMilli().toDouble() / (scale ?: 1000.0))
        }
    }
}