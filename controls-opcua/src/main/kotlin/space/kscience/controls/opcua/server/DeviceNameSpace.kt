package space.kscience.controls.opcua.server

import kotlinx.coroutines.launch
import kotlinx.datetime.toJavaInstant
import org.eclipse.milo.opcua.sdk.core.AccessLevel
import org.eclipse.milo.opcua.sdk.core.Reference
import org.eclipse.milo.opcua.sdk.server.Lifecycle
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.api.DataItem
import org.eclipse.milo.opcua.sdk.server.api.ManagedNamespaceWithLifecycle
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel
import org.eclipse.milo.opcua.stack.core.AttributeId
import org.eclipse.milo.opcua.stack.core.Identifiers
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import space.kscience.controls.api.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.opcua.client.opcToMeta
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.plus


public operator fun CachingDevice.get(propertyDescriptor: PropertyDescriptor): Meta? =
    getProperty(propertyDescriptor.name)

public suspend fun Device.read(propertyDescriptor: PropertyDescriptor): Meta = readProperty(propertyDescriptor.name)

/*
https://github.com/eclipse/milo/blob/master/milo-examples/server-examples/src/main/java/org/eclipse/milo/examples/server/ExampleNamespace.java
 */

public class DeviceNameSpace(
    server: OpcUaServer,
    public val deviceManager: DeviceManager,
) : ManagedNamespaceWithLifecycle(server, NAMESPACE_URI) {

    private val subscription = SubscriptionModel(server, this)

    private fun UaFolderNode.registerDeviceNodes(deviceName: Name, device: Device) {
        val nodes = device.propertyDescriptors.associate { descriptor ->
            val propertyName = descriptor.name


            val node: UaVariableNode = UaVariableNode.UaVariableNodeBuilder(nodeContext).apply {
                //for now, use DF paths as ids
                nodeId = newNodeId("${deviceName.tokens.joinToString("/")}/$propertyName")
                when {
                    descriptor.readable && descriptor.mutable -> {
                        setAccessLevel(AccessLevel.READ_WRITE)
                        setUserAccessLevel(AccessLevel.READ_WRITE)
                    }

                    descriptor.mutable -> {
                        setAccessLevel(AccessLevel.WRITE_ONLY)
                        setUserAccessLevel(AccessLevel.WRITE_ONLY)
                    }

                    descriptor.readable -> {
                        setAccessLevel(AccessLevel.READ_ONLY)
                        setUserAccessLevel(AccessLevel.READ_ONLY)
                    }

                    else -> {
                        setAccessLevel(AccessLevel.NONE)
                        setUserAccessLevel(AccessLevel.NONE)
                    }
                }

                browseName = newQualifiedName(propertyName)
                displayName = LocalizedText.english(propertyName)
                dataType = if (descriptor.metaDescriptor.nodes.isNotEmpty()) {
                    Identifiers.String
                } else when (descriptor.metaDescriptor.valueTypes?.first()) {
                    null, ValueType.STRING, ValueType.NULL -> Identifiers.String
                    ValueType.NUMBER -> Identifiers.Number
                    ValueType.BOOLEAN -> Identifiers.Boolean
                    ValueType.LIST -> Identifiers.ArrayItemType
                }


                setTypeDefinition(Identifiers.BaseDataVariableType)
            }.build()

            // Update initial value, but only if it is cached
            if (device is CachingDevice) {
                device[descriptor]?.toOpc(sourceTime = null, serverTime = null)?.let {
                    node.value = it
                }
            }

            if (descriptor.mutable) {

                /**
                 * Subscribe to node value changes
                 */
                node.addAttributeObserver { _: UaNode, attributeId: AttributeId, value: Any? ->
                    if (attributeId == AttributeId.Value) {
                        val meta: Meta = opcToMeta(value)
                        deviceManager.context.launch {
                            device.writeProperty(propertyName, meta)
                        }
                    }
                }
            }

            nodeManager.addNode(node)
            addOrganizes(node)
            propertyName to node
        }

        //Subscribe on properties updates
        device.onPropertyChange {
            nodes[property]?.let { node ->
                val sourceTime = DateTime(time.toJavaInstant())
                val newValue = value.toOpc(sourceTime = sourceTime)
                if (node.value.value != newValue.value) {
                    node.value = newValue
                }
            }
        }
        //recursively add sub-devices
        if (device is DeviceHub) {
            nodeContext.registerHub(device, deviceName)
        }
    }

    private fun UaNodeContext.registerHub(hub: DeviceHub, namePrefix: Name) {
        hub.devices.forEach { (deviceName, device) ->
            val tokenAsString = deviceName.toString()
            val deviceFolder = UaFolderNode(
                this,
                newNodeId(tokenAsString),
                newQualifiedName(tokenAsString),
                LocalizedText.english(tokenAsString)
            )
            deviceFolder.addReference(
                Reference(
                    deviceFolder.nodeId,
                    Identifiers.Organizes,
                    Identifiers.ObjectsFolder.expanded(),
                    false
                )
            )
            deviceFolder.registerDeviceNodes(namePrefix + deviceName, device)
            this.nodeManager.addNode(deviceFolder)
        }
    }

    init {
        lifecycleManager.addLifecycle(subscription)

        lifecycleManager.addStartupTask {
            nodeContext.registerHub(deviceManager, Name.EMPTY)
        }

        lifecycleManager.addLifecycle(object : Lifecycle {
            override fun startup() {
                server.addressSpaceManager.register(this@DeviceNameSpace)
            }

            override fun shutdown() {
                server.addressSpaceManager.unregister(this@DeviceNameSpace)
            }
        })
    }

    override fun onDataItemsCreated(dataItems: List<DataItem?>?) {
        subscription.onDataItemsCreated(dataItems)
    }

    override fun onDataItemsModified(dataItems: List<DataItem?>?) {
        subscription.onDataItemsModified(dataItems)
    }

    override fun onDataItemsDeleted(dataItems: List<DataItem?>?) {
        subscription.onDataItemsDeleted(dataItems)
    }

    override fun onMonitoringModeChanged(monitoredItems: List<MonitoredItem?>?) {
        subscription.onMonitoringModeChanged(monitoredItems)
    }

    public companion object {
        public const val NAMESPACE_URI: String = "urn:space:kscience:controls:opcua:server"
    }
}

/**
 *  Serve devices from [deviceManager] as OPC-UA
 */
public fun OpcUaServer.serveDevices(deviceManager: DeviceManager): DeviceNameSpace =
    DeviceNameSpace(this, deviceManager).apply { startup() }