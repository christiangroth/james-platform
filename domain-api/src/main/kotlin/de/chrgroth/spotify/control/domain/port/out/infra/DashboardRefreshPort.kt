package de.chrgroth.spotify.control.domain.port.out.infra

import de.chrgroth.spotify.control.domain.model.user.UserId

interface DashboardRefreshPort {
  fun notifyUserPlaybackData(userId: UserId)
  fun notifyUserPlaylistMetadata(userId: UserId)
  fun notifyUserPlaylistChecks(userId: UserId)
  fun notifyCatalogData()
}
