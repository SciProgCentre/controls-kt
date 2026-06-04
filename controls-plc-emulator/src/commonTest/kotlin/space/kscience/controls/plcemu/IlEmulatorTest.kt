package space.kscience.controls.plcemu

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.runTest
import space.kscience.controls.time.ClockManager
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.asValue
import space.kscience.dataforge.meta.boolean
import space.kscience.dataforge.meta.double
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

class IlEmulatorTest {

    class TestPlcEmulatorScope(override val context: Context) : PlcEmulatorScope {
        override val coroutineContext: CoroutineContext get() = context.coroutineContext
        val registers = mutableMapOf<String, Meta>()
        override val clockManager: ClockManager = context.request(ClockManager)

        override suspend fun read(identifier: String): Meta = registers[identifier] ?: Meta.EMPTY

        override suspend fun write(identifier: String, value: Meta) {
            registers[identifier] = value
        }

        override suspend fun call(identifier: String, arguments: Meta): Meta {
            return Meta.EMPTY
        }

        override fun subscribe(identifier: String): Flow<Meta> = TODO()
    }

    private val testContext = Context {
        plugin(ClockManager)
    }

    @Test
    fun testArithmetic() = runTest {
        val scope = TestPlcEmulatorScope(testContext)
        val source = """
            LD 10
            ADD 20
            MUL 2
            ST result
        """.trimIndent()
        
        scope.launchIl(source).join()
        
        assertEquals(60.0, scope.registers["result"]?.double)
    }

    @Test
    fun testLogic() = runTest {
        val scope = TestPlcEmulatorScope(testContext)
        val source = """
            LD true
            AND false
            ST res1
            LD true
            OR false
            ST res2
        """.trimIndent()

        scope.launchIl(source).join()

        assertEquals(false, scope.registers["res1"]?.value?.boolean)
        assertEquals(true, scope.registers["res2"]?.value?.boolean)
    }

    @Test
    fun testJumps() = runTest {
        val scope = TestPlcEmulatorScope(testContext)
        val source = """
            LD 0
            ST counter
            LOOP: LD counter
            ADD 1
            ST counter
            LT 10
            JMPC LOOP
        """.trimIndent()

        scope.launchIl(source).join()

        assertEquals(10.0, scope.registers["counter"]?.double)
    }

    @Test
    fun testCustomOperator() = runTest {
        val scope = TestPlcEmulatorScope(testContext)
        val source = """
            LD 10
            SQUARE
            ST result
        """.trimIndent()

        val customOps = mapOf<String, suspend IlRuntime.(IlInstruction) -> Unit>(
            "SQUARE" to {
                val v = accumulator.double ?: 0.0
                accumulator = Meta((v * v).asValue())
            }
        )

        scope.launchIl(source, customOps).join()

        assertEquals(100.0, scope.registers["result"]?.double)
    }

    @Test
    fun testMultiProgram() = runTest {
        val scope = TestPlcEmulatorScope(testContext)
        val source1 = "LD 1\nST res1"
        val source2 = "LD 2\nST res2"

        joinAll(scope.launchIl(source1), scope.launchIl(source2))

        assertEquals(1.0, scope.registers["res1"]?.double)
        assertEquals(2.0, scope.registers["res2"]?.double)
    }
}
