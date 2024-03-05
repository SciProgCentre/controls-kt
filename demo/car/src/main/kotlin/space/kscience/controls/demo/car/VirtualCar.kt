@file:OptIn(ExperimentalTime::class)

package space.kscience.controls.demo.car

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import space.kscience.controls.manager.clock
import space.kscience.controls.spec.DeviceBySpec
import space.kscience.controls.spec.doRecurring
import space.kscience.controls.spec.read
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.*
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

data class Vector2D(var x: Double = 0.0, var y: Double = 0.0) : MetaRepr {

    override fun toMeta(): Meta = convert(this)

    operator fun div(arg: Double): Vector2D = Vector2D(x / arg, y / arg)

    companion object CoordinatesMetaConverter : MetaConverter<Vector2D> {

        override fun readOrNull(source: Meta): Vector2D = Vector2D(
            source["x"].double ?: 0.0,
            source["y"].double ?: 0.0
        )

        override fun convert(obj: Vector2D): Meta = Meta {
            "x" put obj.x
            "y" put obj.y
        }
    }
}

open class VirtualCar(context: Context, meta: Meta) : DeviceBySpec<VirtualCar>(IVirtualCar, context, meta),
    IVirtualCar {
    private val clock = context.clock

    private val timeScale = 1e-3

    private val mass by meta.double(1000.0) // mass in kilograms

    override var speedState: Vector2D = Vector2D()

    override var locationState: Vector2D = Vector2D()

    override var accelerationState: Vector2D = Vector2D()
        set(value) {
            update()
            field = value
        }

    private var timeState: Instant? = null

    private fun update(newTime: Instant = clock.now()) {
        //initialize time if it is not initialized
        if (timeState == null) {
            timeState = newTime
            return
        }

        val dt: Double = (newTime - (timeState ?: return)).inWholeMilliseconds.toDouble() * timeScale

        locationState.apply {
            x += speedState.x * dt + accelerationState.x * dt.pow(2) / 2.0
            y += speedState.y * dt + accelerationState.y * dt.pow(2) / 2.0
        }

        speedState.apply {
            x += dt * accelerationState.x
            y += dt * accelerationState.y
        }

        //TODO apply friction. One can introduce rotation of the cabin and different friction coefficients along the axis
        launch {
            //update logical states
            read(IVirtualCar.location)
            read(IVirtualCar.speed)
            read(IVirtualCar.acceleration)
        }

    }

    fun applyForce(force: Vector2D, duration: Duration) {
        launch {
            update()
            accelerationState = force / mass
            delay(duration)
            accelerationState.apply {
                x = 0.0
                y = 0.0
            }
            update()
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun onStart() {
        //initializing the clock
        timeState = clock.now()
        //starting regular updates
        doRecurring(100.milliseconds) {
            update()
        }
    }

    companion object : Factory<VirtualCar> {
        override fun build(context: Context, meta: Meta): VirtualCar = VirtualCar(context, meta)
    }
}
