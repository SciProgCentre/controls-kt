package ru.mipt.npm.devices.pimotionmaster

import hep.dataforge.control.base.DeviceBase
import hep.dataforge.control.base.DeviceProperty
import hep.dataforge.control.base.writingVirtual
import hep.dataforge.control.ports.Port
import hep.dataforge.control.ports.PortProxy
import hep.dataforge.control.ports.withDelimiter
import hep.dataforge.meta.MetaItem
import hep.dataforge.values.Null
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first

class PiMotionMasterDevice(parentScope: CoroutineScope, val portFactory: suspend (MetaItem<*>?) -> Port) : DeviceBase() {
    override val scope: CoroutineScope = CoroutineScope(
        parentScope.coroutineContext + Job(parentScope.coroutineContext[Job])
    )

    public val port: DeviceProperty by writingVirtual(Null) {
        info = "The port for TCP connector"
    }

    private val connector = PortProxy { portFactory(port.value) }

    private suspend fun readPhrase(command: String) {
        connector.receiving().withDelimiter("\n").first { it.startsWith(command) }
    }

//
//    val firmwareVersion by reading {
//        connector.r
//    }


}