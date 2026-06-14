@file:OptIn(DFExperimental::class)

package space.kscience.controls.constructor.models.continuous

import space.kscience.controls.api.DeviceFactory
import space.kscience.controls.api.DeviceTreeFactory
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.units.*
import space.kscience.controls.manager.DeviceLibrary
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.createDeviceTree
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.*
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.dataforge.names.*

/**
 * A device library for continuous flow models
 */
public class ContinuousModelLibrary<U : UnitsOfMatter, T : Amount<U>>(
    public val algebra: AmountAlgebra<U, T>
) : DeviceLibrary {

    public val producer: DeviceFactory = DeviceFactory(
        MetaDescriptor {
            value("productionCapacity", ValueType.NUMBER) { required() }
        }
    ) { context, parameters ->

        val productionCapacity = parameters["productionCapacity"].double
            ?: error("Production capacity parameter is required")

        ContinuousProducerDevice<U, T>(
            context = context,
            producerAlgebra = algebra,
            productionCapacity = ValueState(algebra.valueOf(productionCapacity).perSecond)
        )
    }

    public val buffer: DeviceFactory = DeviceFactory(
        MetaDescriptor {
            value("capacity", ValueType.NUMBER) { required() }
            value("initialLevel", ValueType.NUMBER) { default = 0.0.asValue() }
        }
    ) { context, parameters ->

        val capacity = parameters["capacity"].double ?: error("Capacity parameter is required")

        val initialLevel = parameters["initialLevel"].double ?: 0.0

        ContinuousBuffer<U, T>(
            context = context,
            consumerAlgebra = algebra,
            bufferCapacity = ValueState(NumericAmount(capacity)),
            initialLevel = algebra.valueOf(initialLevel)
        )
    }

    public val mix: DeviceFactory = DeviceFactory(
        MetaDescriptor {
            value("supplyKeys", ValueType.LIST) { required() }
            enum("joinManagementStrategy".asName(), JoinManagementStrategy.PROPORTIONAL)
        }
    ) { context, parameters ->

        val supplyKeys = parameters["supplyKeys"].stringList ?: error("Supply keys parameter is required")

        val joinManagementStrategy =
            parameters["joinManagementStrategy"].enum<JoinManagementStrategy>() ?: JoinManagementStrategy.PROPORTIONAL

        ContinuousMix<U, T>(
            context = context,
            producerAlgebra = algebra,
            supplyKeys = supplyKeys,
            joinManagementStrategy = joinManagementStrategy,
        )
    }

    /**
     * A factory for flow model composition including flow bindings
     */
    public val flowModel: DeviceFactory = DeviceFactory(
        MetaDescriptor {
            node("models") {
                required()
                multiple = true
                description = "Models composed in this flowModel"
            }

            node("flowBindings", FlowBindingMetaSpec.descriptor.copy(multiple = true))
        }
    ) { context, parameters ->
        val deviceManager = context.request(DeviceManager)

        val modelConfiguration = parameters["models"] ?: error("Models parameter is required")

        val flowBindings = parameters["flowBindings"]


        object : ContinuousFlowModel(context) {
            init {
                val models = modelConfiguration.items.mapValues { (token, modelMeta) ->
                    installTree(token.toString(), deviceManager.createDeviceTree(modelMeta))
                }

                flowBindings?.items?.forEach { (token, bindingMeta) ->
                    val producerName = bindingMeta[FlowBindingMetaSpec.producer]?.let { Name.parse(it) }
                        ?: error("Producer is required for binding $token")
                    val consumerName = bindingMeta[FlowBindingMetaSpec.consumer]?.let { Name.parse(it) }
                        ?: error("Consumer is required for binding $token")

                    @Suppress("UNCHECKED_CAST")
                    val producerModel: ContinuousProducer<U, T> = when (producerName.length) {
                        1 -> models[producerName.first()] as? ContinuousProducerDevice<U, T>
                            ?: error("Producer is not a continuous producer device")

                        2 -> {
                            val model = models[producerName.first()] as? ContinuousMultiProducer<U, T>
                                ?: error("Producer is not a continuous multi producer device")

                            model.asProducer(producerName[1].toString())
                        }

                        else -> error("Producer name must have length 1 or 2")
                    }

                    @Suppress("UNCHECKED_CAST")
                    val consumerModel: ContinuousConsumer<U, T> = when (consumerName.length) {
                        1 -> models[consumerName.first()] as? ContinuousConsumer<U, T>
                            ?: error("Consumer is not a continuous consumer device")

                        2 -> {
                            val model = models[consumerName.first()] as? ContinuousMultiConsumer<U, T>
                                ?: error("Consumer is not a continuous multi consumer device")

                            model.asConsumer(consumerName[1].toString())
                        }

                        else -> error("Consumer name must have length 1 or 2")
                    }

                    connect(producerModel, consumerModel)
                }
            }
        }
    }


    override val factories: Map<String, DeviceTreeFactory> = mapOf(
        "producer" to producer,
        "buffer" to buffer,
        "mix" to mix,
        "flowModel" to flowModel,
    )

}

/**
 * A specification for flow binding meta for flow model composition
 */
public object FlowBindingMetaSpec : MetaSpec() {
    public val producer: MetaRef<String> by string()
    public val consumer: MetaRef<String> by string()
}