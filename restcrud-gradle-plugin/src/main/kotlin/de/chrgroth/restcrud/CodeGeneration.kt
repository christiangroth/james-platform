package de.chrgroth.restcrud

import java.io.File
import java.nio.charset.StandardCharsets

class FileGenerator(private val generationRoot : File) {

    fun generateFile(folderPath: String, filename: String, content: String) {

        val folder = File(generationRoot, folderPath)
        folder.mkdirs()

        val file = File(folder, filename)
        file.writeText(content, StandardCharsets.UTF_8)
    }
}