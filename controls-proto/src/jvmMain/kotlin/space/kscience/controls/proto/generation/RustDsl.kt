package space.kscience.controls.proto.generation

interface RustElement {
    fun render(builder: StringBuilder, indent: String = "")
}

class RustStruct(private val name: String) : RustElement {
    private val fields = mutableMapOf<String, String>()

    fun field(name: String, type: String) {
        fields[name] = type
    }

    override fun render(builder: StringBuilder, indent: String) {
        builder.append("$indent#[derive(Debug, Default)]\n")
        builder.append("${indent}pub struct $name {\n")
        fields.forEach { (n, t) ->
            builder.append("$indent    pub $n: $t,\n")
        }
        builder.append("$indent}\n\n")
    }
}

class RustImpl(private val structName: String) : RustElement {
    private val methods = mutableListOf<RustFunction>()

    fun fn(name: String, args: String = "", returnType: String? = null, modifiers: String = "", init: RustFunction.() -> Unit) {
        val f = RustFunction(name, args, returnType, modifiers)
        f.init()
        methods.add(f)
    }

    override fun render(builder: StringBuilder, indent: String) {
        builder.append("${indent}impl $structName {\n")
        methods.forEach { it.render(builder, "$indent    ") }
        builder.append("$indent}\n\n")
    }
}

class RustFunction(
    private val name: String,
    private val args: String = "",
    private val returnType: String? = null,
    private val modifiers: String = ""
) : RustElement {
    private val bodyLines = mutableListOf<String>()

    operator fun String.unaryPlus() {
        bodyLines.add(this)
    }

    override fun render(builder: StringBuilder, indent: String) {
        val ret = if (returnType != null) " -> $returnType" else ""
        val mod = if (modifiers.isNotEmpty()) "$modifiers " else ""
        builder.append("${indent}pub ${mod}fn $name($args)$ret {\n")
        bodyLines.forEach { line ->
            builder.append("$indent    $line\n")
        }
        builder.append("$indent}\n\n")
    }
}

class RustFile : RustElement {
    private val elements = mutableListOf<RustElement>()

    fun use(crate: String) {
        elements.add(object : RustElement {
            override fun render(builder: StringBuilder, indent: String) {
                builder.append("${indent}use $crate;\n")
            }
        })
    }
    
    fun custom(content: String) {
        elements.add(object : RustElement {
            override fun render(builder: StringBuilder, indent: String) {
                content.lines().forEach { builder.append("$indent$it\n") }
                builder.append("\n")
            }
        })
    }

    operator fun String.unaryPlus() {
        elements.add(object : RustElement {
            override fun render(builder: StringBuilder, indent: String) {
                this@unaryPlus.lines().forEach { 
                    if (it.isNotBlank()) builder.append("$indent$it\n")
                    else builder.append("\n")
                }
            }
        })
    }
    
    fun struct(name: String, init: RustStruct.() -> Unit) {
        val s = RustStruct(name)
        s.init()
        elements.add(s)
    }

    fun impl(structName: String, init: RustImpl.() -> Unit) {
        val i = RustImpl(structName)
        i.init()
        elements.add(i)
    }

    fun fn(name: String, args: String = "", returnType: String? = null, modifiers: String = "", init: RustFunction.() -> Unit) {
        val f = RustFunction(name, args, returnType, modifiers)
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

fun rustFile(init: RustFile.() -> Unit): RustFile {
    val file = RustFile()
    file.init()
    return file
}
