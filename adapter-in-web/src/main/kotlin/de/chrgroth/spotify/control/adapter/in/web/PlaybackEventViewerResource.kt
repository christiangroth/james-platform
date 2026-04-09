package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackEventViewerPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

@Path("/playback-event-viewer")
@ApplicationScoped
@Suppress("Unused")
class PlaybackEventViewerResource {

  @Inject
  @Location("playback-event-viewer.html")
  private lateinit var template: Template

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var playbackEventViewer: PlaybackEventViewerPort

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun viewer(@QueryParam("date") dateParam: String?): TemplateInstance {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val requestedDate = dateParam?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
    val date = if (requestedDate > today) today else requestedDate

    val userId = UserId(securityIdentity.principal.name)
    val result = playbackEventViewer.getEvents(userId, date)

    return template
      .data("result", result)
      .data("prevDate", date.minus(DatePeriod(days = 1)))
      .data("nextDate", date.plus(DatePeriod(days = 1)))
      .data("today", today)
  }
}
