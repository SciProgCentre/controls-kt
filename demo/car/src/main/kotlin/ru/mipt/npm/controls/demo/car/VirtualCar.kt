@file:OptIn(ExperimentalTime::class)

package ru.mipt.npm.controls.demo.car

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import ru.mipt.npm.controls.spec.DeviceBySpec
import ru.mipt.npm.controls.spec.doRecurring
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaRepr
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.transformations.MetaConverter
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

data class Vector2D(var x: Double = 0.0, var y: Double = 0.0) : MetaRepr {

    override fun toMeta(): Meta = objectToMeta(this)

    operator fun div(arg: Double): Vector2D = Vector2D(x / arg, y / arg)

    companion object CoordinatesMetaConverter : MetaConverter<Vector2D> {
        override fun metaToObject(meta: Meta): Vector2D = Vector2D(
            meta["x"].double ?: 0.0,
            meta["y"].double ?: 0.0
        )

        override fun objectToMeta(obj: Vector2D): Meta = Meta {
            "x" put obj.x
            "y" put obj.y
        }
    }
}

open class VirtualCar(context: Context, meta: Meta) : DeviceBySpec<VirtualCar>(IVirtualCar, context, meta), IVirtualCar {
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

    private fun update(newTime: Instant = Clock.System.now()) {
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
            IVirtualCar.location.read()
            IVirtualCar.speed.read()
            IVirtualCar.acceleration.read()
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
    override suspend fun open() {
        super<DeviceBySpec>.open()
        //initializing the clock
        timeState = Clock.System.now()
        //starting regular updates
        doRecurring(Duration.milliseconds(100)) {
            update()
        }
    }

    companion object : Factory<VirtualCar> {
        override fun invoke(meta: Meta, context: Context): VirtualCar = VirtualCar(context, meta)
    }
}
