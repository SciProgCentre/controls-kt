package space.kscience.controls.plcemu

import me.alllex.parsus.parser.*
import me.alllex.parsus.token.regexToken
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.asValue
import space.kscience.dataforge.names.parseAsName

/**
 * LLM generated code: IL Parser implementation using Parsus library.
 * 
 * A parser for IEC 61131-3 Instruction List (IL).
 */
public object IlParser : Grammar<IlProgram>() {
    private val lineBreak by regexToken("(\\r?\\n)+")
    private val ws by regexToken("[ \\t]+", ignored = true)
    private val comment by regexToken("//.*|/\\*.*?\\*/", ignored = true)

    private val labelToken by regexToken("[a-zA-Z_][a-zA-Z0-9_]*:")
    private val booleanToken by regexToken("true|false")
    private val operatorToken by regexToken("[a-zA-Z]+")
    private val identifierToken by regexToken("[a-zA-Z_][a-zA-Z0-9_.]*")
    private val numberToken by regexToken("-?\\d+(\\.\\d+)?")
    private val stringToken by regexToken("\"[^\"]*\"")

    private val operandParser: Parser<IlOperand> by (booleanToken map { IlOperand.Constant(Meta(it.text.toBoolean().asValue())) }) or
            (identifierToken map { IlOperand.Variable(it.text.parseAsName()) }) or
            (numberToken map { IlOperand.Constant(Meta(it.text.toDoubleOrNull()?.asValue() ?: it.text.toInt().asValue())) }) or
            (stringToken map { IlOperand.Constant(Meta(it.text.removeSurrounding("\"").asValue())) })

    private val instructionParser: Parser<IlInstruction> by parser {
        val label = optional(labelToken)()?.text?.removeSuffix(":")
        val opWithMod = operatorToken().text

        val (op, mod) = when {
            opWithMod.endsWith("CN") && opWithMod.length > 2 -> opWithMod.dropLast(2) to "CN"
            opWithMod.endsWith("C") && opWithMod.length > 1 -> opWithMod.dropLast(1) to "C"
            opWithMod.endsWith("N") && opWithMod.length > 1 -> opWithMod.dropLast(1) to "N"
            else -> opWithMod to null
        }

        val arg = optional(operandParser)()
        IlInstruction(label, op, mod, arg)
    }

    override val root: Parser<IlProgram> by parser {
        val list = mutableListOf<IlInstruction>()
        while (true) {
            optional(lineBreak)()
            val instr = poll(instructionParser) ?: break
            list.add(instr)
        }
        IlProgram(list)
    }

    /**
     * Parse an IL program from string.
     */
    public fun parseProgram(text: String): IlProgram = parse(text).getOrThrow()
}
