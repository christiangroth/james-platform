package de.chrgroth.james.platform.domain.port.out.infra

import de.chrgroth.james.platform.domain.model.user.Username

interface DashboardRefreshPort {
  fun notifyUserPlaybackData(username: Username)
  fun notifyUserPlaylistMetadata(username: Username)
  fun notifyCatalogData()
}
