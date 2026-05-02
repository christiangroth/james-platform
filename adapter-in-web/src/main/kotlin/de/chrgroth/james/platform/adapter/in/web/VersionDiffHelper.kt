package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint

enum class DiffLineType { ADDED, REMOVED, UNCHANGED }

enum class DiffChangeType { ADDED, REMOVED, MODIFIED, UNCHANGED }

data class DiffLine(val content: String, val type: DiffLineType)

data class EntityDiffView(
  val name: String,
  val changeType: DiffChangeType,
  val lines: List<DiffLine>,
)

data class ReportDiffView(
  val name: String,
  val changeType: DiffChangeType,
  val htmlLines: List<DiffLine>,
  val scriptLines: List<DiffLine>,
)

data class VersionDiffView(
  val entityDiffs: List<EntityDiffView>,
  val reportDiffs: List<ReportDiffView>,
) {
  val hasChanges: Boolean get() = entityDiffs.any { it.changeType != DiffChangeType.UNCHANGED } ||
    reportDiffs.any { it.changeType != DiffChangeType.UNCHANGED }
}

object VersionDiffHelper {

  fun computeDiff(predecessor: AppVersion, current: AppVersion): VersionDiffView {
    val entityDiffs = computeEntityDiffs(predecessor, current)
    val reportDiffs = computeReportDiffs(predecessor, current)
    return VersionDiffView(entityDiffs, reportDiffs)
  }

  private fun computeEntityDiffs(predecessor: AppVersion, current: AppVersion): List<EntityDiffView> {
    val result = mutableListOf<EntityDiffView>()
    val predecessorById = predecessor.entityDefinitions.associateBy { it.id }
    val currentById = current.entityDefinitions.associateBy { it.id }

    // Removed entities (in predecessor but not in current)
    for (entity in predecessor.entityDefinitions) {
      if (entity.id !in currentById) {
        result += EntityDiffView(
          name = entity.name,
          changeType = DiffChangeType.REMOVED,
          lines = entity.toDslLines().map { DiffLine(it, DiffLineType.REMOVED) },
        )
      }
    }

    // Added entities (in current but not in predecessor)
    for (entity in current.entityDefinitions) {
      if (entity.id !in predecessorById) {
        result += EntityDiffView(
          name = entity.name,
          changeType = DiffChangeType.ADDED,
          lines = entity.toDslLines().map { DiffLine(it, DiffLineType.ADDED) },
        )
      }
    }

    // Potentially modified entities (in both)
    for (entity in current.entityDefinitions) {
      val predecessorEntity = predecessorById[entity.id] ?: continue
      val oldLines = predecessorEntity.toDslLines()
      val newLines = entity.toDslLines()
      if (oldLines == newLines) {
        result += EntityDiffView(
          name = entity.name,
          changeType = DiffChangeType.UNCHANGED,
          lines = newLines.map { DiffLine(it, DiffLineType.UNCHANGED) },
        )
      } else {
        result += EntityDiffView(
          name = entity.name,
          changeType = DiffChangeType.MODIFIED,
          lines = diffLines(oldLines, newLines),
        )
      }
    }

    return result.sortedWith(
      compareBy(
        { if (it.changeType == DiffChangeType.REMOVED) 0 else if (it.changeType == DiffChangeType.ADDED) 1 else 2 },
        { it.name },
      ),
    )
  }

  private fun computeReportDiffs(predecessor: AppVersion, current: AppVersion): List<ReportDiffView> {
    val result = mutableListOf<ReportDiffView>()
    val predecessorById = predecessor.reports.associateBy { it.id }
    val currentById = current.reports.associateBy { it.id }

    // Removed reports
    for (report in predecessor.reports) {
      if (report.id !in currentById) {
        result += ReportDiffView(
          name = report.name,
          changeType = DiffChangeType.REMOVED,
          htmlLines = report.html.lines().map { DiffLine(it, DiffLineType.REMOVED) },
          scriptLines = report.script.lines().map { DiffLine(it, DiffLineType.REMOVED) },
        )
      }
    }

    // Added reports
    for (report in current.reports) {
      if (report.id !in predecessorById) {
        result += ReportDiffView(
          name = report.name,
          changeType = DiffChangeType.ADDED,
          htmlLines = report.html.lines().map { DiffLine(it, DiffLineType.ADDED) },
          scriptLines = report.script.lines().map { DiffLine(it, DiffLineType.ADDED) },
        )
      }
    }

    // Potentially modified reports
    for (report in current.reports) {
      val predecessorReport = predecessorById[report.id] ?: continue
      val htmlLines = diffLines(predecessorReport.html.lines(), report.html.lines())
      val scriptLines = diffLines(predecessorReport.script.lines(), report.script.lines())
      val changed = htmlLines.any { it.type != DiffLineType.UNCHANGED } ||
        scriptLines.any { it.type != DiffLineType.UNCHANGED }
      result += ReportDiffView(
        name = report.name,
        changeType = if (changed) DiffChangeType.MODIFIED else DiffChangeType.UNCHANGED,
        htmlLines = htmlLines,
        scriptLines = scriptLines,
      )
    }

    return result.sortedWith(
      compareBy(
        { if (it.changeType == DiffChangeType.REMOVED) 0 else if (it.changeType == DiffChangeType.ADDED) 1 else 2 },
        { it.name },
      ),
    )
  }

  private fun EntityDefinition.toDslLines(): List<String> {
    val lines = mutableListOf<String>()
    lines += "entity $name {"
    for (prop in properties) {
      lines += "  ${prop.toDsl()}"
    }
    lines += "}"
    return lines
  }

  private fun Property.toDsl(): String {
    val parts = mutableListOf<String>()
    parts += "$name: $type"
    if (!nullable) parts += "required"
    for (constraint in constraints.sortedBy { it.toDslString() }) {
      parts += constraint.toDslString()
    }
    return parts.joinToString(" ")
  }

  private fun PropertyConstraint.toDslString(): String = when (this) {
    is PropertyConstraint.UniqueKey -> "uniqueKey"
    is PropertyConstraint.MinLong -> "min=$min"
    is PropertyConstraint.MaxLong -> "max=$max"
    is PropertyConstraint.MinDouble -> "min=$min"
    is PropertyConstraint.MaxDouble -> "max=$max"
    is PropertyConstraint.MinLength -> "minLength=$min"
    is PropertyConstraint.MaxLength -> "maxLength=$max"
    is PropertyConstraint.Pattern -> "pattern=$regex"
    is PropertyConstraint.MinSize -> "minSize=$min"
    is PropertyConstraint.MaxSize -> "maxSize=$max"
  }

  internal fun diffLines(oldLines: List<String>, newLines: List<String>): List<DiffLine> {
    val m = oldLines.size
    val n = newLines.size
    val lcs = Array(m + 1) { IntArray(n + 1) }
    for (i in 1..m) {
      for (j in 1..n) {
        lcs[i][j] = if (oldLines[i - 1] == newLines[j - 1]) {
          lcs[i - 1][j - 1] + 1
        } else {
          maxOf(lcs[i - 1][j], lcs[i][j - 1])
        }
      }
    }
    val result = mutableListOf<DiffLine>()
    var i = m
    var j = n
    while (i > 0 || j > 0) {
      when {
        i > 0 && j > 0 && oldLines[i - 1] == newLines[j - 1] -> {
          result.add(0, DiffLine(oldLines[i - 1], DiffLineType.UNCHANGED))
          i--
          j--
        }
        j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j]) -> {
          result.add(0, DiffLine(newLines[j - 1], DiffLineType.ADDED))
          j--
        }
        else -> {
          result.add(0, DiffLine(oldLines[i - 1], DiffLineType.REMOVED))
          i--
        }
      }
    }
    return result
  }
}
