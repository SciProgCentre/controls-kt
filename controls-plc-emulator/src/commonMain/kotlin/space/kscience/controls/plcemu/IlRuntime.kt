package space.kscience.controls.plcemu

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.asValue
import space.kscience.dataforge.meta.boolean
import space.kscience.dataforge.meta.double

/**
 * LLM generated code: IL Runtime implementation for IEC 61131-3 Instruction List emulator.
 * 
 * A runtime for executing [IlProgram].
 */
public class IlRuntime(
    public val program: IlProgram,
    public val scope: PlcEmulatorScope,
    public val customOperators: Map<String, suspend IlRuntime.(IlInstruction) -> Unit> = emptyMap()
) {
    /**
     * The primary register (Accumulator).
     */
    public var accumulator: Meta = Meta.EMPTY
        internal set

    /**
     * The Program Counter (index of the next instruction).
     */
    public var programCounter: Int = 0
        internal set

    private var isFinished = false

    private fun Meta.toBoolean(): Boolean = value?.boolean ?: false
    private fun Meta.toDouble(): Double = value?.double ?: 0.0
    private fun Boolean.asMeta(): Meta = Meta(this)
    private fun Double.asMeta(): Meta = Meta(this)

    private suspend fun readOperand(operand: IlOperand?): Meta {
        return when (operand) {
            is IlOperand.Constant -> operand.value
            is IlOperand.Variable -> scope.read(operand.name.toString())
            is IlOperand.Label -> Meta(operand.label.asValue())
            null -> Meta.EMPTY
        }
    }

    private val standardOperators = mapOf<String, suspend IlRuntime.(IlInstruction) -> Unit>(
        "LD" to { instr ->
            val value = readOperand(instr.operand)
            accumulator = if (instr.modifier == "N") (!value.toBoolean()).asMeta() else value
        },
        "ST" to { instr ->
            val target = (instr.operand as? IlOperand.Variable)?.name ?: error("ST requires a variable operand")
            val value = if (instr.modifier == "N") (!accumulator.toBoolean()).asMeta() else accumulator
            scope.write(target.toString(), value)
        },
        "S" to { instr ->
            if (accumulator.toBoolean()) {
                val target = (instr.operand as? IlOperand.Variable)?.name ?: error("S requires a variable operand")
                scope.write(target.toString(), true.asMeta())
            }
        },
        "R" to { instr ->
            if (accumulator.toBoolean()) {
                val target = (instr.operand as? IlOperand.Variable)?.name ?: error("R requires a variable operand")
                scope.write(target.toString(), false.asMeta())
            }
        },
        "AND" to { instr ->
            val value = readOperand(instr.operand)
            val operandBool = if (instr.modifier == "N") !value.toBoolean() else value.toBoolean()
            accumulator = (accumulator.toBoolean() && operandBool).asMeta()
        },
        "OR" to { instr ->
            val value = readOperand(instr.operand)
            val operandBool = if (instr.modifier == "N") !value.toBoolean() else value.toBoolean()
            accumulator = (accumulator.toBoolean() || operandBool).asMeta()
        },
        "XOR" to { instr ->
            val value = readOperand(instr.operand)
            val operandBool = if (instr.modifier == "N") !value.toBoolean() else value.toBoolean()
            accumulator = (accumulator.toBoolean() xor operandBool).asMeta()
        },
        "ADD" to { instr ->
            val value = readOperand(instr.operand)
            accumulator = (accumulator.toDouble() + value.toDouble()).asMeta()
        },
        "SUB" to { instr ->
            val value = readOperand(instr.operand)
            accumulator = (accumulator.toDouble() - value.toDouble()).asMeta()
        },
        "MUL" to { instr ->
            val value = readOperand(instr.operand)
            accumulator = (accumulator.toDouble() * value.toDouble()).asMeta()
        },
        "DIV" to { instr ->
            val value = readOperand(instr.operand)
            accumulator = (accumulator.toDouble() / value.toDouble()).asMeta()
        },
        "GT" to { instr ->
            val value = readOperand(instr.operand)
            accumulator = (accumulator.toDouble() > value.toDouble()).asMeta()
        },
        "GE" to { instr ->
            val value = readOperand(instr.operand)
            accumulator = (accumulator.toDouble() >= value.toDouble()).asMeta()
        },
        "EQ" to { instr ->
            val value = readOperand(instr.operand)
            accumulator = (accumulator == value).asMeta()
        },
        "NE" to { instr ->
            val value = readOperand(instr.operand)
            accumulator = (accumulator != value).asMeta()
        },
        "LE" to { instr ->
            val value = readOperand(instr.operand)
            accumulator = (accumulator.toDouble() <= value.toDouble()).asMeta()
        },
        "LT" to { instr ->
            val value = readOperand(instr.operand)
            accumulator = (accumulator.toDouble() < value.toDouble()).asMeta()
        },
        "JMP" to { instr ->
            val label = (instr.operand as? IlOperand.Variable)?.name?.toString()
                ?: (instr.operand as? IlOperand.Label)?.label
                ?: error("JMP requires a label")
            programCounter = program.labels[label] ?: error("Label $label not found")
        },
        "CAL" to { instr ->
            val func = (instr.operand as? IlOperand.Variable)?.name?.toString() ?: error("CAL requires a function name")
            accumulator = scope.call(func, accumulator)
        },
        "RET" to { _ ->
            isFinished = true
        }
    )

    /**
     * Execute a single instruction.
     */
    public suspend fun runStep() {
        if (isFinished || programCounter >= program.instructions.size) {
            isFinished = true
            return
        }
        val instr = program.instructions[programCounter]

        // Conditional execution for C/CN modifiers
        val shouldExecute = when (instr.modifier) {
            "C" -> accumulator.toBoolean()
            "CN" -> !accumulator.toBoolean()
            else -> true
        }

        if (shouldExecute) {
            val op = instr.operator.uppercase()
            val handler = customOperators[op] ?: standardOperators[op] ?: error("Unknown operator $op")

            val oldPC = programCounter
            handler(instr)

            // If the handler didn't change PC, increment it
            if (programCounter == oldPC) {
                programCounter++
            }
        } else {
            programCounter++
        }
    }

    /**
     * Execute the program until it finishes.
     */
    public suspend fun run() {
        while (!isFinished) {
            runStep()
        }
    }
}

/**
 * Launch an IL program in a new coroutine Job.
 */
public fun PlcEmulatorScope.launchIl(
    program: IlProgram,
    customOperators: Map<String, suspend IlRuntime.(IlInstruction) -> Unit> = emptyMap()
): Job = launch {
    IlRuntime(program, this@launchIl, customOperators).run()
}

/**
 * Parse and launch an IL program in a new coroutine Job.
 */
public fun PlcEmulatorScope.launchIl(
    source: String,
    customOperators: Map<String, suspend IlRuntime.(IlInstruction) -> Unit> = emptyMap()
): Job = launch {
    val program = IlParser.parseProgram(source)
    IlRuntime(program, this@launchIl, customOperators).run()
}
