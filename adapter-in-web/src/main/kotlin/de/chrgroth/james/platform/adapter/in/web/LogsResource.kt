package de.chrgroth.james.platform.adapter.`in`.web

import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType

data class UiLogGroup(
  val clazz: String,
  val level: String,
  val entries: List<UiLogEntry>,
) {
  val count: Int = entries.size
}

@Path("/logs")
@ApplicationScoped
@Suppress("Unused")
class LogsResource(
  @param:Location("logs.html")
  private val logsTemplate: Template,
  private val logBuffer: UiLogBuffer,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun logs(@QueryParam("view") view: String?): TemplateInstance {
    val entries = logBuffer.getRecent()
    val isGroupedView = view == "grouped"
    return logsTemplate
      .data("entries", entries)
      .data("isGroupedView", isGroupedView)
      .data("groups", entries.toGroupedView())
  }

  private fun List<UiLogEntry>.toGroupedView(): List<UiLogGroup> =
    this
      .groupBy { it.clazz to it.level }
      .map { (key, groupedEntries) ->
        UiLogGroup(
          clazz = key.first,
          level = key.second,
          entries = groupedEntries.sortedByDescending { it.timestamp },
        )
      }
      .sortedWith(
        compareBy<UiLogGroup> { levelPriority(it.level) }
          .thenByDescending { it.count }
          .thenBy { it.clazz },
      )

  private fun levelPriority(level: String): Int = when (level) {
    "ERROR" -> 0
    "WARN" -> 1
    else -> 2
  }
}
