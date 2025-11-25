package de.chrgroth.james

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.app.AppVersionChangeType

internal fun Semver.computeNext(isBreaking: Boolean, changeType: AppVersionChangeType): Semver =
  if (isBreaking) {
    incMajor()
  } else {
    when (changeType) {
      AppVersionChangeType.BUGFIX -> incPatch()
      AppVersionChangeType.FEATURE -> incMinor()
    }
  }

private fun Semver.incMajor(): Semver = Semver(major.toInt() + 1, 0, 0)
private fun Semver.incMinor(): Semver = Semver(major.toInt(), minor.toInt() + 1, 0)
private fun Semver.incPatch(): Semver = Semver(major.toInt(), minor.toInt(), patch.toInt() + 1)
