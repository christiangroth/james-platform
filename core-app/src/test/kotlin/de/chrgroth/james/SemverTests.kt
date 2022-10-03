package de.chrgroth.james

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.app.AppVersionChangeType.BUGFIX
import de.chrgroth.james.app.AppVersionChangeType.FEATURE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SemverTests {

    private val current = Semver(1, 2, 3)

    @Test
    fun `next feature version is minor level change`() {
        current.computeNext(false, FEATURE)
            .assertParts(current.major, "3", "0")
    }

    @Test
    fun `next bugfix version is patch level change`() {
        current.computeNext(false, BUGFIX)
            .assertParts(current.major, current.minor, "4")
    }

    @Test
    fun `breaking feature version is minor level change`() {
        current.computeNext(true, FEATURE)
            .assertParts("2", "0", "0")
    }

    @Test
    fun `breaking bugfix version is patch level change`() {
        current.computeNext(true, BUGFIX)
            .assertParts("2", "0", "0")
    }

    private fun Semver.assertParts(expectedMajor: String, expectedMinor: String, expectedPatch: String) {
        assertThat(major).isEqualTo(expectedMajor)
        assertThat(minor).isEqualTo(expectedMinor)
        assertThat(patch).isEqualTo(expectedPatch)
    }
}
