package de.chrgroth.james.platform.domain.port.`in`.infra

import de.chrgroth.james.platform.domain.model.DashboardStats
import de.chrgroth.james.platform.domain.model.user.UserId

interface DashboardPort {
  fun getStats(userId: UserId): DashboardStats
  fun getPlaybackStats(userId: UserId): DashboardStats
  fun getPlaylistMetadata(userId: UserId): DashboardStats
  fun getRecentlyPlayed(userId: UserId): DashboardStats
  fun getListeningStats(userId: UserId): DashboardStats
  fun getPlaylistCheckStats(): DashboardStats
  fun getCatalogStats(): DashboardStats
}
