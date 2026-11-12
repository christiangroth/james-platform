package de.chrgroth.james.platform.adapter.`in`.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LocalReturnUrlTests {

  @Test
  fun `accepts local paths of the user UI`() {
    assertThat(LocalReturnUrl.validated("/ui/user/dashboard")).isEqualTo("/ui/user/dashboard")
    assertThat(LocalReturnUrl.validated("/ui/user/apps/abc")).isEqualTo("/ui/user/apps/abc")
  }

  @Test
  fun `rejects everything that could leave the site or is not a UI path`() {
    listOf(null, "", "  ", "https://evil.example/ui/", "//evil.example", "/\\evil.example", "/ui/../admin", "javascript:alert(1)", "/other", "ui/user/dashboard", "/ui/user/a\nb", "/ui/user/a b")
      .forEach { assertThat(LocalReturnUrl.validated(it)).describedAs("candidate: %s", it).isNull() }
  }
}
