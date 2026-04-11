package de.chrgroth.james.platform.domain.port.`in`.playback

import de.chrgroth.james.platform.domain.model.playback.PlaybackEventViewerResult
import de.chrgroth.james.platform.domain.model.user.UserId
import kotlinx.datetime.LocalDate

interface PlaybackEventViewerPort {
  fun getEvents(userId: UserId, date: LocalDate): PlaybackEventViewerResult
}
