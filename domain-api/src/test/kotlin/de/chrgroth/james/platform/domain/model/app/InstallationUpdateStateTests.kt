package de.chrgroth.james.platform.domain.model.app

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class InstallationUpdateStateTests {

  @ParameterizedTest
  @CsvSource(
    "1.2.0, 1.2.0, UP_TO_DATE",
    "1.0.0, 1.2.0, AUTO_UPDATE_PENDING",
    "1.2.0, 1.2.3, AUTO_UPDATE_PENDING",
    "1.2.0, 2.0.0, MANUAL_UPDATE_REQUIRED",
    "0.9.0, 1.0.0, MANUAL_UPDATE_REQUIRED",
    "garbage, 1.0.0, MANUAL_UPDATE_REQUIRED",
  )
  fun `derives the update state from installed and latest version`(installed: String, latest: String, expected: InstallationUpdateState) {
    assertThat(InstallationUpdateState.of(VersionNumber(installed), VersionNumber(latest))).isEqualTo(expected)
  }

  @ParameterizedTest
  @CsvSource("1.2.3, 1", "10.0.0, 10", "x.1.0, ")
  fun `exposes the semver major`(version: String, expected: Int?) {
    assertThat(VersionNumber(version).major).isEqualTo(expected)
  }
}
