package space.kscience.controls.constructor.models.continuous

import space.kscience.controls.api.DeviceFactory
import space.kscience.controls.api.DeviceTreeFactory
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.units.*
import space.kscience.controls.manager.DeviceLibrary
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.enum
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.stringList

public class ContinuousModelLibrary<U : UnitsOfMatter, T : Amount<U>>(
    public val algebra: AmountAlgebra<U, T>
) : DeviceLibrary {

    public val producer: DeviceFactory = DeviceFactory { context, parameters ->

        val productionCapacity = parameters["productionCapacity"].double
            ?: error("Production capacity parameter is required")

        ContinuousProducerDevice<U, T>(
            context = context,
            producerAlgebra = algebra,
            productionCapacity = ValueState(algebra.valueOf(productionCapacity).perSecond)
        )
    }

    public val buffer: DeviceFactory = DeviceFactory { context, parameters ->

        val capacity = parameters["capacity"].double ?: error("Capacity parameter is required")

        val initialLevel = parameters["initialLevel"].double ?: 0.0

        ContinuousBuffer<U, T>(
            context = context,
            consumerAlgebra = algebra,
            bufferCapacity = ValueState(NumericAmount(capacity)),
            initialLevel = algebra.valueOf(initialLevel)
        )
    }

    public val mix: DeviceFactory = DeviceFactory { context, parameters ->

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


    override val factories: Map<String, DeviceTreeFactory> = mapOf(
        "producer" to producer,
        "buffer" to buffer,
        "mix" to mix,
    )

}