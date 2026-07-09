package space.kscience.controls.models

import space.kscience.controls.constructor.states
import space.kscience.controls.constructor.units.Kilograms
import space.kscience.controls.constructor.units.kilograms
import space.kscience.controls.models.continuous.ContinuousBuffer
import space.kscience.dataforge.context.Context
import kotlin.test.Test

class ModelDescriptorTest {

    @Test
    fun testBufferDescriptors(){
        val context = Context("test") { }
        val buffer = ContinuousBuffer<Kilograms>(context, Kilograms, 100.kilograms)
        buffer.states.forEach { (key, state) -> println("$key: $state") }
    }
}