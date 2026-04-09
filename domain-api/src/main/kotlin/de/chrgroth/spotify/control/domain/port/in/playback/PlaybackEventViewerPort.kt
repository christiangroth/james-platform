package de.chrgroth.spotify.control.domain.port.`in`.playback

import de.chrgroth.spotify.control.domain.model.playback.PlaybackEventViewerResult
import de.chrgroth.spotify.control.domain.model.user.UserId
import kotlinx.datetime.LocalDate

interface PlaybackEventViewerPort {
  fun getEvents(userId: UserId, date: LocalDate): PlaybackEventViewerResult
}
