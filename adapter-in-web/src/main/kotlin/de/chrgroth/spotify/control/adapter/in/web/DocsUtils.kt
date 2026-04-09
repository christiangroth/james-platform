package de.chrgroth.spotify.control.adapter.`in`.web

internal object DocsUtils {

  fun readMarkdown(resourcePath: String): String? {
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
      ?: return null
    return stream.bufferedReader(Charsets.UTF_8).readText()
  }

  fun extractTitle(content: String, fallback: String): String =
    content.lineSequence()
      .firstOrNull { it.startsWith("# ") }
      ?.removePrefix("# ")
      ?.trim()
      ?: fallback
}
