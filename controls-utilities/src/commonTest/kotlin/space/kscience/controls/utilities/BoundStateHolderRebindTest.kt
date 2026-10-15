@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package space.kscience.controls.utilities

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import space.kscience.controls.constructor.BoundStateHolder
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.expressions.ComputationDevice
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.kmath.expressions.Expression
import space.kscience.kmath.expressions.Symbol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class BoundStateHolderRebindTest {
    private class Source(value: Double, second: Long) : ValueState<Meta> {
        private val samples = MutableStateFlow(sample(value, second))
        override val valueWithTime: ValueWithTime<Meta> get() = samples.value
        override fun subscribeWithTime() = samples
        override fun toString(): String = "Source($valueWithTime)"

        fun emit(value: Double, second: Long) {
            samples.value = sample(value, second)
        }

        companion object {
            fun sample(value: Double, second: Long) =
                ValueWithTime(MetaConverter.double.convert(value), Instant.fromEpochSeconds(second))
        }
    }

    @Test
    fun builtinHoldersRebindThroughTheirPublicInterface() = runTest(timeout = 5.seconds) {
        val context = Context {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val computation = ComputationDevice(
                context, setOf("x"), Expression { it.getValue(Symbol("x")) }
            )
            val alarm = Alarm(context)
            val accumulator = Accumulator(context, 10.seconds, backgroundScope)
            val inputs: List<Triple<BoundStateHolder, ValueState<Double?>, List<String>>> = listOf(
                Triple(computation, computation.result, listOf("x")),
                Triple(alarm, alarm.value, listOf("", "value")),
                Triple(accumulator, accumulator.value, listOf("", "value")),
            )
            for ((holder, input, names) in inputs) {
                val first = Source(1.0, 10)
                val second = Source(3.0, 30)
                names.forEach { holder.rebind(first, it) }
                assertEquals(1.0, input.value)
                val samples = mutableListOf<ValueWithTime<Double?>>()
                val job = backgroundScope.launch { input.subscribeWithTime().toList(samples) }
                runCurrent()

                assertFailsWith<IllegalStateException> { holder.rebind(second, "missing") }
                assertEquals(1.0, input.value)
                first.emit(2.0, 20)
                runCurrent()
                holder.rebind(second, names.last())
                runCurrent()
                assertFailsWith<IllegalStateException> { holder.bind(first, names.first()) }
                assertEquals(3.0, input.value)

                first.emit(99.0, 99)
                runCurrent()
                assertEquals(3.0, samples.last().value)
                second.emit(4.0, 40)
                runCurrent()
                assertEquals(listOf<Double?>(1.0, 2.0, 3.0, 4.0), samples.map { it.value })
                assertEquals(listOf(10L, 20L, 30L, 40L).map { Instant.fromEpochSeconds(it) }, samples.map { it.time })
                job.cancelAndJoin()
            }
        } finally {
            context.close()
        }
    }

    @Test
    fun unsupportedHolderDoesNotSilentlyBind() {
        var bindings = 0
        val holder = object : BoundStateHolder {
            override fun bind(state: ValueState<Meta>, inputName: String) {
                bindings++
            }
        }
        assertFailsWith<UnsupportedOperationException> {
            holder.rebind(Source(1.0, 10))
        }
        assertEquals(0, bindings)
    }
}
