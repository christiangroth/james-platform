package de.chrgroth.james.platform.domain.port.out.playback

import kotlin.time.Instant

interface PlaybackActivityPort {
  fun isPlaybackActive(): Boolean
  fun lastActivityTimestamp(): Instant?
}
