package space.kscience.controls

import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.Null
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.descriptors.validate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class NullableConverterTest {
    private val converter = MetaConverter.double.nullable()

    @Test
    fun testNullRoundTrip() {
        val meta = converter.convert(null)
        assertEquals(Meta(Null), meta)
        assertNull(converter.read(meta))
        assertNull(converter.readOrNull(meta))
    }

    @Test
    fun testNonNullRoundTrip() {
        val meta = converter.convert(1.5)
        assertEquals(1.5, converter.read(meta))
        assertEquals(1.5, converter.readOrNull(meta))
    }

    @Test
    fun testMalformedInput() {
        val meta = Meta("abc")
        assertNull(converter.readOrNull(meta))
        assertFailsWith<IllegalStateException> {
            converter.read(meta)
        }
    }

    @Test
    fun testDescriptorAllowsNull() {
        val descriptor = assertNotNull(converter.descriptor)
        assertEquals(listOf(ValueType.NUMBER, ValueType.NULL), descriptor.valueTypes)
        assertTrue(descriptor.validate(Meta(Null)))
        assertTrue(descriptor.validate(Meta(1.5)))
        assertFalse(descriptor.validate(Meta("abc")))
    }

    @Test
    fun testDescriptorDoesNotDuplicateNull() {
        val base = object : MetaConverter<Double> by MetaConverter.double {
            override val descriptor: MetaDescriptor = MetaDescriptor(
                valueTypes = listOf(ValueType.NUMBER, ValueType.NULL)
            )
        }
        assertEquals(
            listOf(ValueType.NUMBER, ValueType.NULL),
            base.nullable().descriptor?.valueTypes
        )
    }

    @Test
    fun testDescriptorPreservesUnrestrictedTypes() {
        val base = object : MetaConverter<Double> by MetaConverter.double {
            override val descriptor: MetaDescriptor = MetaDescriptor()
        }
        val descriptor = assertNotNull(base.nullable().descriptor)
        assertNull(descriptor.valueTypes)
        assertTrue(descriptor.validate(Meta(Null)))
        assertTrue(descriptor.validate(Meta(1.5)))
    }
}
