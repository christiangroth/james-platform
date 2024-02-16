package de.chrgroth.gradle.plugins

import java.util.regex.Pattern

val versionExtractor: Pattern = Pattern.compile("""([0-9]+)(?:.([0-9]+))?(?:.([0-9]+))?([0-9.a-zA-Z-+_]*)""")

data class ProjectVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val addition: String,
    val ticketId: String?
) {

    operator fun compareTo(other: ProjectVersion): Int {
        val majorComparison = major.compareTo(other.major)
        val minorComparison = minor.compareTo(other.minor)
        val patchComparison = patch.compareTo(other.patch)

        return when {
            majorComparison != 0 -> majorComparison
            minorComparison != 0 -> minorComparison
            patchComparison != 0 -> patchComparison
            else -> 0
        }
    }

    companion object {
        fun String.toProjectVersion(ticketId: String? = null) = invoke(this, ticketId)

        operator fun invoke(projectVersion: String, ticketId: String? = null): ProjectVersion {
            val matcher = versionExtractor.matcher(projectVersion).apply { find() }
            return try {
                ProjectVersion(
                    major = matcher.group(1).toInt(),
                    minor = matcher.group(2).toInt(),
                    patch = matcher.group(3).toInt(),
                    addition = if (matcher.groupCount() > 3) matcher.group(4) else "",
                    ticketId = ticketId
                )
            } catch (e: Exception) {
                throw UnsupportedProjectVersionException(projectVersion)
            }
        }

    }

    override fun toString() = "$major.$minor.$patch${ticketId?.let { "-$it" } ?: ""}$addition"
}

class UnsupportedProjectVersionException(projectVersion: String) :
    RuntimeException("Command returned version $projectVersion cannot be parsed to version pattern!")
