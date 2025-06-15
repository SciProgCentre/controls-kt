package center.sciprog.controls.demo.thermo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.MutableMeta
import space.kscience.dataforge.meta.MutableMetaDelegate
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.NameToken
import space.kscience.dataforge.names.asName
import space.kscience.dataforge.names.plus
import space.kscience.visionforge.AbstractVision
import kotlin.reflect.KProperty

public fun <T> MutableMeta.mapOfConvertable(
    converter: MetaConverter<T>,
    key: Name? = null,
): MutableMetaDelegate<Map<String, T>> = object : MutableMetaDelegate<Map<String, T>> {
    override val descriptor: MetaDescriptor? = converter.descriptor?.copy(multiple = true)

    override fun getValue(thisRef: Any?, property: KProperty<*>): Map<String, T> {
        val prefix = key ?: property.name.asName()
        return get(prefix)?.items?.map { it.key.toString() to converter.read(it.value) }?.toMap() ?: emptyMap()
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Map<String, T>) {
        val prefix = key ?: property.name.asName()
        value.forEach { (key, value) ->
            set(prefix + NameToken.parse(key), converter.convert(value))
        }
    }
}


@Serializable
data class ThermoSensorVisionData(
    val temperature: Double,
    val status: ThermoSensorStatus,
)

@Serializable
@SerialName("controls.thermo")
class VisionOfThermoSensorHub : AbstractVision() {


    @OptIn(DFExperimental::class)
    var sensorData by properties.mapOfConvertable(
        //TODO replace by manual converter
        converter = MetaConverter.serializable<ThermoSensorVisionData>()
    )
}