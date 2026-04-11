package de.chrgroth.james.platform.domain.port.out.infra

import de.chrgroth.james.platform.domain.model.user.UserId

interface DashboardRefreshPort {
  fun notifyUserPlaybackData(userId: UserId)
  fun notifyUserPlaylistMetadata(userId: UserId)
  fun notifyUserPlaylistChecks(userId: UserId)
  fun notifyCatalogData()
}
