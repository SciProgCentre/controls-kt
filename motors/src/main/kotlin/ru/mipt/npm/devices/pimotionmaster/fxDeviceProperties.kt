package ru.mipt.npm.devices.pimotionmaster

import hep.dataforge.control.api.Device
import hep.dataforge.control.base.TypedDeviceProperty
import hep.dataforge.control.base.TypedReadOnlyDeviceProperty
import javafx.beans.property.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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