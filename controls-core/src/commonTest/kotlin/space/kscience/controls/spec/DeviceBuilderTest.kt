package space.kscience.controls.spec

import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.meta.string
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// LLM generated code: Unit tests for DeviceBuilder verifying readable, writeable, and logical property access.
internal class DeviceBuilderTest {

    @Test
    fun testPropertyAccess() = runTest {
        val context = Context("DeviceBuilderTest")

        val readableSpec = DevicePropertySpec(
            MetaConverter.int,
            PropertyDescriptor("readableProp", readable = true, mutable = false)
        )
        val writeableSpec = DevicePropertySpec(
            MetaConverter.string,
            PropertyDescriptor("writeableProp", readable = true, mutable = true)
        )
        val logicalSpec = DevicePropertySpec(
            MetaConverter.int,
            PropertyDescriptor("logicalProp", readable = true, mutable = true)
        )

        val readableValue = 42
        var writeableValue = "initial"

        val device = Device(context) {
            reader(readableSpec) { readableValue }
            reader(writeableSpec) { writeableValue }
            writer(writeableSpec) { writeableValue = it }
            logical(logicalSpec, default = 10)
        }

        // 1. Readable property: accessible for read, throws exception on write
        assertEquals(42, device.read(readableSpec))
        assertFailsWith<IllegalStateException> {
            device.write(readableSpec, 100)
        }

        // 2. Writeable property: accessible for read and write
        assertEquals("initial", device.read(writeableSpec))
        device.write(writeableSpec, "updated")
        assertEquals("updated", device.read(writeableSpec))
        assertEquals("updated", writeableValue)

        // 3. Logical property: accessible for read and write
        assertEquals(10, device.read(logicalSpec))
        device.write(logicalSpec, 20)
        assertEquals(20, device.read(logicalSpec))

        context.close()
    }
}
