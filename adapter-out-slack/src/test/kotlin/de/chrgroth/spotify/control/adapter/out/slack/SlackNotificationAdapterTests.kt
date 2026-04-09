package de.chrgroth.spotify.control.adapter.out.slack

import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import org.junit.jupiter.api.Test
import java.util.Optional

class SlackNotificationAdapterTests {

  private fun adapter(
    webhookUrl: Optional<String> = Optional.empty(),
    username: String = "SpCtl",
    iconEmoji: String = ":robot_face:",
    startupEnabled: Boolean = false,
    stoppingEnabled: Boolean = false,
    partitionPausedEnabled: Boolean = false,
    partitionResumedEnabled: Boolean = false,
    checkPassedEnabled: Boolean = false,
    violationsChangedEnabled: Boolean = false,
  ) = SlackNotificationAdapter(
    version = "1.0.0-TEST",
    webhookUrl = webhookUrl,
    username = username,
    iconEmoji = iconEmoji,
    startupEnabled = startupEnabled,
    stoppingEnabled = stoppingEnabled,
    partitionPausedEnabled = partitionPausedEnabled,
    partitionResumedEnabled = partitionResumedEnabled,
    checkPassedEnabled = checkPassedEnabled,
    violationsChangedEnabled = violationsChangedEnabled,
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

  @Test
  fun `partition paused notification does not throw when disabled`() {
    adapter().onPartitionPaused("test-partition", "RATE_LIMITED")
  }

  @Test
  fun `partition paused notification does not throw when no webhook url configured`() {
    adapter(partitionPausedEnabled = true).onPartitionPaused("test-partition", "RATE_LIMITED")
  }

  @Test
  fun `partition paused notification includes status reason`() {
    adapter(partitionPausedEnabled = true).onPartitionPaused("test-partition", "RATE_LIMITED")
  }

  @Test
  fun `partition paused notification handles blank reason`() {
    adapter(partitionPausedEnabled = true).onPartitionPaused("test-partition", "unknown")
  }

  @Test
  fun `partition resumed notification does not throw when disabled`() {
    adapter().onPartitionActivated("test-partition")
  }

  @Test
  fun `partition resumed notification does not throw when no webhook url configured`() {
    adapter(partitionResumedEnabled = true).onPartitionActivated("test-partition")
  }

  @Test
  fun `check passed notification does not throw when disabled`() {
    adapter().notifyCheckPassed(buildCheck())
  }

  @Test
  fun `check passed notification does not throw when no webhook url configured`() {
    adapter(checkPassedEnabled = true).notifyCheckPassed(buildCheck())
  }

  @Test
  fun `violations changed notification does not throw when disabled`() {
    adapter().notifyViolationsChanged(buildCheck(violations = listOf("Artist – Track")))
  }

  @Test
  fun `violations changed notification does not throw when no webhook url configured`() {
    adapter(violationsChangedEnabled = true).notifyViolationsChanged(buildCheck(violations = listOf("Artist – Track")))
  }

  private fun buildCheck(
    playlistId: String = "playlist-1",
    checkId: String = "playlist-1:duplicate-tracks",
    succeeded: Boolean = true,
    violations: List<String> = emptyList(),
  ) = de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck(
    checkId = checkId,
    playlistId = de.chrgroth.spotify.control.domain.model.playlist.PlaylistId(playlistId),
    lastCheck = kotlin.time.Clock.System.now(),
    succeeded = succeeded,
    violations = violations,
  )
}
