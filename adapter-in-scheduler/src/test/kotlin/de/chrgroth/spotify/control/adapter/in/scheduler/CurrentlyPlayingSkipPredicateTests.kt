package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.quarkus.starters.domain.ScheduledSkipPredicate
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackActivityPort
import io.mockk.every
import io.mockk.mockk
import io.quarkus.scheduler.ScheduledExecution
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CurrentlyPlayingSkipPredicateTests {

  private val starterSkipPredicate: ScheduledSkipPredicate = mockk(relaxed = true)
  private val playbackActivity: PlaybackActivityPort = mockk()
  private val execution: ScheduledExecution = mockk(relaxed = true)

  private val predicate = CurrentlyPlayingSkipPredicate(playbackActivity, starterSkipPredicate)

  @Test
  fun `skips when ScheduledSkipPredicate returns true`() {
    every { starterSkipPredicate.test(execution) } returns true

    assertThat(predicate.test(execution)).isTrue()
  }

  @Test
  fun `does not skip on first call when no playback active`() {
    every { starterSkipPredicate.test(execution) } returns false
    every { playbackActivity.isPlaybackActive() } returns false

    assertThat(predicate.test(execution)).isFalse()
  }

  @Test
  fun `skips on second immediate call when no playback active (slow interval not elapsed)`() {
    every { starterSkipPredicate.test(execution) } returns false
    every { playbackActivity.isPlaybackActive() } returns false
    predicate.test(execution)

    assertThat(predicate.test(execution)).isTrue()
  }

  @Test
  fun `skips on second immediate call when playback active (fast interval not elapsed)`() {
    every { starterSkipPredicate.test(execution) } returns false
    every { playbackActivity.isPlaybackActive() } returns true
    predicate.test(execution)

    assertThat(predicate.test(execution)).isTrue()
  }
}
