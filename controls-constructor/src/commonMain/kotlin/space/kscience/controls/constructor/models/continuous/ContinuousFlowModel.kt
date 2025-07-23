package space.kscience.controls.constructor.models.continuous

import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.constructor.LateBindDeviceState
import space.kscience.controls.constructor.ModelConstructor
import space.kscience.controls.constructor.model
import space.kscience.controls.constructor.units.Amount
import space.kscience.controls.constructor.units.Numeric
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

public fun <U : UnitsOfMeasurement> ContinuousFlowModel.producer(
    capacity: DeviceState<Numeric<U>>,
    supplyRequest: LateBindDeviceState<Numeric<U>> = LateBindDeviceState(Numeric(0))
): ContinuousProducer<U, Numeric<U>> = model(ContinuousProducer(context, capacity, supplyRequest))

public fun <U : UnitsOfMeasurement> ContinuousFlowModel.consumer(
    capacity: DeviceState<Numeric<U>>,
    supplyRequest: LateBindDeviceState<Numeric<U>> = LateBindDeviceState(Numeric(0))
): ContinuousConsumer<U, Numeric<U>> = model(ContinuousConsumer(context, capacity, supplyRequest))