package de.chrgroth.james.platform.domain.imports

import com.fasterxml.jackson.databind.JsonNode
import de.chrgroth.james.platform.domain.model.imports.DataPath

/**
 * Detects JSON paths pointing to arrays that contain exclusively objects. Only object fields are descended into;
 * arrays are never descended into, matching found or not, since a dot-separated path cannot address array elements.
 */
object DataPathDetector {

  private const val PATH_SEPARATOR = "."

  fun detect(root: JsonNode): List<DataPath> {
    val results = mutableListOf<DataPath>()
    walk(root, emptyList(), results)
    return results
  }

  fun resolve(root: JsonNode, path: String): DataPath? {
    if (path.isBlank()) {
      return null
    }

    var current = root
    for (segment in path.split(PATH_SEPARATOR)) {
      if (segment.isBlank() || !current.isObject) {
        return null
      }
      current = current.get(segment) ?: return null
    }
    return current.asDataPathOrNull(path)
  }

  private fun walk(node: JsonNode, path: List<String>, results: MutableList<DataPath>) {
    node.properties().forEach { (key, value) ->
      val currentPath = path + key
      val dataPath = value.asDataPathOrNull(currentPath.joinToString(PATH_SEPARATOR))
      if (dataPath != null) {
        results += dataPath
      } else if (value.isObject) {
        walk(value, currentPath, results)
      }
    }
  }

  private fun JsonNode.asDataPathOrNull(path: String): DataPath? =
    if (isArray && size() > 0 && all { it.isObject }) DataPath(path, size()) else null
}
