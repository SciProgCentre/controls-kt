package space.kscience.controls.plcemu

import me.alllex.parsus.parser.*
import me.alllex.parsus.token.literalToken
import me.alllex.parsus.token.regexToken
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.asValue

/*
 * LLM generated code: ST AST, Parser, and Compiler for IEC 61131-3 Structured Text.
 */

// --- AST ---

public enum class StVariableScope {
    VAR, VAR_INPUT, VAR_OUTPUT, VAR_IN_OUT
}

public data class StVariableDeclaration(
    val name: String,
    val type: String,
    val initialValue: Meta? = null,
    val scope: StVariableScope = StVariableScope.VAR
)

public sealed interface StExpression
public data class StLiteral(val value: Meta) : StExpression
public data class StVariableReference(val name: String) : StExpression
public data class StBinaryExpression(val left: StExpression, val operator: String, val right: StExpression) : StExpression
public data class StUnaryExpression(val operator: String, val expression: StExpression) : StExpression
public data class StFunctionCall(val functionName: String, val arguments: List<StExpression>) : StExpression

public sealed interface StStatement
public data class StAssignment(val target: String, val value: StExpression) : StStatement
public data class StIf(
    val condition: StExpression,
    val thenBranch: List<StStatement>,
    val elseIfBranches: List<Pair<StExpression, List<StStatement>>> = emptyList(),
    val elseBranch: List<StStatement>? = null
) : StStatement

public data class StCaseBranch(val values: List<StExpression>, val body: List<StStatement>)
public data class StCase(
    val expression: StExpression,
    val cases: List<StCaseBranch>,
    val elseBranch: List<StStatement>? = null
) : StStatement

public data class StFor(
    val variable: String,
    val start: StExpression,
    val end: StExpression,
    val step: StExpression? = null,
    val body: List<StStatement>
) : StStatement

public data class StWhile(val condition: StExpression, val body: List<StStatement>) : StStatement
public data class StRepeat(val body: List<StStatement>, val condition: StExpression) : StStatement
public data class StFbCall(val instanceName: String, val arguments: Map<String, StExpression>) : StStatement

public sealed interface StPou {
    public val name: String
    public val variables: List<StVariableDeclaration>
    public val body: List<StStatement>
}

public data class StProgram(
    override val name: String,
    override val variables: List<StVariableDeclaration>,
    override val body: List<StStatement>
) : StPou

public data class StFunction(
    override val name: String,
    override val variables: List<StVariableDeclaration>,
    override val body: List<StStatement>,
    val returnType: String
) : StPou

public data class StFunctionBlock(
    override val name: String,
    override val variables: List<StVariableDeclaration>,
    override val body: List<StStatement>
) : StPou

public data class StProject(val pous: List<StPou>)

// --- Parser ---

public object StParser : Grammar<StProject>() {
    init {
        // Ignore case for all keywords
    }

    private val lineBreak by regexToken("(\\r?\\n)+", ignored = true)
    private val ws by regexToken("[ \\t]+", ignored = true)
    private val comment by regexToken("//.*|/\\*.*?\\*/|\\(\\*.*?\\*\\)", ignored = true)

    private val PROGRAM_KW by regexToken("PROGRAM", ignoreCase = true)
    private val END_PROGRAM_KW by regexToken("END_PROGRAM", ignoreCase = true)
    private val FUNCTION_KW by regexToken("FUNCTION", ignoreCase = true)
    private val END_FUNCTION_KW by regexToken("END_FUNCTION", ignoreCase = true)
    private val FUNCTION_BLOCK_KW by regexToken("FUNCTION_BLOCK", ignoreCase = true)
    private val END_FUNCTION_BLOCK_KW by regexToken("END_FUNCTION_BLOCK", ignoreCase = true)

    private val VAR_KW by regexToken("VAR", ignoreCase = true)
    private val VAR_INPUT_KW by regexToken("VAR_INPUT", ignoreCase = true)
    private val VAR_OUTPUT_KW by regexToken("VAR_OUTPUT", ignoreCase = true)
    private val VAR_IN_OUT_KW by regexToken("VAR_IN_OUT", ignoreCase = true)
    private val END_VAR_KW by regexToken("END_VAR", ignoreCase = true)

    private val IF_KW by regexToken("IF", ignoreCase = true)
    private val THEN_KW by regexToken("THEN", ignoreCase = true)
    private val ELSIF_KW by regexToken("ELSIF", ignoreCase = true)
    private val ELSE_KW by regexToken("ELSE", ignoreCase = true)
    private val END_IF_KW by regexToken("END_IF", ignoreCase = true)

    private val CASE_KW by regexToken("CASE", ignoreCase = true)
    private val OF_KW by regexToken("OF", ignoreCase = true)
    private val END_CASE_KW by regexToken("END_CASE", ignoreCase = true)

    private val FOR_KW by regexToken("FOR", ignoreCase = true)
    private val TO_KW by regexToken("TO", ignoreCase = true)
    private val BY_KW by regexToken("BY", ignoreCase = true)
    private val DO_KW by regexToken("DO", ignoreCase = true)
    private val END_FOR_KW by regexToken("END_FOR", ignoreCase = true)

    private val WHILE_KW by regexToken("WHILE", ignoreCase = true)
    private val END_WHILE_KW by regexToken("END_WHILE", ignoreCase = true)

    private val REPEAT_KW by regexToken("REPEAT", ignoreCase = true)
    private val UNTIL_KW by regexToken("UNTIL", ignoreCase = true)
    private val END_REPEAT_KW by regexToken("END_REPEAT", ignoreCase = true)

    private val ASSIGN by literalToken(":=")
    private val COLON by literalToken(":")
    private val SEMICOLON by literalToken(";")
    private val COMMA by literalToken(",")
    private val LPAREN by literalToken("(")
    private val RPAREN by literalToken(")")
    private val DOT by literalToken(".")

    private val OR_KW by regexToken("OR", ignoreCase = true)
    private val XOR_KW by regexToken("XOR", ignoreCase = true)
    private val AND_KW by regexToken("AND", ignoreCase = true)
    private val NOT_KW by regexToken("NOT", ignoreCase = true)

    private val EQ by literalToken("=")
    private val NEQ by literalToken("<>")
    private val LE by literalToken("<=")
    private val GE by literalToken(">=")
    private val LT by literalToken("<")
    private val GT by literalToken(">")

    private val PLUS by literalToken("+")
    private val MINUS by literalToken("-")
    private val MUL by literalToken("*")
    private val DIV by literalToken("/")
    private val MOD_KW by regexToken("MOD", ignoreCase = true)
    private val POW by literalToken("**")

    private val TRUE_KW by regexToken("TRUE", ignoreCase = true)
    private val FALSE_KW by regexToken("FALSE", ignoreCase = true)

    private val numberToken by regexToken("-?\\d+(\\.\\d+)?")
    private val stringToken by regexToken("'[^']*'|\"[^\"]*\"")
    private val identifierToken by regexToken("[a-zA-Z_][a-zA-Z0-9_.]*")

    // --- Expression Parsing with Precedence ---

    private val primaryExpression: Parser<StExpression> by
        (TRUE_KW map { StLiteral(Meta(true.asValue())) }) or
        (FALSE_KW map { StLiteral(Meta(false.asValue())) }) or
        (numberToken map { StLiteral(Meta(it.text.toIntOrNull()?.asValue() ?: it.text.toDouble().asValue())) }) or
        (stringToken map { StLiteral(Meta(it.text.substring(1, it.text.length - 1).asValue())) }) or
        parser {
            val id = identifierToken().text
            val args = optional(parser {
                LPAREN()
                val a = separated(ref(::expression), COMMA)()
                RPAREN()
                a
            })()
            if (args != null) StFunctionCall(id, args) else StVariableReference(id)
        } or
        parser {
            LPAREN()
            val e = expression()
            RPAREN()
            e
        }

    private val unaryExpression: Parser<StExpression> by
        (NOT_KW * ref(::unaryExpression) map { (_, e) -> StUnaryExpression("NOT", e) }) or
        (MINUS * ref(::unaryExpression) map { (_, e) -> StUnaryExpression("-", e) }) or
        primaryExpression

    private val powerExpression: Parser<StExpression> by leftAssociative(unaryExpression, POW) { l, _, r -> StBinaryExpression(l, "**", r) }
    private val mulExpression: Parser<StExpression> by leftAssociative(powerExpression, (MUL or DIV or MOD_KW) map { it.text.uppercase() }) { l, op, r -> StBinaryExpression(l, op, r) }
    private val addExpression: Parser<StExpression> by leftAssociative(mulExpression, (PLUS or MINUS) map { it.text }) { l, op, r -> StBinaryExpression(l, op, r) }
    private val comparisonExpression: Parser<StExpression> by leftAssociative(addExpression, (LE or GE or LT or GT or EQ or NEQ) map { it.text }) { l, op, r -> StBinaryExpression(l, op, r) }
    private val andExpression: Parser<StExpression> by leftAssociative(comparisonExpression, AND_KW) { l, _, r -> StBinaryExpression(l, "AND", r) }
    private val xorExpression: Parser<StExpression> by leftAssociative(andExpression, XOR_KW) { l, _, r -> StBinaryExpression(l, "XOR", r) }
    private val expression: Parser<StExpression> by leftAssociative(xorExpression, OR_KW) { l, _, r -> StBinaryExpression(l, "OR", r) }

    // --- Statement Parsing ---

    private val assignmentStatement: Parser<StAssignment> by parser {
        val target = identifierToken().text
        ASSIGN()
        val value = expression()
        SEMICOLON()
        StAssignment(target, value)
    }

    private val fbCallStatement: Parser<StFbCall> by parser {
        val instance = identifierToken().text
        LPAREN()
        val args = optional(separated(parser {
            val name = identifierToken().text
            ASSIGN()
            val value = expression()
            name to value
        }, COMMA))()
        RPAREN()
        SEMICOLON()
        StFbCall(instance, (args ?: emptyList<Pair<String, StExpression>>()).toMap())
    }

    private val ifStatement: Parser<StIf> by parser {
        IF_KW()
        val cond = expression()
        THEN_KW()
        val thenB = zeroOrMore(statement)()
        val elseIfs = zeroOrMore(parser {
            ELSIF_KW()
            val c = expression()
            THEN_KW()
            val b = zeroOrMore(statement)()
            c to b
        })()
        val elseB = optional(parser {
            ELSE_KW()
            zeroOrMore(statement)()
        })()
        END_IF_KW()
        SEMICOLON()
        StIf(cond, thenB, elseIfs, elseB)
    }

    private val caseStatement: Parser<StCase> by parser {
        CASE_KW()
        val expr = expression()
        OF_KW()
        val branches = zeroOrMore(parser {
            val values = separated(expression, COMMA)()
            COLON()
            val body = zeroOrMore(statement)()
            StCaseBranch(values, body)
        })()
        val elseB = optional(parser {
            ELSE_KW()
            zeroOrMore(statement)()
        })()
        END_CASE_KW()
        SEMICOLON()
        StCase(expr, branches, elseB)
    }

    private val forStatement: Parser<StFor> by parser {
        FOR_KW()
        val v = identifierToken().text
        ASSIGN()
        val start = expression()
        TO_KW()
        val end = expression()
        val step = optional(parser {
            BY_KW()
            expression()
        })()
        DO_KW()
        val body = zeroOrMore(statement)()
        END_FOR_KW()
        SEMICOLON()
        StFor(v, start, end, step, body)
    }

    private val whileStatement: Parser<StWhile> by parser {
        WHILE_KW()
        val cond = expression()
        DO_KW()
        val body = zeroOrMore(statement)()
        END_WHILE_KW()
        SEMICOLON()
        StWhile(cond, body)
    }

    private val repeatStatement: Parser<StRepeat> by parser {
        REPEAT_KW()
        val body = zeroOrMore(statement)()
        UNTIL_KW()
        val cond = expression()
        END_REPEAT_KW()
        SEMICOLON()
        StRepeat(body, cond)
    }

    private val statement: Parser<StStatement> by
        ifStatement or caseStatement or forStatement or whileStatement or repeatStatement or
        fbCallStatement or assignmentStatement

    // --- Declaration Parsing ---

    private val varScope: Parser<StVariableScope> by
        (VAR_INPUT_KW map { StVariableScope.VAR_INPUT }) or
        (VAR_OUTPUT_KW map { StVariableScope.VAR_OUTPUT }) or
        (VAR_IN_OUT_KW map { StVariableScope.VAR_IN_OUT }) or
        (VAR_KW map { StVariableScope.VAR })

    private val varDeclaration: Parser<List<StVariableDeclaration>> by parser {
        val scope = varScope()
        val decls = zeroOrMore(parser {
            val name = identifierToken().text
            COLON()
            val type = identifierToken().text
            val init = optional(parser {
                ASSIGN()
                // Simplification: only literals in initial values for now
                (primaryExpression map { (it as? StLiteral)?.value ?: error("Only literals allowed in initial values") })()
            })()
            SEMICOLON()
            StVariableDeclaration(name, type, init, scope)
        })()
        END_VAR_KW()
        decls
    }

    // --- POU Parsing ---

    private val programPou: Parser<StProgram> by parser {
        PROGRAM_KW()
        val name = identifierToken().text
        val vars = mutableListOf<StVariableDeclaration>()
        while (true) {
            val v = poll(varDeclaration)
            if (v != null) vars.addAll(v) else break
        }
        val body = zeroOrMore(statement)()
        END_PROGRAM_KW()
        StProgram(name, vars, body)
    }

    private val functionPou: Parser<StFunction> by parser {
        FUNCTION_KW()
        val name = identifierToken().text
        COLON()
        val retType = identifierToken().text
        val vars = mutableListOf<StVariableDeclaration>()
        while (true) {
            val v = poll(varDeclaration)
            if (v != null) vars.addAll(v) else break
        }
        val body = zeroOrMore(statement)()
        END_FUNCTION_KW()
        StFunction(name, vars, body, retType)
    }

    private val functionBlockPou: Parser<StFunctionBlock> by parser {
        FUNCTION_BLOCK_KW()
        val name = identifierToken().text
        val vars = mutableListOf<StVariableDeclaration>()
        while (true) {
            val v = poll(varDeclaration)
            if (v != null) vars.addAll(v) else break
        }
        val body = zeroOrMore(statement)()
        END_FUNCTION_BLOCK_KW()
        StFunctionBlock(name, vars, body)
    }

    override val root: Parser<StProject> by parser {
        val pous = zeroOrMore(programPou or functionPou or functionBlockPou)()
        StProject(pous)
    }
}

// --- Compiler ---

public class StCompiler {
    private var labelCounter = 0

    private fun nextLabel(prefix: String = "L"): String = "${prefix}${labelCounter++}"

    private val tempVariables = mutableListOf<IlVariableDefinition>()

    private fun nextTemp(type: String = "ANY"): String {
        val name = "__temp_${tempVariables.size}"
        tempVariables.add(IlVariableDefinition(name, type))
        return name
    }

    public fun compile(project: StProject): IlProject {
        return IlProject(project.pous.map { compilePou(it) })
    }

    private fun compilePou(pou: StPou): IlProgramBlock {
        tempVariables.clear()
        val instructions = mutableListOf<IlInstruction>()
        pou.body.forEach { instructions.addAll(compileStatement(it)) }

        val extraVars = mutableListOf<IlVariableDefinition>()
        if (pou is StFunction) {
            instructions.add(IlInstruction(null, "LD", null, IlOperand.Variable(pou.name)))
            extraVars.add(IlVariableDefinition(pou.name, pou.returnType))
        }

        val variables = pou.variables.map {
            IlVariableDefinition(it.name, it.type, it.initialValue)
        } + tempVariables + extraVars

        return IlProgramBlock(pou.name, variables, instructions)
    }

    private fun compileStatement(stmt: StStatement): List<IlInstruction> = when (stmt) {
        is StAssignment -> {
            compileExpression(stmt.value) + IlInstruction(null, "ST", null, IlOperand.Variable(stmt.target))
        }
        is StIf -> {
            val instructions = mutableListOf<IlInstruction>()
            val endLabel = nextLabel("END_IF")
            
            // Initial IF
            instructions.addAll(compileExpression(stmt.condition))
            val nextBranchLabel = if (stmt.elseIfBranches.isNotEmpty() || stmt.elseBranch != null) nextLabel("ELSE") else endLabel
            instructions.add(IlInstruction(null, "JMPCN", null, IlOperand.Label(nextBranchLabel)))
            stmt.thenBranch.forEach { instructions.addAll(compileStatement(it)) }
            if (nextBranchLabel != endLabel) {
                instructions.add(IlInstruction(null, "JMP", null, IlOperand.Label(endLabel)))
            }
            
            // ELSIFs
            var currentLabel = nextBranchLabel
            stmt.elseIfBranches.forEachIndexed { index, (cond, body) ->
                instructions.add(IlInstruction(currentLabel, "LD", null, null)) // Placeholder to attach label
                instructions.addAll(compileExpression(cond))
                val nextLabel = if (index < stmt.elseIfBranches.size - 1 || stmt.elseBranch != null) nextLabel("ELSE") else endLabel
                instructions.add(IlInstruction(null, "JMPCN", null, IlOperand.Label(nextLabel)))
                body.forEach { instructions.addAll(compileStatement(it)) }
                instructions.add(IlInstruction(null, "JMP", null, IlOperand.Label(endLabel)))
                currentLabel = nextLabel
            }
            
            // ELSE
            if (stmt.elseBranch != null) {
                instructions.add(IlInstruction(currentLabel, "LD", null, null)) // Placeholder
                stmt.elseBranch.forEach { instructions.addAll(compileStatement(it)) }
            } else if (currentLabel != endLabel) {
                instructions.add(IlInstruction(currentLabel, "LD", null, null)) // Placeholder
            }
            
            instructions.add(IlInstruction(endLabel, "LD", null, null)) // Final end label placeholder
            instructions
        }
        is StCase -> {
            val instructions = mutableListOf<IlInstruction>()
            val endLabel = nextLabel("END_CASE")
            val tempVar = nextTemp() // Type ANY or we could try to infer
            
            instructions.addAll(compileExpression(stmt.expression))
            instructions.add(IlInstruction(null, "ST", null, IlOperand.Variable(tempVar)))
            
            stmt.cases.forEach { branch ->
                val nextBranchLabel = nextLabel("CASE_BRANCH")
                val skipBranchLabel = nextLabel("SKIP_BRANCH")
                
                branch.values.forEach { valueExpr ->
                    instructions.add(IlInstruction(null, "LD", null, IlOperand.Variable(tempVar)))
                    val valInstructions = compileExpression(valueExpr)
                    if (valInstructions.size == 1 && valInstructions[0].operator == "LD" && valInstructions[0].operand != null) {
                        instructions.add(IlInstruction(null, "EQ", null, valInstructions[0].operand))
                    } else {
                        instructions.add(IlInstruction(null, "EQ", "(", null))
                        instructions.addAll(valInstructions)
                        instructions.add(IlInstruction(null, ")", null, null))
                    }
                    instructions.add(IlInstruction(null, "JMPC", null, IlOperand.Label(nextBranchLabel)))
                }
                instructions.add(IlInstruction(null, "JMP", null, IlOperand.Label(skipBranchLabel)))
                
                instructions.add(IlInstruction(nextBranchLabel, "LD", null, null))
                branch.body.forEach { instructions.addAll(compileStatement(it)) }
                instructions.add(IlInstruction(null, "JMP", null, IlOperand.Label(endLabel)))
                
                instructions.add(IlInstruction(skipBranchLabel, "LD", null, null))
            }

            stmt.elseBranch?.forEach { instructions.addAll(compileStatement(it)) }
            
            instructions.add(IlInstruction(endLabel, "LD", null, null))
            instructions
        }
        is StWhile -> {
            val startLabel = nextLabel("WHILE_START")
            val endLabel = nextLabel("WHILE_END")
            val instructions = mutableListOf<IlInstruction>()
            
            instructions.add(IlInstruction(startLabel, "LD", null, null)) // Just to hold the label
            instructions.addAll(compileExpression(stmt.condition))
            instructions.add(IlInstruction(null, "JMPCN", null, IlOperand.Label(endLabel)))
            stmt.body.forEach { instructions.addAll(compileStatement(it)) }
            instructions.add(IlInstruction(null, "JMP", null, IlOperand.Label(startLabel)))
            instructions.add(IlInstruction(endLabel, "LD", null, null))
            
            instructions
        }
        is StRepeat -> {
            val startLabel = nextLabel("REPEAT_START")
            val instructions = mutableListOf<IlInstruction>()
            
            instructions.add(IlInstruction(startLabel, "LD", null, null))
            stmt.body.forEach { instructions.addAll(compileStatement(it)) }
            instructions.addAll(compileExpression(stmt.condition))
            instructions.add(IlInstruction(null, "JMPCN", null, IlOperand.Label(startLabel)))
            
            instructions
        }
        is StFor -> {
            val startLabel = nextLabel("FOR_START")
            val endLabel = nextLabel("FOR_END")
            val instructions = mutableListOf<IlInstruction>()
            val endTemp = nextTemp()
            
            // i := start
            instructions.addAll(compileExpression(stmt.start))
            instructions.add(IlInstruction(null, "ST", null, IlOperand.Variable(stmt.variable)))
            
            // endTemp := end
            instructions.addAll(compileExpression(stmt.end))
            instructions.add(IlInstruction(null, "ST", null, IlOperand.Variable(endTemp)))
            
            instructions.add(IlInstruction(startLabel, "LD", null, IlOperand.Variable(stmt.variable)))
            instructions.add(IlInstruction(null, "LE", null, IlOperand.Variable(endTemp)))
            
            instructions.add(IlInstruction(null, "JMPCN", null, IlOperand.Label(endLabel)))
            
            stmt.body.forEach { instructions.addAll(compileStatement(it)) }
            
            // i := i + step
            instructions.add(IlInstruction(null, "LD", null, IlOperand.Variable(stmt.variable)))
            if (stmt.step != null) {
                val stepInstructions = compileExpression(stmt.step)
                if (stepInstructions.size == 1 && stepInstructions[0].operator == "LD" && stepInstructions[0].operand != null) {
                    instructions.add(IlInstruction(null, "ADD", null, stepInstructions[0].operand))
                } else {
                    instructions.add(IlInstruction(null, "ADD", "(", null))
                    instructions.addAll(stepInstructions)
                    instructions.add(IlInstruction(null, ")", null, null))
                }
            } else {
                instructions.add(IlInstruction(null, "ADD", null, IlOperand.Constant(Meta(1.asValue()))))
            }
            instructions.add(IlInstruction(null, "ST", null, IlOperand.Variable(stmt.variable)))
            instructions.add(IlInstruction(null, "JMP", null, IlOperand.Label(startLabel)))
            instructions.add(IlInstruction(endLabel, "LD", null, null))
            
            instructions
        }
        is StFbCall -> {
            // CAL MyInstance(Arg1 := Val1, ...)
            // In many IL dialects, arguments are loaded before CAL or passed in some way.
            // The spec says: "Calling a FUNCTION_BLOCK MUST involve a CAL instruction targeting the specific instance's data."
            val instructions = mutableListOf<IlInstruction>()
            stmt.arguments.forEach { (name, expr) ->
                instructions.addAll(compileExpression(expr))
                instructions.add(IlInstruction(null, "ST", null, IlOperand.Variable("${stmt.instanceName}.$name")))
            }
            instructions.add(IlInstruction(null, "CAL", null, IlOperand.Variable(stmt.instanceName)))
            instructions
        }
        else -> emptyList()
    }

    private fun compileExpression(expr: StExpression): List<IlInstruction> = when (expr) {
        is StLiteral -> listOf(IlInstruction(null, "LD", null, IlOperand.Constant(expr.value)))
        is StVariableReference -> listOf(IlInstruction(null, "LD", null, IlOperand.Variable(expr.name)))
        is StUnaryExpression -> {
            val op = when (expr.operator) {
                "NOT" -> "NOT"
                "-" -> "NEG" // or similar
                else -> expr.operator
            }
            compileExpression(expr.expression) + IlInstruction(null, op, null, null)
        }
        is StBinaryExpression -> {
            // Simple decomposition for left-associative operators: (A op B) op C
            // LD A, op B, op C
            // If right side is complex, we use parentheses modifier
            val leftInstrs = compileExpression(expr.left)
            val rightInstrs = compileExpression(expr.right)
            
            if (rightInstrs.size == 1 && rightInstrs[0].operator == "LD" && rightInstrs[0].operand != null) {
                val op = mapOperator(expr.operator)
                leftInstrs + IlInstruction(null, op, null, rightInstrs[0].operand)
            } else {
                val op = mapOperator(expr.operator)
                leftInstrs + IlInstruction(null, op, "(", null) + rightInstrs + IlInstruction(null, ")", null, null)
            }
        }
        is StFunctionCall -> {
            // LD Arg1, Func Arg2, Arg3...
            val instructions = mutableListOf<IlInstruction>()
            if (expr.arguments.isNotEmpty()) {
                instructions.addAll(compileExpression(expr.arguments[0]))
                for (i in 1 until expr.arguments.size) {
                    val argInstructions = compileExpression(expr.arguments[i])
                    if (argInstructions.size == 1 && argInstructions[0].operator == "LD" && argInstructions[0].operand != null) {
                        instructions.add(IlInstruction(null, expr.functionName, null, argInstructions[0].operand))
                    } else {
                        instructions.add(IlInstruction(null, expr.functionName, "(", null))
                        instructions.addAll(argInstructions)
                        instructions.add(IlInstruction(null, ")", null, null))
                    }
                }
            } else {
                instructions.add(IlInstruction(null, expr.functionName, null, null))
            }
            instructions
        }
    }

    private fun mapOperator(stOp: String): String = when (stOp) {
        "+" -> "ADD"
        "-" -> "SUB"
        "*" -> "MUL"
        "/" -> "DIV"
        "MOD" -> "MOD"
        "**" -> "POW"
        "AND" -> "AND"
        "OR" -> "OR"
        "XOR" -> "XOR"
        "=" -> "EQ"
        "<>" -> "NE"
        "<" -> "LT"
        ">" -> "GT"
        "<=" -> "LE"
        ">=" -> "GE"
        else -> stOp
    }

    public companion object {
        public fun compile(stSource: String): IlProject {
            val project = StParser.parse(stSource).getOrThrow()
            return StCompiler().compile(project)
        }
    }
}
