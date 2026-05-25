package space.kscience.controls.binary

import kotlinx.coroutines.CoroutineScope
import space.kscience.controls.time.clock
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.dataforge.misc.DFInternal
import kotlin.time.Clock

/**
 * An object that is used to construct a graph of frame processing nodes. It is not required to use this graph to create a frame processor pipeline,
 * but it could be used as a single monitoring point for the whole pipeline.
 */
@DFExperimental
public class FrameProcessingGraph(
    public val context: Context,
    public val scope: CoroutineScope = context,
    public val clock: Clock = context.clock,
) {
    public class Node(
        public val id: String,
        public val producer: FrameProducer,
        public val sources: Set<Node>
    ) {
        override fun toString(): String =
            "FrameProcessingGraph.Node(id='$id', sources=${sources.map { it.id }})"
    }

    private val _nodes = mutableMapOf<String, Node>()
    public val nodes: Collection<Node> get() = _nodes.values

    /**
     * Register a producer without dependencies
     */
    public fun producer(id: String, producer: FrameProducer): Node {
        val node = Node(id, producer, emptySet())
        _nodes[id] = node
        return node
    }

    /**
     * Register a node with explicit dependencies.
     *
     * User is responsible for providing a valid set of dependencies.
     */
    @DFInternal
    public fun node(id: String, producer: FrameProducer, subscribedOn: Set<Node>): Node {
        val node = Node(id, producer, subscribedOn)
        _nodes[id] = node
        return node
    }

    /**
     * Construct and register a new frame processor node with given sources and transformer.
     */
    @OptIn(DFInternal::class)
    public fun node(id: String, sources: Collection<Node>, transformer: FrameTransformer): Node {
        val processor = FrameProcessor(
            scope = scope,
            transformer = transformer,
            clock = clock
        )

        sources.forEach { processor.subscribe(it.producer) }

        return node(id, processor, sources.toSet())
    }

    /**
     * Register a new frame processor node that transforms frames from [this] node.
     */
    public fun Node.transform(id: String, transformer: FrameTransformer): Node = node(id, setOf(this), transformer)

}