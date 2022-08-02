package space.kscience.controls.opcua.server

import org.eclipse.milo.opcua.sdk.core.AccessLevel
import org.eclipse.milo.opcua.sdk.core.Reference
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode
import org.eclipse.milo.opcua.stack.core.Identifiers
import org.eclipse.milo.opcua.stack.core.types.builtin.*


internal fun UaNode.inverseReferenceTo(targetNodeId: NodeId, typeId: NodeId) {
    addReference(
        Reference(
            nodeId,
            typeId,
            targetNodeId.expanded(),
            Reference.Direction.INVERSE
        )
    )
}

internal fun NodeId.resolve(child: String): NodeId {
    val id = this.identifier.toString()
    return NodeId(this.namespaceIndex, "$id/$child")
}


internal fun UaNodeContext.addVariableNode(
    parentNodeId: NodeId,
    name: String,
    nodeId: NodeId = parentNodeId.resolve(name),
    dataTypeId: NodeId,
    value: Any,
    referenceTypeId: NodeId = Identifiers.HasComponent
): UaVariableNode {

    val variableNode: UaVariableNode = UaVariableNode.UaVariableNodeBuilder(this).apply {
        setNodeId(nodeId)
        setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
        setBrowseName(QualifiedName(parentNodeId.namespaceIndex, name))
        setDisplayName(LocalizedText.english(name))
        setDataType(dataTypeId)
        setTypeDefinition(Identifiers.BaseDataVariableType)
        setMinimumSamplingInterval(100.0)
        setValue(DataValue(Variant(value)))
    }.build()

//    variableNode.filterChain.addFirst(AttributeLoggingFilter())

    nodeManager.addNode(variableNode)

    variableNode.inverseReferenceTo(
        parentNodeId,
        referenceTypeId
    )

    return variableNode
}
//
//fun UaNodeContext.addVariableNode(
//    parentNodeId: NodeId,
//    name: String,
//    nodeId: NodeId = parentNodeId.resolve(name),
//    dataType: BuiltinDataType = BuiltinDataType.Int32,
//    referenceTypeId: NodeId = Identifiers.HasComponent
//): UaVariableNode = addVariableNode(
//    parentNodeId,
//    name,
//    nodeId,
//    dataType.nodeId,
//    dataType.defaultValue(),
//    referenceTypeId
//)


