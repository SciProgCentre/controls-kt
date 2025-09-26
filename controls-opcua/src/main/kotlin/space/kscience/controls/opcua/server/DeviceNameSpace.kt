package space.kscience.controls.opcua.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.eclipse.milo.opcua.sdk.core.AccessLevel
import org.eclipse.milo.opcua.sdk.core.Reference
import org.eclipse.milo.opcua.sdk.server.Lifecycle
import org.eclipse.milo.opcua.sdk.server.ManagedNamespaceWithLifecycle
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.sdk.server.items.DataItem
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel
import org.eclipse.milo.opcua.stack.core.AttributeId
import org.eclipse.milo.opcua.stack.core.NodeIds
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import space.kscience.controls.api.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.ValueType
import kotlin.time.toJavaInstant


public operator fun CachingDevice.get(propertyDescriptor: PropertyDescriptor): Meta? =
    getCachedProperty(propertyDescriptor.name)

public suspend fun Device.read(propertyDescriptor: PropertyDescriptor): Meta = readProperty(propertyDescriptor.name)

/*
https://github.com/eclipse/milo/blob/master/milo-examples/server-examples/src/main/java/org/eclipse/milo/examples/server/ExampleNamespace.java
 */

public class DeviceNameSpace(
    private val scope: CoroutineScope,
    server: OpcUaServer,
    public val deviceHub: DeviceHub
) : ManagedNamespaceWithLifecycle(server, NAMESPACE_URI) {

    private val subscription = SubscriptionModel(server, this)

    /**
     * Register device node within existing folder
     */
    private fun UaFolderNode.registerDeviceNodes(deviceName: String, device: Device) {
        val nodes = device.propertyDescriptors.associate { descriptor ->
            val propertyName = descriptor.name


            val node: UaVariableNode = UaVariableNode.UaVariableNodeBuilder(nodeContext).apply {
                //for now, use DF paths as ids
                nodeId = newNodeId("$deviceName/$propertyName")
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
                    NodeIds.String
                } else when (descriptor.metaDescriptor.valueTypes?.first()) {
                    null, ValueType.STRING, ValueType.NULL -> NodeIds.String
                    ValueType.NUMBER -> NodeIds.Number
                    ValueType.BOOLEAN -> NodeIds.Boolean
                    ValueType.LIST -> NodeIds.ArrayItemType
                }


                setTypeDefinition(NodeIds.BaseDataVariableType)
            }.build()

            // Update the initial value, but only if it is cached
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
                        scope.launch {
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
            device.devices.forEach { (childDeviceName, device) ->

                val deviceFolder = UaFolderNode(
                    nodeContext,
                    newNodeId("$deviceName/$childDeviceName"),
                    newQualifiedName("$deviceName/$childDeviceName"),
                    LocalizedText.english(childDeviceName.toString())
                )

                deviceFolder.registerDeviceNodes("$deviceName/$childDeviceName", device)

                nodeManager.addNode(deviceFolder)
                addOrganizes(deviceFolder)
            }
        }
    }

    private fun UaNodeContext.registerTopLevelHub(hub: DeviceHub) {
        val rootNode = UaFolderNode(
            nodeContext,
            newNodeId("Controls"),
            newQualifiedName("Controls"),
            LocalizedText.english("Controls")
        )

        hub.devices.forEach { (deviceName, device) ->
            val nameAsString = "$deviceName"

            val deviceFolder = UaFolderNode(
                nodeContext,
                newNodeId(nameAsString),
                newQualifiedName(nameAsString),
                LocalizedText.english(nameAsString)
            )

            deviceFolder.registerDeviceNodes(deviceName.toString(), device)

            nodeManager.addNode(deviceFolder)

            rootNode.addOrganizes(deviceFolder)
        }

        nodeManager.addNode(rootNode)

        rootNode.addReference(
            Reference(
                rootNode.nodeId,
                NodeIds.Organizes,
                NodeIds.ObjectsFolder.expanded(),
                false
            )
        )


    }

    init {
        lifecycleManager.addLifecycle(subscription)

        lifecycleManager.addStartupTask {
            nodeContext.registerTopLevelHub(deviceHub)
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


public fun OpcUaServer.serveDevices(scope: CoroutineScope, deviceHub: DeviceHub): DeviceNameSpace =
    DeviceNameSpace(scope, this, deviceHub).apply { startup() }

/**
 *  Serve devices from [deviceManager] as OPC-UA
 */
public fun OpcUaServer.serveDevices(deviceManager: DeviceManager): DeviceNameSpace =
    serveDevices(deviceManager.context, deviceManager)