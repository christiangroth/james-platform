package de.chrgroth.james

import com.github.glwithu06.semver.Semver

fun Semver.incMajor() = Semver(major.toInt() + 1, 0, 0)
fun Semver.incMinor() = Semver(major.toInt(), minor.toInt() + 1, 0)
fun Semver.incPatch() = Semver(major.toInt(), minor.toInt(), patch.toInt() + 1)
