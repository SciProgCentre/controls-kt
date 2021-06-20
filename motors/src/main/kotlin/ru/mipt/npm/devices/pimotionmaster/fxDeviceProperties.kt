package ru.mipt.npm.devices.pimotionmaster

import javafx.beans.property.ObjectPropertyBase
import javafx.beans.property.Property
import javafx.beans.property.ReadOnlyProperty
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.mipt.npm.controls.api.Device
import ru.mipt.npm.controls.base.TypedDeviceProperty
import ru.mipt.npm.controls.base.TypedReadOnlyDeviceProperty
import space.kscience.dataforge.context.info
import space.kscience.dataforge.context.logger
import tornadofx.*

fun <T : Any> TypedReadOnlyDeviceProperty<T>.fxProperty(ownerDevice: Device?): ReadOnlyProperty<T> =
    object : ObjectPropertyBase<T>() {
        override fun getBean(): Any? = ownerDevice
        override fun getName(): String = this@fxProperty.name

        init {
            //Read incoming changes
            flowTyped().onEach {
                if (it != null) {
                    runLater {
                        set(it)
                    }
                } else {
                    invalidated()
                }
            }.catch {
                ownerDevice?.logger?.info { "Failed to set property $name to $it" }
            }.launchIn(scope)
        }
    }

fun <T : Any> TypedDeviceProperty<T>.fxProperty(ownerDevice: Device?): Property<T> =
    object : ObjectPropertyBase<T>() {
        override fun getBean(): Any? = ownerDevice
        override fun getName(): String = this@fxProperty.name

        init {
            //Read incoming changes
            flowTyped().onEach {
                if (it != null) {
                    runLater {
                        set(it)
                    }
                } else {
                    invalidated()
                }
            }.catch {
                ownerDevice?.logger?.info { "Failed to set property $name  to $it" }
            }.launchIn(scope)

            onChange {
                typedValue = it
            }
        }
    }
