package space.kscience.controls.constructor.models.flow

import space.kscience.controls.constructor.units.Amount

public object Connections {
    public fun <T : Amount<*>> connect(
        producer: ContinuousProducer<T>,
        consumer: ContinuousConsumer<T>,
    ) {
        producer.connectConsumer(consumer.capacity)
        consumer.connectProducer(producer.capacity)
    }
}