package space.kscience.controls.plcemu

import me.alllex.parsus.parser.*
import me.alllex.parsus.token.regexToken
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.asValue

/**
 * LLM generated code: IL Parser implementation using Parsus library.
 * 
 * A parser for IEC 61131-3 Instruction List (IL).
 */
public object IlParser : Grammar<IlProject>() {
    private val lineBreak by regexToken("(\\r?\\n)+", ignored = true)
    private val ws by regexToken("[ \\t]+", ignored = true)
    private val comment by regexToken("//.*|/\\*.*?\\*/", ignored = true)

    private val PROGRAM_KW by regexToken("PROGRAM", ignoreCase = true)
    private val END_PROGRAM_KW by regexToken("END_PROGRAM", ignoreCase = true)
    private val VAR_KW by regexToken("VAR", ignoreCase = true)
    private val END_VAR_KW by regexToken("END_VAR", ignoreCase = true)
    
    private val ASSIGN by regexToken(":=")
    private val COLON by regexToken(":")
    private val SEMICOLON by regexToken(";")

    private val booleanToken by regexToken("(true|false)", ignoreCase = true)
    private val numberToken by regexToken("-?\\d+(\\.\\d+)?")
    private val stringToken by regexToken("\"[^\"]*\"")
    private val labelToken by regexToken("[a-zA-Z_][a-zA-Z0-9_]*:")
    private val identifierToken by regexToken("[a-zA-Z_][a-zA-Z0-9_.]*")

    private val constantParser: Parser<Meta> by (booleanToken map { Meta(it.text.lowercase().toBoolean().asValue()) }) or
            (numberToken map { Meta(it.text.toDoubleOrNull()?.asValue() ?: it.text.toInt().asValue()) }) or
            (stringToken map { Meta(it.text.removeSurrounding("\"").asValue()) })

    private val operandParser: Parser<IlOperand> by (constantParser map { IlOperand.Constant(it) }) or
            (identifierToken map { IlOperand.Variable(it.text) })

    private val variableDeclaration: Parser<IlVariableDefinition> by parser {
        val name = identifierToken().text
        COLON()
        val type = identifierToken().text
        val initialValue = optional(parser {
            ASSIGN()
            constantParser()
        })()
        optional(SEMICOLON)()
        IlVariableDefinition(name, type, initialValue)
    }

    private val varBlock: Parser<List<IlVariableDefinition>> by parser {
        VAR_KW()
        val vars = zeroOrMore(variableDeclaration)()
        END_VAR_KW()
        vars
    }

    private val instructionParser: Parser<IlInstruction> by parser {
        val op = identifierToken().text

        val (baseOp, mod) = when {
            op.endsWith("CN") && op.length > 2 -> op.dropLast(2) to "CN"
            op.endsWith("C") && op.length > 1 -> op.dropLast(1) to "C"
            op.endsWith("N") && op.length > 1 -> op.dropLast(1) to "N"
            else -> op to null
        }

        val arg = optional(operandParser)()
        IlInstruction(null, baseOp, mod, arg)
    }

    private val programBlock: Parser<IlProgramBlock> by parser {
        PROGRAM_KW()
        val name = identifierToken().text
        val variables = mutableListOf<IlVariableDefinition>()
        val instructions = mutableListOf<IlInstruction>()
        var nextLabel: String? = null
        
        while (true) {
            if (poll(END_PROGRAM_KW) != null) break
            
            val vars = poll(varBlock)
            if (vars != null) {
                variables.addAll(vars)
                continue
            }
            
            val label = poll(labelToken)
            if (label != null) {
                nextLabel = label.text.removeSuffix(":")
                continue
            }
            
            val instr = poll(instructionParser)
            if (instr != null) {
                instructions.add(instr.copy(label = nextLabel))
                nextLabel = null
                continue
            }
            
            // If we reached here, we are stuck
            error("Unexpected token at ${currentOffset}")
        }
        IlProgramBlock(name, variables, instructions)
    }

    override val root: Parser<IlProject> by parser {
        val programs = zeroOrMore(programBlock)()
        IlProject(programs)
    }

    /**
     * Parse an IL project from string.
     */
    public fun parseProject(text: String): IlProject = parse(text).getOrThrow()
}
