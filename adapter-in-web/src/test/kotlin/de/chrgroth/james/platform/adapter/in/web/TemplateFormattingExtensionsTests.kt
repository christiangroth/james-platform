package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.infra.AppTimeZone
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class TemplateFormattingExtensionsTests {

  @Test
  fun `the shared UI zone defaults to Europe Berlin`() {
    assertThat(AppTimeZone.zone.id).isEqualTo(System.getProperty("app.time-zone") ?: System.getenv("APP_TIME_ZONE") ?: "Europe/Berlin")
  }

  @Test
  fun `formatted renders a UTC instant in the UI zone during winter time`() {
    assumeDefaultZone()

    assertThat(TemplateFormattingExtensions.formatted(Instant.parse("2026-01-15T05:30:00Z"))).isEqualTo("2026-01-15 06:30:00")
  }

  @Test
  fun `formatted renders a UTC instant in the UI zone during summer time`() {
    assumeDefaultZone()

    assertThat(TemplateFormattingExtensions.formatted(Instant.parse("2026-07-15T04:30:00Z"))).isEqualTo("2026-07-15 06:30:00")
  }

  @Test
  fun `formattedShort renders in the UI zone across the spring DST change`() {
    assumeDefaultZone()

    assertThat(TemplateFormattingExtensions.formattedShort(kotlin.time.Instant.parse("2026-03-29T00:59:00Z"))).isEqualTo("2026-03-29 01:59")
    assertThat(TemplateFormattingExtensions.formattedShort(kotlin.time.Instant.parse("2026-03-29T01:00:00Z"))).isEqualTo("2026-03-29 03:00")
  }

  private fun assumeDefaultZone() {
    org.junit.jupiter.api.Assumptions.assumeTrue(AppTimeZone.zone.id == "Europe/Berlin", "zone overridden via app.time-zone / APP_TIME_ZONE")
  }
}
