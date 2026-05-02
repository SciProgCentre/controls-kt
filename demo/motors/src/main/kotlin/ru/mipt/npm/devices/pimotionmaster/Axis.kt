package ru.mipt.npm.devices.pimotionmaster

import kotlinx.coroutines.delay
import space.kscience.controls.api.Device
import space.kscience.controls.spec.*
import space.kscience.controls.unit
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.get
import kotlin.time.Duration.Companion.milliseconds


object Axis : AbstractDeviceSpec() {

    val enabled by property(MetaConverter.boolean) {
        description = "Motor enable state."
    }

    val halt by unitAction()

    val targetPosition by mutableProperty(MetaConverter.double) {
        description = """
            Sets a new absolute target position for the specified axis.
            Servo mode must be switched on for the commanded axis prior to using this command (closed-loop operation).
        """.trimIndent()
    }

    val onTarget by property(MetaConverter.boolean) {
        description = "Queries the on-target state of the specified axis."
    }

    val reference by property(MetaConverter.boolean) {
        description = "Get Referencing Result"
    }

    val moveToReference by unitAction()

    val minPosition by property(MetaConverter.double) {
        description = "Minimal position value for the axis"
    }

    val maxPosition by property(MetaConverter.double) {
        description = "Maximal position value for the axis"
    }

    val position by property(MetaConverter.double) {
        description = "The current axis position."
    }

    val openLoopTarget by mutableProperty(MetaConverter.double) {
        description = "Position for open-loop operation."
    }

    val closedLoop by mutableProperty(MetaConverter.boolean) {
        description = "Servo closed loop mode"
    }

    val velocity by mutableProperty(MetaConverter.double) {
        description = "Velocity value for closed-loop operation"
    }

    val move by action(MetaConverter.meta, MetaConverter.unit)

    fun builder(
        port: PiMotionMasterConnector,
        axisId: String,
    ) = DeviceBuilder(Axis){

        suspend fun readAxisBoolean(axisId: String, command: String): Boolean =
            (port.requestAndParse(command, axisId)[axisId]?.toIntOrNull()
                ?: error("Malformed $command response. Should include integer value for $axisId")) != 0

        suspend fun writeAxisBoolean(axisId: String, command: String, value: Boolean): Boolean {
            val boolean = if (value) {
                "1"
            } else {
                "0"
            }
            port.send(command, axisId, boolean)
            port.failIfError()
            return value
        }


        fun axisNumberProperty(
            spec: DevicePropertySpec<Double>,
            command: String,
        ) {
            reader(spec) {
                port.requestAndParse("$command?", axisId)[axisId]?.toDoubleOrNull()
                    ?: error("Malformed $command response. Should include float value for $axisId")
            }

            writer(spec) { newValue ->
                port.send(command, axisId, newValue.toString())
                port.failIfError()

            }
        }

        fun axisBooleanProperty(
            spec: DevicePropertySpec<Boolean>,
            command: String
        ) {
            reader(spec) {
                readAxisBoolean(axisId, command)
            }

            writer(spec) { newValue ->
                writeAxisBoolean(axisId, command, newValue)
            }
        }

        reader(Axis.enabled) {
            readAxisBoolean(axisId, "EAX")
        }

        action(Axis.halt) {
            port.send("HLT", axisId)
        }

        axisNumberProperty(Axis.targetPosition, "MOV")

        reader(Axis.onTarget) {
            readAxisBoolean(axisId, "ONT?")
        }

        reader(Axis.reference) {
            readAxisBoolean(axisId, "FRF?")
        }

        action(Axis.moveToReference) {
            port.send("FRF", axisId)
        }

        reader(Axis.minPosition) {
            port.requestAndParse("TMN?", axisId)[axisId]?.toDoubleOrNull()
                ?: error("Malformed `TMN?` response. Should include float value for $axisId")

        }

        reader(Axis.maxPosition) {
            port.requestAndParse("TMX?", axisId)[axisId]?.toDoubleOrNull()
                ?: error("Malformed `TMX?` response. Should include float value for $axisId")
        }

        reader(Axis.position) {
            port.requestAndParse("POS?", axisId)[axisId]?.toDoubleOrNull()
                ?: error("Malformed `POS?` response. Should include float value for $axisId")
        }

        axisNumberProperty(Axis.openLoopTarget, "OMA")

        axisBooleanProperty(Axis.closedLoop, "SVO")

        axisNumberProperty(Axis.velocity, "VEL")

        action(Axis.move) { it ->
            val target = it.double ?: it["target"].double ?: error("Unacceptable target value $it")
            write(Axis.closedLoop, true)
            //optionally set velocity
            it["velocity"].double?.let { v ->
                write(Axis.velocity, v)
            }
            write(Axis.targetPosition, target)
            //read `onTarget` and `position` properties in a cycle until movement is complete
            while (!read(Axis.onTarget)) {
                read(Axis.position)
                delay(200.milliseconds)
            }
        }
    }

    fun build(
        context: Context,
        meta: Meta,
        port: PiMotionMasterConnector,
        axisId: String,
    ): Device = builder(port, axisId).build(context, meta)

}