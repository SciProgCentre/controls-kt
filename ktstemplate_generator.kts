/**
 * # Generating files from templates script
 *
 * ## Using
 *
 * * Create file contains `.ktstemplate` in its name
 * * Write variables **before** the first H* (starting with `#`) section
 * * Launch `kotlin ${thisscriptname}` with required args
 *
 * ### File sample
 *
 * ```markdown
 * first = hello
 * second = world
 *
 * # Sample
 *
 * This is sample of $first $second
 * ```
 *
 * Will have next result:
 *
 * ```markdown
 * # Sample
 *
 * This is sample of hello world
 * ```
 *
 * ## Launch
 *
 * This script accept next args:
 *
 * `[...paths] [--plain] [--recursive]`
 *
 * Where:
 *
 * * `...paths` - Paths to the files-templates or folders with files-templates
 * * `--plain` - will look only into the folders from `paths`
 * * `--recursive` - (default) will look into the folders from `paths` and recursively in subfolders
 */
import kotlin.collections.LinkedHashSet
import java.io.File

val templateEnding = Regex("\\.ktstemplate(\\.[^\\.]*)*$")
val templateOnlyEnding = Regex("\\.ktstemplate")
val singleArgumentRegex = Regex("^[\\w\\d]+$")
val splitterRegex = Regex("[ ]*=[ ]*")

sealed interface Mode {
    fun filesList(folder: File): Sequence<File>

    data object Recursive : Mode {
        override fun filesList(folder: File): Sequence<File> {
            return sequence {
                val folders = mutableListOf<File>()
                folders.add(folder)
                while (folders.isNotEmpty()) {
                    val currentFolder = folders.removeAt(0)
                    currentFolder.listFiles().toList().forEach {
                        when {
                            it.isFile -> yield(it)
                            it.isDirectory -> folders.add(it)
                        }
                    }
                }
            }
        }
    }

    data object Plain : Mode {
        override fun filesList(folder: File): Sequence<File> {
            return sequence {
                folder.listFiles().forEach {
                    yield(it)
                }
            }
        }
    }
}
var mode: Mode = Mode.Recursive

val folders = args.mapNotNull {
    if (it.startsWith("-")) { // assume some arg
        when (it) {
            "--plain" -> mode = Mode.Plain
            "--recursive" -> mode = Mode.Recursive
            "--help" -> {
                println("[...pathnames] [--recursive] [--plain]")
                println("...pathnames - Pass any count of folder or files paths")
                println("--recursive - (default) Use recursive visiting of folders for each path in pathnames")
                println("--plain - (default) Use plain (non-recursive) visiting of folders for each path in pathnames")
            }
        }
        null
    } else {
        File(it)
    }
}.ifEmpty {
    listOf(File("./"))
}

fun String.replaceVariables(variables: Map<String, String>): String {
    var currentLine = this
    variables.forEach { (k, v) ->
        currentLine = currentLine.replace("\${${k}}", v)
        if (k.matches(singleArgumentRegex)) {
            currentLine = currentLine.replace("\$${k}", v)
        }
    }
    return currentLine
}

fun generateFromTemplate(folder: File, file: File) {
    val targetFile = File(folder, file.name.replace(templateOnlyEnding, ""))

    val variables = mutableMapOf<String, String>()
    var writeVariables = true
    var text = ""

    file.readLines().forEach { line ->
        when {
            writeVariables && line.startsWith("#") -> {
                writeVariables = false
            }
            writeVariables -> {
                val splitted = line.split(splitterRegex)
                if (splitted.size > 1) {
                    val k = splitted[0]
                    val v = splitted[1].replaceVariables(variables)
                    variables[k] = v
                }
                return@forEach
            }
        }
        text += line.let {
            line.replaceVariables(variables) + "\n"
        }
    }
    targetFile.writeText(text)
    println("${targetFile.absolutePath} has been recreated")
}

if (args.none { it == "--help" }) {
    folders.forEach { folder ->
        mode.filesList(folder).forEach { file ->
            if (file.name.contains(templateEnding)) {
                generateFromTemplate(file.parentFile, file)
            }
        }
    }
}
