package space.kscience.controls.proto.generation

public interface RustElement {
    public fun render(builder: StringBuilder, indent: String = "")
}

public class RustStruct(private val name: String) : RustElement {
    private val fields = mutableMapOf<String, String>()

    public fun field(name: String, type: String) {
        fields[name] = type
    }

    override fun render(builder: StringBuilder, indent: String) {
        builder.append("$indent#[derive(Debug, Default, Clone)]\n")
        builder.append("${indent}pub struct $name {\n")
        fields.forEach { (n, t) ->
            builder.append("$indent    pub $n: $t,\n")
        }
        builder.append("$indent}\n\n")
    }
}

public class RustImpl(private val structName: String) : RustElement {
    private val methods = mutableListOf<RustFunction>()

    public fun fn(
        name: String,
        args: String = "",
        returnType: String? = null,
        modifiers: String = "",
        visibility: String = "pub",
        init: RustFunction.() -> Unit,
    ) {
        val f = RustFunction(name, args, returnType, modifiers, visibility)
        f.init()
        methods.add(f)
    }

    override fun render(builder: StringBuilder, indent: String) {
        builder.append("${indent}impl $structName {\n")
        methods.forEach { it.render(builder, "$indent    ") }
        builder.append("$indent}\n\n")
    }
}

public class RustFunction(
    private val name: String,
    private val args: String = "",
    private val returnType: String? = null,
    private val modifiers: String = "",
    private val visibility: String = "pub",
) : RustElement {
    private val bodyLines = mutableListOf<String>()

    public fun line(code: String) {
        bodyLines.add(code)
    }

    public operator fun String.unaryPlus() {
        bodyLines.add(this)
    }

    public fun block(header: String, init: RustFunction.() -> Unit) {
        bodyLines.add("$header {")
        val nested = RustFunction("_nested")
        nested.init()
        nested.bodyLines.forEach { line ->
            bodyLines.add("    $line")
        }
        bodyLines.add("}")
    }

    public fun ifBlock(condition: String, init: RustFunction.() -> Unit) {
        block("if $condition", init)
    }

    public fun ifElseBlock(
        condition: String,
        ifInit: RustFunction.() -> Unit,
        elseInit: RustFunction.() -> Unit,
    ) {
        block("if $condition", ifInit)
        bodyLines.add("else {")
        val nestedElse = RustFunction("_nested_else")
        nestedElse.elseInit()
        nestedElse.bodyLines.forEach { line ->
            bodyLines.add("    $line")
        }
        bodyLines.add("}")
    }

    public fun matchBlock(expression: String, init: RustFunction.() -> Unit) {
        block("match $expression", init)
    }

    public fun arm(pattern: String, init: RustFunction.() -> Unit) {
        bodyLines.add("$pattern => {")
        val nested = RustFunction("_nested_arm")
        nested.init()
        nested.bodyLines.forEach { line ->
            bodyLines.add("    $line")
        }
        bodyLines.add("},")
    }

    override fun render(builder: StringBuilder, indent: String) {
        val ret = if (returnType != null) " -> $returnType" else ""
        val mod = if (modifiers.isNotEmpty()) "$modifiers " else ""
        val vis = if (visibility.isNotEmpty()) "$visibility " else ""
        builder.append("${indent}${vis}${mod}fn $name($args)$ret {\n")
        bodyLines.forEach { line ->
            builder.append("$indent    $line\n")
        }
        builder.append("$indent}\n\n")
    }
}

public class RustFile : RustElement {
    private val elements = mutableListOf<RustElement>()

    public fun use(crate: String) {
        elements.add(object : RustElement {
            override fun render(builder: StringBuilder, indent: String) {
                builder.append("${indent}use $crate;\n")
            }
        })
    }
    
    public fun custom(content: String) {
        elements.add(object : RustElement {
            override fun render(builder: StringBuilder, indent: String) {
                content.lines().forEach { builder.append("$indent$it\n") }
                builder.append("\n")
            }
        })
    }

    public operator fun String.unaryPlus() {
        elements.add(object : RustElement {
            override fun render(builder: StringBuilder, indent: String) {
                this@unaryPlus.lines().forEach { 
                    if (it.isNotBlank()) builder.append("$indent$it\n")
                    else builder.append("\n")
                }
            }
        })
    }
    
    public fun struct(name: String, init: RustStruct.() -> Unit) {
        val s = RustStruct(name)
        s.init()
        elements.add(s)
    }

    public fun impl(structName: String, init: RustImpl.() -> Unit) {
        val i = RustImpl(structName)
        i.init()
        elements.add(i)
    }

    public fun fn(
        name: String,
        args: String = "",
        returnType: String? = null,
        modifiers: String = "",
        visibility: String = "pub",
        init: RustFunction.() -> Unit,
    ) {
        val f = RustFunction(name, args, returnType, modifiers, visibility)
        f.init()
        elements.add(f)
    }

    override fun render(builder: StringBuilder, indent: String) {
        elements.forEach { it.render(builder, indent) }
    }
    
    override fun toString(): String {
        val sb = StringBuilder()
        render(sb)
        return sb.toString()
    }
}

public fun rustFile(init: RustFile.() -> Unit): RustFile {
    val file = RustFile()
    file.init()
    return file
}
