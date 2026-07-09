package space.kscience.controls.plcemu

import space.kscience.dataforge.meta.Meta

/**
 * LLM generated code: IL models and AST for IEC 61131-3 Instruction List emulator.
 *
 * An operand for an IL instruction.
 */
public sealed interface IlOperand {
    /**
     * A variable or register reference.
     */
    public data class Variable(val name: String) : IlOperand

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
 * A variable declaration in an IL program.
 */
public data class IlVariableDefinition(
    val name: String,
    val type: String,
    val initialValue: Meta? = null
)

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
 * A named IL program block.
 */
public data class IlProgramBlock(
    val name: String,
    val variables: List<IlVariableDefinition>,
    val instructions: List<IlInstruction>
) {
    /**
     * A map of labels to instruction indices.
     */
    val labels: Map<String, Int> = instructions.mapIndexedNotNull { index, instr ->
        instr.label?.let { it to index }
    }.toMap()
}

/**
 * A parsed IL project containing multiple program blocks.
 */
public data class IlProject(
    val programs: List<IlProgramBlock>
)
