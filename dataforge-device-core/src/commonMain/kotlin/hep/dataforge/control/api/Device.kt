package hep.dataforge.control.api

import hep.dataforge.control.api.Device.Companion.DEVICE_TARGET
import hep.dataforge.control.controllers.DeviceMessage
import hep.dataforge.control.controllers.MessageController
import hep.dataforge.control.controllers.MessageData
import hep.dataforge.io.Envelope
import hep.dataforge.io.Responder
import hep.dataforge.io.SimpleEnvelope
import hep.dataforge.meta.Meta
import hep.dataforge.meta.MetaItem
import hep.dataforge.meta.wrap
import hep.dataforge.provider.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.io.Binary
import kotlinx.io.Closeable

/**
 *  General interface describing a managed Device
 */
@Type(DEVICE_TARGET)
interface Device: Closeable, Responder {
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
    suspend fun exec(action: String, argument: MetaItem<*>? = null): MetaItem<*>?

    override suspend fun respond(request: Envelope): Envelope {
        val requestMessage = DeviceMessage.wrap(request.meta)
        val responseMessage = respondMessage(requestMessage)
        return SimpleEnvelope(responseMessage.toMeta(), Binary.EMPTY)
    }

    override fun close() {
        scope.cancel("The device is closed")
    }

    companion object{
        const val DEVICE_TARGET = "device"
    }
}

suspend fun Device.respondMessage(
    request: DeviceMessage
): DeviceMessage {
    val result: List<MessageData> = when (val action = request.type) {
        MessageController.GET_PROPERTY_ACTION -> {
            request.data.map { property ->
                MessageData {
                    name = property.name
                    value = getProperty(name)
                }
            }
        }
        MessageController.SET_PROPERTY_ACTION -> {
            request.data.map { property ->
                val propertyName: String = property.name
                val propertyValue = property.value
                if (propertyValue == null) {
                    invalidateProperty(propertyName)
                } else {
                    setProperty(propertyName, propertyValue)
                }
                MessageData {
                    name = propertyName
                    value = getProperty(propertyName)
                }
            }
        }
        MessageController.EXECUTE_ACTION -> {
            request.data.map { payload ->
                MessageData {
                    name = payload.name
                    value = exec(payload.name, payload.value)
                }
            }
        }
        MessageController.PROPERTY_LIST_ACTION -> {
            propertyDescriptors.map { descriptor ->
                MessageData {
                    name = descriptor.name
                    value = MetaItem.NodeItem(descriptor.config)
                }
            }
        }

        MessageController.ACTION_LIST_ACTION -> {
            actionDescriptors.map { descriptor ->
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
    return DeviceMessage.ok {
        target = request.source
        data = result
    }
}

suspend fun Device.exec(name: String, meta: Meta?) = exec(name, meta?.let { MetaItem.NodeItem(it) })