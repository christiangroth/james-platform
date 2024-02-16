package de.chrgroth.gradle.plugins

import de.chrgroth.gradle.plugins.releasenotes.ReleasenoteSnippetType
import java.io.File
import java.util.concurrent.TimeUnit

fun String.replaceAll(placeholders: Map<ReleasenoteSnippetType, String>): String {
    var result = this
    placeholders.forEach { (key, value) -> result = result.replace("{${key.nextVersionReplacementVariableName}}", value) }
    return result
}

fun File.readOrNull() = if (canRead()) readText() else null
fun File.prepend(nextVersionText: String) {
    writeText(nextVersionText + readText())
}

fun File.createWithText(text: String) {
    parentFile.mkdirs()
    createNewFile()
    writeText(text)
}

fun runToString(workingDirectory: File, vararg cmd: String): String {
    return try {
        val proc = ProcessBuilder(cmd.toList())
            .directory(workingDirectory)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        proc.waitFor(5, TimeUnit.SECONDS)
        val errorOutput = String(proc.errorStream.use { it.readAllBytes() })
        if(errorOutput.isNotEmpty()) System.err.println(errorOutput)
        String(proc.inputStream.use { it.readAllBytes() })
    } catch (e: java.io.IOException) {
        e.printStackTrace()
        ""
    }
}
