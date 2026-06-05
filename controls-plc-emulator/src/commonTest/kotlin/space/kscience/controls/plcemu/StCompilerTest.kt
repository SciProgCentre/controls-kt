package space.kscience.controls.plcemu

import space.kscience.dataforge.meta.int
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StCompilerTest {

    @Test
    fun testSimpleAssignment() {
        val st = """
            PROGRAM Test
            VAR
                A : INT;
                B : INT := 10;
            END_VAR
            A := B + 5;
            END_PROGRAM
        """.trimIndent()

        val ilProject = StCompiler.compile(st)
        val program = ilProject.programs.find { it.name == "Test" }
        assertNotNull(program)

        assertEquals(2, program.variables.size)
        assertEquals("A", program.variables[0].name)
        assertEquals("B", program.variables[1].name)

        // Expected IL:
        // LD B
        // ADD 5
        // ST A

        val instructions = program.instructions
        assertEquals("LD", instructions[0].operator)
        assertEquals("B", (instructions[0].operand as IlOperand.Variable).name)
        assertEquals("ADD", instructions[1].operator)
        assertEquals(5, (instructions[1].operand as IlOperand.Constant).value.value?.int)
        assertEquals("ST", instructions[2].operator)
        assertEquals("A", (instructions[2].operand as IlOperand.Variable).name)
    }

    @Test
    fun testIfStatement() {
        val st = """
            PROGRAM Test
            VAR
                A : BOOL;
                B : INT;
            END_VAR
            IF A THEN
                B := 1;
            ELSE
                B := 2;
            END_IF;
            END_PROGRAM
        """.trimIndent()

        val ilProject = StCompiler.compile(st)
        val program = ilProject.programs.find { it.name == "Test" }
        assertNotNull(program)

        // Just verify it compiles and has some instructions
        assertTrue { program.instructions.isNotEmpty() }
    }

    @Test
    fun testFbCall() {
        val st = """
            FUNCTION_BLOCK MyFB
            VAR_INPUT
                IN1 : INT;
            END_VAR
            VAR_OUTPUT
                OUT1 : INT;
            END_VAR
            OUT1 := IN1 + 1;
            END_FUNCTION_BLOCK

            PROGRAM Main
            VAR
                FB1 : MyFB;
                Res : INT;
            END_VAR
            FB1(IN1 := 5);
            Res := FB1.OUT1;
            END_PROGRAM
        """.trimIndent()

        val ilProject = StCompiler.compile(st)
        val main = ilProject.programs.find { it.name == "Main" }
        assertNotNull(main)

        val instructions = main.instructions
        // Expected for FB1(IN1 := 5):
        // LD 5
        // ST FB1.IN1
        // CAL FB1

        val calIndex = instructions.indexOfFirst { it.operator == "CAL" }
        assertTrue { calIndex > 0 }
        assertEquals("FB1", (instructions[calIndex].operand as IlOperand.Variable).name)
        assertEquals("ST", instructions[calIndex - 1].operator)
        assertEquals("FB1.IN1", (instructions[calIndex - 1].operand as IlOperand.Variable).name)
    }
}
