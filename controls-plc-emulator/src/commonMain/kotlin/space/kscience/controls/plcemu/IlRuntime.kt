package space.kscience.controls.plcemu

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.asValue
import space.kscience.dataforge.meta.boolean
import space.kscience.dataforge.meta.double

/**
 * LLM generated code: IL Runtime implementation for IEC 61131-3 Instruction List emulator.
 * 
 * A runtime for executing [IlProgramBlock].
 */
public class IlRuntime(
    public val program: IlProgramBlock,
    public val scope: PlcState,
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

    private val localVariables = mutableMapOf<String, Meta>().apply {
        program.variables.forEach { v ->
            put(v.name, v.initialValue ?: Meta.EMPTY)
        }
    }

    private fun Meta.toBoolean(): Boolean = value?.boolean ?: error("Invalid boolean value in Meta: $this")
    private fun Meta.toDouble(): Double = value?.double ?: error("Invalid double value in Meta: $this")
    private fun Boolean.asMeta(): Meta = Meta(this.asValue())
    private fun Double.asMeta(): Meta = Meta(this.asValue())

    private suspend fun readOperand(operand: IlOperand?): Meta = when (operand) {
        is IlOperand.Constant -> operand.value
        is IlOperand.Variable -> {
            val name = operand.name
            localVariables[name] ?: scope.read(name)
        }
        is IlOperand.Label -> Meta(operand.label.asValue())
        null -> Meta.EMPTY
    }

    private suspend fun writeVariable(name: String, value: Meta) {
        if (name in localVariables) {
            localVariables[name] = value
        } else {
            scope.write(name, value)
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
            writeVariable(target, value)
        },
        "S" to { instr ->
            if (accumulator.toBoolean()) {
                val target = (instr.operand as? IlOperand.Variable)?.name ?: error("S requires a variable operand")
                writeVariable(target, true.asMeta())
            }
        },
        "R" to { instr ->
            if (accumulator.toBoolean()) {
                val target = (instr.operand as? IlOperand.Variable)?.name ?: error("R requires a variable operand")
                writeVariable(target, false.asMeta())
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
            val label = (instr.operand as? IlOperand.Variable)?.name
                ?: (instr.operand as? IlOperand.Label)?.label
                ?: error("JMP requires a label")
            programCounter = program.labels[label] ?: error("Label $label not found")
        },
        "CAL" to { instr ->
            val func = (instr.operand as? IlOperand.Variable)?.name ?: error("CAL requires a function name")
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
            kotlinx.coroutines.yield()
        }
    }
}

/**
 * A runner for managing multiple IL programs.
 */
public class IlRunner(
    public val scope: PlcState,
    public val project: IlProject,
    public val customOperators: Map<String, suspend IlRuntime.(IlInstruction) -> Unit> = emptyMap()
) {
    private val runningPrograms = mutableMapOf<String, Job>()

    /**
     * Start a program by name.
     */
    public fun start(programName: String): Job {
        val program = project.programs.find { it.name == programName } ?: error("Program $programName not found")
        val job = scope.launch {
            IlRuntime(program, scope, customOperators).run()
        }
        runningPrograms[programName] = job
        job.invokeOnCompletion { runningPrograms.remove(programName) }
        return job
    }

    /**
     * Stop a running program by name.
     */
    public fun stop(programName: String) {
        runningPrograms[programName]?.cancel("Stopped by runner")
    }

    /**
     * Check if a program is running.
     */
    public fun isRunning(programName: String): Boolean = runningPrograms.containsKey(programName)
}

/**
 * Launch an IL program block in a new coroutine Job.
 */
public fun PlcState.launchIl(
    program: IlProgramBlock,
    customOperators: Map<String, suspend IlRuntime.(IlInstruction) -> Unit> = emptyMap()
): Job = launch {
    IlRuntime(program, this@launchIl, customOperators).run()
}

/**
 * Parse and launch an IL project in a new coroutine Job (launches all programs).
 */
public fun PlcState.launchIl(
    source: String,
    customOperators: Map<String, suspend IlRuntime.(IlInstruction) -> Unit> = emptyMap()
): List<Job> {
    val project = IlParser.parseProject(source)
    return project.programs.map { program ->
        launchIl(program, customOperators)
    }
}
