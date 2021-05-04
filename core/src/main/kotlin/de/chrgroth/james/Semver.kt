package de.chrgroth.james

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.app.AppVersionChangeType
import de.chrgroth.james.app.AppVersionReleaseNotes

internal fun Semver.computeNext(isBreaking: Boolean, releaseNotes: AppVersionReleaseNotes) =
    if (isBreaking) {
        incMajor()
    } else {
        when (releaseNotes.changeType) {
            AppVersionChangeType.BUGFIX -> incPatch()
            AppVersionChangeType.FEATURE -> incMinor()
        }
    }

private fun Semver.incMajor() = Semver(major.toInt() + 1, 0, 0)
private fun Semver.incMinor() = Semver(major.toInt(), minor.toInt() + 1, 0)
private fun Semver.incPatch() = Semver(major.toInt(), minor.toInt(), patch.toInt() + 1)
