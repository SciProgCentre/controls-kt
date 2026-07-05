package space.kscience.controls.demo

import space.kscience.controls.spec.DeviceBase
import space.kscience.controls.spec.DeviceWithStateFactory
import space.kscience.controls.spec.doubleProperty
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.int
import kotlin.random.Random

/**
 * A device that produces random numbers
 */
class RandomGeneratorDevice : DeviceWithStateFactory<Random>() {

    context(device: DeviceBase)
    override suspend fun createState(): Random {
        val seed = device.context.properties["seed"].int ?: 1
        return Random(seed)
    }

    val random by doubleProperty {
        nextDouble()
    }

}