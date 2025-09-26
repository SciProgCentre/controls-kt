package space.kscience.controls.constructor.models.continuous

import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.constructor.ModelConstructor
import space.kscience.controls.constructor.units.Amount
import space.kscience.controls.constructor.units.UnitsOfMeasurement
import space.kscience.dataforge.context.Context

public abstract class ContinuousFlowModel(
    context: Context,
    vararg dependencies: DeviceState<*>
) : ModelConstructor(context, *dependencies) {

    public companion object {
        public fun <U : UnitsOfMeasurement, T : Amount<U>> connect(
            producer: ContinuousProducerInterface<U, T>,
            consumer: ContinuousConsumerInterface<U, T>,
        ) {
            producer.connectConsumer(consumer.consumationCapacity)
            consumer.connectProducer(producer.productionCapacity)
        }
    }
}