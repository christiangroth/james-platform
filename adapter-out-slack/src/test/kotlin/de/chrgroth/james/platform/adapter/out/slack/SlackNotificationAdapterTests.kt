package de.chrgroth.james.platform.adapter.out.slack

import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import org.junit.jupiter.api.Test
import java.util.Optional

class SlackNotificationAdapterTests {

  private fun adapter(
    webhookUrl: Optional<String> = Optional.empty(),
    username: String = "James Platform",
    iconEmoji: String = ":robot_face:",
    startupEnabled: Boolean = false,
    stoppingEnabled: Boolean = false,
  ) = SlackNotificationAdapter(
    version = "1.0.0-TEST",
    webhookUrl = webhookUrl,
    username = username,
    iconEmoji = iconEmoji,
    startupEnabled = startupEnabled,
    stoppingEnabled = stoppingEnabled,
  )

  @Test
  fun `adapter logs on construction when webhook url is blank`() {
    adapter(webhookUrl = Optional.empty())
  }

  @Test
  fun `adapter logs on construction when webhook url is set`() {
    adapter(webhookUrl = Optional.of("https://hooks.slack.com/test"))
  }

  @Test
  fun `startup notification does not throw when disabled`() {
    adapter().onStartup(StartupEvent())
  }

  @Test
  fun `startup notification does not throw when no webhook url configured`() {
    adapter(startupEnabled = true).onStartup(StartupEvent())
  }

  @Test
  fun `stopping notification does not throw when disabled`() {
    adapter().onShutdown(ShutdownEvent())
  }

  @Test
  fun `stopping notification does not throw when no webhook url configured`() {
    adapter(stoppingEnabled = true).onShutdown(ShutdownEvent())
  }
}
