package space.kscience.controls.plcemu

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import space.kscience.controls.time.ClockManager
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.double
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class IlEmulatorTest {

    class TestPlcEmulatorScope(override val context: Context) : PlcEmulatorScope {
        override val coroutineContext: CoroutineContext get() = context.coroutineContext
        val registers = mutableMapOf<String, Meta>()
        val calls = mutableListOf<Pair<String, Meta>>()
        override val clockManager: ClockManager = context.request(ClockManager)

        override suspend fun read(identifier: String): Meta = registers[identifier] ?: Meta.EMPTY

        override suspend fun write(identifier: String, value: Meta) {
            registers[identifier] = value
        }

        override suspend fun call(identifier: String, arguments: Meta): Meta {
            calls.add(identifier to arguments)
            return Meta.EMPTY
        }

        override fun subscribe(identifier: String): Flow<Meta> = TODO()
    }

    private val testContext = Context {
        plugin(ClockManager)
    }

    @Test
    fun testMinimal() = runTest {
        val source = "PROGRAM MyProg\nEND_PROGRAM"
        val project = IlParser.parseProject(source)
        assertEquals(1, project.programs.size)
    }

    @Test
    fun testFullProgramWithVariables() = runTest {
        val scope = TestPlcEmulatorScope(testContext)
        val source = """
            PROGRAM MyProg
              VAR
                A : INT := 10;
                B : INT := 20;
                Res : INT;
              END_VAR
              LD A
              ADD B
              ST Res
              ST GlobalRes
            END_PROGRAM
        """.trimIndent()
        
        val project = IlParser.parseProject(source)
        val runner = IlRunner(scope, project)
        runner.start("MyProg").join()
        
        // GlobalRes should be in scope.registers, but Res should NOT (it's local)
        assertEquals(30.0, scope.registers["GlobalRes"]?.double)
        assertTrue("Res" !in scope.registers)
    }

    @Test
    fun testRunnerStartStop() = runTest(timeout = 1.seconds) {
        val scope = TestPlcEmulatorScope(testContext)
        val source = """
            PROGRAM Infinite
              VAR
                Counter : INT := 0;
              END_VAR
              LOOP: LD Counter
              ADD 1
              ST Counter
              ST GlobalCounter
              JMP LOOP
            END_PROGRAM
        """.trimIndent()

        val project = IlParser.parseProject(source)
        val runner = IlRunner(scope, project)
        val job = runner.start("Infinite")
        
        // Let it run for a bit (in virtual time/test dispatcher)
        // Since it's a tight loop without delays, it might run many iterations instantly
        
        runner.stop("Infinite")
        job.join()
        
        val count = scope.registers["GlobalCounter"]?.double ?: 0.0
        assertTrue(count > 0)
        assertTrue(!runner.isRunning("Infinite"))
    }

    @Test
    fun testMultiplePrograms() = runTest {
        val scope = TestPlcEmulatorScope(testContext)
        val source = """
            PROGRAM Prog1
              LD 1
              ST Res1
            END_PROGRAM
            
            PROGRAM Prog2
              LD 2
              ST Res2
            END_PROGRAM
        """.trimIndent()

        val project = IlParser.parseProject(source)
        val runner = IlRunner(scope, project)
        
        runner.start("Prog1").join()
        runner.start("Prog2").join()

        assertEquals(1.0, scope.registers["Res1"]?.double)
        assertEquals(2.0, scope.registers["Res2"]?.double)
    }

}
