package space.kscience.controls.constructor.models.continuous

import space.kscience.controls.constructor.ModelConstructor
import space.kscience.controls.constructor.units.Amount
import space.kscience.controls.constructor.units.UnitsOfMatter
import space.kscience.dataforge.context.Context

public abstract class ContinuousFlowModel(
    context: Context,
) : ModelConstructor(context) {

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