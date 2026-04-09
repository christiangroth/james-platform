package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Path("/docs/{subdir}/{filename}")
@ApplicationScoped
@Suppress("Unused")
class DocsFileResource {

  @Inject
  @Location("docs.html")
  private lateinit var docsTemplate: Template

  private val allowedSubdirs = setOf("arc42", "adr", "coding-guidelines", "releasenotes")

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun docs(
    @PathParam("subdir") subdir: String,
    @PathParam("filename") filename: String,
  ): TemplateInstance {
    val decodedFilename = URLDecoder.decode(filename, StandardCharsets.UTF_8)
    if (subdir !in allowedSubdirs || isInvalidFilename(decodedFilename)) {
      throw NotFoundException("Doc not found: $subdir/$filename")
    }
    val content = DocsUtils.readMarkdown("docs/$subdir/$decodedFilename")
      ?: throw NotFoundException("Doc not found: $subdir/$filename")
    return docsTemplate.instance()
      .data("title", DocsUtils.extractTitle(content, decodedFilename))
      .data("markdownContent", content)
  }

  private fun isInvalidFilename(filename: String): Boolean =
    !filename.endsWith(".md") || filename.contains("/") || filename.contains("..")
}
