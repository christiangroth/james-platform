package de.chrgroth.james.platform.adapter.`in`.web

import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

private const val RELEASE_NOTES_CLASSPATH_RESOURCE = "docs/releasenotes/RELEASENOTES.md"

/**
 * Renders the generated `docs/releasenotes/RELEASENOTES.md` (see [ReleaseNotesParser]) as a dedicated accordion page
 * grouped by minor version, instead of the raw markdown dump served by the generic [DocsFileResource] under
 * `/docs/releasenotes/RELEASENOTES.md`. Reachable by any authenticated user, same as the generic docs viewer and the
 * app version link in the navbar that now points here.
 */
@Path("/release-notes")
@ApplicationScoped
@Suppress("Unused")
class ReleaseNotesResource {

  @Inject
  @Location("release-notes.html")
  private lateinit var releaseNotesTemplate: Template

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun releaseNotes(): TemplateInstance {
    val content = DocsUtils.readMarkdown(RELEASE_NOTES_CLASSPATH_RESOURCE).orEmpty()
    return releaseNotesTemplate.data("groups", ReleaseNotesParser.parse(content))
  }
}
