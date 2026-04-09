package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.error.PlaylistSyncError
import mu.KLogging
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistPort
import de.chrgroth.spotify.control.domain.port.`in`.user.UserProfilePort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlin.time.Instant

@Path("/settings/playlist")
@ApplicationScoped
@Suppress("Unused")
class PlaylistSettingsResource(
  @param:Location("settings/playlist.html")
  private val playlistTemplate: Template,
  private val securityIdentity: SecurityIdentity,
  private val userProfile: UserProfilePort,
  private val playlist: PlaylistPort,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun playlist(): TemplateInstance {
    val userId = UserId(securityIdentity.principal.name)
    val displayName = userProfile.getDisplayName(userId) ?: userId.value
    val sortedPlaylists = playlist.getPlaylists(userId).sortedBy { it.name }
    val padWidth = sortedPlaylists.size.toString().length
    val trackCounts = playlist.getTrackCounts(userId)
    val rows = sortedPlaylists.mapIndexed { index, playlistInfo ->
      PlaylistRow(
        lineNumber = (index + 1).toString().padStart(padWidth, '0'),
        playlist = playlistInfo,
        numberOfTracks = trackCounts[playlistInfo.spotifyPlaylistId],
      )
    }
    return playlistTemplate
      .data("displayName", displayName)
      .data("rows", rows)
  }

  data class PlaylistRow(val lineNumber: String, val playlist: PlaylistInfo, val numberOfTracks: Int? = null) {
    val active: Boolean get() = playlist.syncStatus == PlaylistSyncStatus.ACTIVE
    val lastSyncTime: Instant get() = playlist.lastSyncTime ?: playlist.lastSnapshotIdSyncTime
    val typeLabel: String? get() = playlist.type?.name?.lowercase()
  }

  @PUT
  @Authenticated
  @Path("/{playlistId}/sync-status")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun updateSyncStatus(
    @PathParam("playlistId") playlistId: String,
    request: SyncStatusRequest,
  ): Response {
    val userId = UserId(securityIdentity.principal.name)
    val syncStatus = PlaylistSyncStatus.entries.find { it.name == request.syncStatus }
      ?: return Response.status(Response.Status.BAD_REQUEST)
        .entity(mapOf("error" to "Invalid sync status: ${request.syncStatus}"))
        .build()
    return when (playlist.updateSyncStatus(userId, playlistId, syncStatus).isRight()) {
      true -> {
        val updated = playlist.getPlaylists(userId).find { it.spotifyPlaylistId == playlistId }
        Response.ok(mapOf("syncStatus" to syncStatus.name, "type" to updated?.type?.name)).build()
      }
      false -> {
        logger.warn { "Playlist $playlistId not found for user ${userId.value} during sync status update" }
        Response.status(Response.Status.NOT_FOUND)
          .entity(mapOf("error" to "Playlist not found"))
          .build()
      }
    }
  }

  data class SyncStatusRequest(val syncStatus: String = "")

  @PUT
  @Authenticated
  @Path("/{playlistId}/type")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun updatePlaylistType(
    @PathParam("playlistId") playlistId: String,
    request: PlaylistTypeRequest,
  ): Response {
    val userId = UserId(securityIdentity.principal.name)
    val type = PlaylistType.entries.find { it.name == request.type }
      ?: return Response.status(Response.Status.BAD_REQUEST)
        .entity(mapOf("error" to "Invalid playlist type: ${request.type}"))
        .build()
    return playlist.updatePlaylistType(userId, playlistId, type).fold(
      ifLeft = { error ->
        when (error) {
          PlaylistSyncError.PLAYLIST_TYPE_CONFLICT -> {
            logger.warn { "Playlist type update conflict for $playlistId (user ${userId.value}): ${error.code}" }
            Response.status(Response.Status.CONFLICT)
              .entity(mapOf("error" to "Only one playlist of type ALL is allowed"))
              .build()
          }
          PlaylistSyncError.PLAYLIST_NOT_ACTIVE -> {
            logger.warn { "Playlist type update rejected for inactive playlist $playlistId (user ${userId.value}): ${error.code}" }
            Response.status(Response.Status.BAD_REQUEST)
              .entity(mapOf("error" to "Playlist type can only be set for active playlists"))
              .build()
          }
          else -> {
            logger.warn { "Playlist $playlistId not found for user ${userId.value} during type update: ${error.code}" }
            Response.status(Response.Status.NOT_FOUND)
              .entity(mapOf("error" to "Playlist not found"))
              .build()
          }
        }
      },
      ifRight = { Response.ok(mapOf("type" to type.name)).build() },
    )
  }

  data class PlaylistTypeRequest(val type: String = "")

  @POST
  @Authenticated
  @Path("/sync")
  @Produces(MediaType.APPLICATION_JSON)
  fun syncNow(): Response {
    val userId = UserId(securityIdentity.principal.name)
    return playlist.syncPlaylists(userId).fold(
      ifLeft = { error ->
        logger.error { "Playlist sync failed for user ${userId.value}: ${error.code}" }
        Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(mapOf("error" to "Sync failed: ${error.code}"))
          .build()
      },
      ifRight = { Response.ok(mapOf("status" to "ok")).build() },
    )
  }

  @POST
  @Authenticated
  @Path("/{playlistId}/sync")
  @Produces(MediaType.APPLICATION_JSON)
  fun syncPlaylist(@PathParam("playlistId") playlistId: String): Response {
    val userId = UserId(securityIdentity.principal.name)
    return playlist.enqueueSyncPlaylistData(userId, playlistId).fold(
      ifLeft = { error ->
        when (error) {
          PlaylistSyncError.PLAYLIST_SYNC_INACTIVE -> {
            logger.warn { "Sync enqueue rejected for inactive playlist $playlistId (user ${userId.value}): ${error.code}" }
            Response.status(Response.Status.BAD_REQUEST)
              .entity(mapOf("error" to "Sync enqueue failed: ${error.code}"))
              .build()
          }
          else -> {
            logger.warn { "Sync enqueue failed for playlist $playlistId (user ${userId.value}): ${error.code}" }
            Response.status(Response.Status.NOT_FOUND)
              .entity(mapOf("error" to "Sync enqueue failed: ${error.code}"))
              .build()
          }
        }
      },
      ifRight = { Response.ok(mapOf("status" to "ok")).build() },
    )
  }

  companion object : KLogging()
}
