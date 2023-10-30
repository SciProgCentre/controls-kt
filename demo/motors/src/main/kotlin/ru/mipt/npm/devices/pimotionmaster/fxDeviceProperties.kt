package ru.mipt.npm.devices.pimotionmaster

import javafx.beans.property.ObjectPropertyBase
import javafx.beans.property.Property
import javafx.beans.property.ReadOnlyProperty
import space.kscience.controls.api.Device
import space.kscience.controls.spec.*
import space.kscience.dataforge.context.info
import space.kscience.dataforge.context.logger
import tornadofx.*

/**
 * Bind a FX property to a device property with a given [spec]
 */
fun <D : Device, T : Any> D.fxProperty(
    spec: DevicePropertySpec<D, T>,
): ReadOnlyProperty<T> = object : ObjectPropertyBase<T>() {
    override fun getBean(): Any = this
    override fun getName(): String = spec.name

    init {
        //Read incoming changes
        onPropertyChange(spec) {
            runLater {
                try {
                    set(it)
                } catch (ex: Throwable) {
                    logger.info { "Failed to set property $name to $it" }
                }
            }
        }
    }
}

fun <D : Device, T : Any> D.fxProperty(spec: MutableDevicePropertySpec<D, T>): Property<T> =
    object : ObjectPropertyBase<T>() {
        override fun getBean(): Any = this
        override fun getName(): String = spec.name

        init {
            //Read incoming changes
            onPropertyChange(spec) {
                runLater {
                    try {
                        set(it)
                    } catch (ex: Throwable) {
                        logger.info { "Failed to set property $name to $it" }
                    }
                }
            }

            onChange { newValue ->
                if (newValue != null) {
                    writeAsync(spec, newValue)
                }
            }
        }
    }
