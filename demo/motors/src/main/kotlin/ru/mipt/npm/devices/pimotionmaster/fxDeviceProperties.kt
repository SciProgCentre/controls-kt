package ru.mipt.npm.devices.pimotionmaster

import javafx.beans.property.ObjectPropertyBase
import javafx.beans.property.Property
import javafx.beans.property.ReadOnlyProperty
import ru.mipt.npm.controls.api.Device
import ru.mipt.npm.controls.spec.*
import space.kscience.dataforge.context.info
import space.kscience.dataforge.context.logger
import tornadofx.*

/**
 * Bind a FX property to a device property with a given [spec]
 */
fun <D : Device, T : Any> Device.fxProperty(
    spec: DevicePropertySpec<D, T>
): ReadOnlyProperty<T> = object : ObjectPropertyBase<T>() {
    override fun getBean(): Any = this
    override fun getName(): String = spec.name

    init {
        //Read incoming changes
        onPropertyChange(spec) {
            if (it != null) {
                runLater {
                    try {
                        set(it)
                    } catch (ex: Throwable) {
                        logger.info { "Failed to set property $name to $it" }
                    }
                }
            } else {
                invalidated()
            }
        }
    }
}

fun <D : Device, T : Any> D.fxProperty(spec: WritableDevicePropertySpec<D, T>): Property<T> =
    object : ObjectPropertyBase<T>() {
        override fun getBean(): Any = this
        override fun getName(): String = spec.name

        init {
            //Read incoming changes
            onPropertyChange(spec) {
                if (it != null) {
                    runLater {
                        try {
                            set(it)
                        } catch (ex: Throwable) {
                            logger.info { "Failed to set property $name to $it" }
                        }
                    }
                } else {
                    invalidated()
                }
            }

            onChange { newValue ->
                if (newValue != null) {
                    write(spec, newValue)
                }
            }
        }
    }
