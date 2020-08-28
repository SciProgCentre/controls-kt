package hep.dataforge.control.api

import hep.dataforge.control.api.Device.Companion.DEVICE_TARGET
import hep.dataforge.control.controllers.DeviceMessage
import hep.dataforge.control.controllers.MessageData
import hep.dataforge.control.controllers.wrap
import hep.dataforge.io.Envelope
import hep.dataforge.io.EnvelopeBuilder
import hep.dataforge.meta.*
import hep.dataforge.provider.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.io.Closeable

interface Consumer {
    fun consume(message: Envelope): Unit
}

/**
 *  General interface describing a managed Device
 */
@Type(DEVICE_TARGET)
interface Device: Closeable{
    /**
     * List of supported property descriptors
     */
    val propertyDescriptors: Collection<PropertyDescriptor>

    /**
     * List of supported action descriptors. Action is a request to the device that
     * may or may not change the properties
     */
    val actionDescriptors: Collection<ActionDescriptor>

    /**
     * The scope encompassing all operations on a device. When canceled, cancels all running processes
     */
    val scope: CoroutineScope

    /**
     * Register a new property change listener for this device.
     * [owner] is provided optionally in order for listener to be
     * easily removable
     */
    fun registerListener(listener: DeviceListener, owner: Any? = listener)

    /**
     * Remove all listeners belonging to the specified owner
     */
    fun removeListeners(owner: Any?)

    /**
     * Get the value of the property or throw error if property in not defined.
     * Suspend if property value is not available
     */
    suspend fun getProperty(propertyName: String): MetaItem<*>

    /**
     * Invalidate property and force recalculate
     */
    suspend fun invalidateProperty(propertyName: String)

    /**
     * Set property [value] for a property with name [propertyName].
     * In rare cases could suspend if the [Device] supports command queue and it is full at the moment.
     */
    suspend fun setProperty(propertyName: String, value: MetaItem<*>)

    /**
     * Send an action request and suspend caller while request is being processed.
     * Could return null if request does not return a meaningful answer.
     */
    suspend fun execute(action: String, argument: MetaItem<*>? = null): MetaItem<*>?

    /**
     *
     */
    suspend fun respondWithData(request: Envelope): EnvelopeBuilder = error("Respond with data not implemented")

    override fun close() {
        scope.cancel("The device is closed")
    }

    companion object{
        const val DEVICE_TARGET = "device"
        const val GET_PROPERTY_ACTION = "read"
        const val SET_PROPERTY_ACTION = "write"
        const val EXECUTE_ACTION = "execute"
        const val PROPERTY_LIST_ACTION = "propertyList"
        const val ACTION_LIST_ACTION = "actionList"

        internal suspend fun respond(device: Device, deviceTarget: String, request: Envelope): Envelope {
            val target = request.meta["target"].string
            return try {
                if (request.data == null) {
                    respondMessage(device, deviceTarget, DeviceMessage.wrap(request.meta)).wrap()
                } else if (target != null && target != deviceTarget) {
                    error("Wrong target name $deviceTarget expected but $target found")
                } else {
                    val response = device.respondWithData(request).apply {
                        meta {
                            "target" put request.meta["source"].string
                            "source" put deviceTarget
                        }
                    }
                    return response.build()
                }
            } catch (ex: Exception) {
                DeviceMessage.fail {
                    comment = ex.message
                }.wrap()
            }
        }

        internal suspend fun respondMessage(
            device: Device,
            deviceTarget: String,
            request: DeviceMessage
        ): DeviceMessage {
            return try {
                val result: List<MessageData> = when (val action = request.type) {
                    GET_PROPERTY_ACTION -> {
                        request.data.map { property ->
                            MessageData {
                                name = property.name
                                value = device.getProperty(name)
                            }
                        }
                    }
                    SET_PROPERTY_ACTION -> {
                        request.data.map { property ->
                            val propertyName: String = property.name
                            val propertyValue = property.value
                            if (propertyValue == null) {
                                device.invalidateProperty(propertyName)
                            } else {
                                device.setProperty(propertyName, propertyValue)
                            }
                            MessageData {
                                name = propertyName
                                value =  device.getProperty(propertyName)
                            }
                        }
                    }
                    EXECUTE_ACTION -> {
                        request.data.map { payload ->
                            MessageData {
                                name = payload.name
                                value =  device.execute(payload.name, payload.value)
                            }
                        }
                    }
                    PROPERTY_LIST_ACTION -> {
                        device.propertyDescriptors.map { descriptor ->
                            MessageData {
                                name = descriptor.name
                                value = MetaItem.NodeItem(descriptor.config)
                            }
                        }
                    }

                    ACTION_LIST_ACTION -> {
                        device.actionDescriptors.map { descriptor ->
                            MessageData {
                                name = descriptor.name
                                value = MetaItem.NodeItem(descriptor.config)
                            }
                        }
                    }

                    else -> {
                        error("Unrecognized action $action")
                    }
                }
                DeviceMessage.ok {
                    target = request.source
                    source = deviceTarget
                    data = result
                }
            } catch (ex: Exception) {
                DeviceMessage.fail {
                    comment = ex.message
                }
            }
        }
    }
}

suspend fun Device.execute(name: String, meta: Meta?): MetaItem<*>? = execute(name, meta?.let { MetaItem.NodeItem(it) })