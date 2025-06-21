package space.kscience.controls.constructor.models.flow

import space.kscience.controls.constructor.units.UnitsOfMeasurement

public object Connections {
    public fun <U : UnitsOfMeasurement> connect(
        producer: ContinuousProducer<U>,
        consumer: ContinuousConsumer<U>,
    ){
        producer.connectConsumer(consumer.capacity)
        consumer.connectProducer(producer.capacity)
    }

}