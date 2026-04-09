package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.error.ArtistSettingsError
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogPort
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackPort
import de.chrgroth.spotify.control.domain.port.`in`.user.UserProfilePort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import mu.KLogging

@Path("/settings/playback")
@ApplicationScoped
@Suppress("Unused")
class PlaybackSettingsResource(
  @param:Location("settings/playback.html")
  private val playbackTemplate: Template,
  private val securityIdentity: SecurityIdentity,
  private val userProfile: UserProfilePort,
  private val playback: PlaybackPort,
  private val catalog: CatalogPort,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun playback(): TemplateInstance {
    val userId = UserId(securityIdentity.principal.name)
    val displayName = userProfile.getDisplayName(userId) ?: userId.value
    return playbackTemplate
      .data("displayName", displayName)
  }

  @POST
  @Authenticated
  @Path("/rebuild")
  @Produces(MediaType.APPLICATION_JSON)
  fun rebuildPlaybackData(): Response {
    val userId = UserId(securityIdentity.principal.name)
    playback.enqueueRebuildPlaybackData(userId)
    return Response.ok(mapOf("status" to "ok")).build()
  }

  @POST
  @Authenticated
  @Path("/resync-catalog")
  @Produces(MediaType.APPLICATION_JSON)
  fun resyncCatalog(): Response {
    return catalog.resyncCatalog().fold(
      ifLeft = { error ->
        logger.error { "Catalog re-sync failed: ${error.code}" }
        Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(mapOf("error" to "Re-sync failed: ${error.code}"))
          .build()
      },
      ifRight = { Response.ok(mapOf("status" to "ok")).build() },
    )
  }

  @POST
  @Authenticated
  @Path("/artists/{artistId}/resync")
  @Produces(MediaType.APPLICATION_JSON)
  fun resyncArtist(@PathParam("artistId") artistId: String): Response {
    return catalog.resyncArtist(artistId).fold(
      ifLeft = { error ->
        when (error) {
          ArtistSettingsError.ARTIST_NOT_FOUND -> {
            logger.warn { "Artist $artistId not found for re-sync: ${error.code}" }
            Response.status(Response.Status.NOT_FOUND)
              .entity(mapOf("error" to "Artist not found: $artistId"))
              .build()
          }
          else -> {
            logger.error { "Artist re-sync failed for $artistId: ${error.code}" }
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
              .entity(mapOf("error" to "Re-sync failed: ${error.code}"))
              .build()
          }
        }
      },
      ifRight = { Response.ok(mapOf("status" to "ok")).build() },
    )
  }

  companion object : KLogging()
}
