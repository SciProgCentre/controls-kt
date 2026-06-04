package space.kscience.controls.plcemu

import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name

/**
 * LLM generated code: IL models and AST for IEC 61131-3 Instruction List emulator.
 *
 * An operand for an IL instruction.
 */
public sealed interface IlOperand {
    /**
     * A variable or register reference.
     */
    public data class Variable(val name: Name) : IlOperand

    /**
     * A constant value.
     */
    public data class Constant(val value: Meta) : IlOperand

    /**
     * A label for jumps.
     */
    public data class Label(val label: String) : IlOperand
}

/**
 * A single IL instruction.
 */
public data class IlInstruction(
    val label: String?,
    val operator: String,
    val modifier: String?,
    val operand: IlOperand?
)

/**
 * A parsed IL program.
 */
public data class IlProgram(
    val instructions: List<IlInstruction>
) {
    /**
     * A map of labels to instruction indices.
     */
    val labels: Map<String, Int> = instructions.mapIndexedNotNull { index, instr ->
        instr.label?.let { it to index }
    }.toMap()
}
