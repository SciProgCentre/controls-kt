package space.kscience.controls.constructor.models.continuous

import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.units.Amount
import space.kscience.controls.constructor.units.UnitsOfMatter
import space.kscience.dataforge.context.Context

/**
 *  A class for continuous flow model composition
 */
public abstract class ContinuousFlowModel(
    context: Context,
) : DeviceConstructor(context) {

    public companion object {
        public fun <U : UnitsOfMatter, T : Amount<U>> connect(
            producer: ContinuousProducer<U, T>,
            consumer: ContinuousConsumer<U, T>,
        ) {
            producer.connectConsumer(consumer.consumationCapacity)
            consumer.connectProducer(producer.productionCapacity)
        }
    }
}

public interface ContinuousFlowComponent