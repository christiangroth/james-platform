package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.model.viewer.MongoViewerFilter
import de.chrgroth.spotify.control.domain.model.viewer.MongoViewerFilterOperator
import de.chrgroth.spotify.control.domain.port.`in`.infra.MongoViewerPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.UriInfo

@Path("/mongodb-viewer")
@ApplicationScoped
@Suppress("Unused")
class MongoViewerResource {

  @Inject
  @Location("mongodb-viewer.html")
  private lateinit var viewerTemplate: Template

  @Inject
  private lateinit var mongoViewer: MongoViewerPort

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun viewer(
    @QueryParam("collection") collection: String?,
    @QueryParam("sort") sort: String?,
    @QueryParam("sortDir") sortDir: String?,
    @QueryParam("page") page: Int?,
    @QueryParam("pageSize") pageSize: Int?,
    @Context uriInfo: UriInfo,
  ): TemplateInstance {
    val effectivePage = page?.takeIf { it > 0 } ?: 1
    val effectivePageSize = pageSize?.takeIf { it in PAGE_SIZES } ?: DEFAULT_PAGE_SIZE
    val effectiveSortDesc = sortDir?.equals("desc", ignoreCase = true) ?: false

    val allParams = uriInfo.queryParameters
    val filters = mutableListOf<MongoViewerFilter>()
    allParams.forEach { (key, values) ->
      val value = values.firstOrNull()?.trim() ?: return@forEach
      when {
        key.startsWith("fc_") -> {
          val fieldName = key.removePrefix("fc_")
          if (value.isNotBlank()) filters.add(MongoViewerFilter(fieldName, MongoViewerFilterOperator.CONTAINS, value))
        }
        key.startsWith("feq_") -> {
          val fieldName = key.removePrefix("feq_")
          if (value.isNotBlank()) filters.add(MongoViewerFilter(fieldName, MongoViewerFilterOperator.EQUALS, value))
        }
        key.startsWith("fin_") -> {
          val fieldName = key.removePrefix("fin_")
          if (value.isNotBlank()) filters.add(MongoViewerFilter(fieldName, MongoViewerFilterOperator.IN, value))
        }
        key.startsWith("fnin_") -> {
          val fieldName = key.removePrefix("fnin_")
          if (value.isNotBlank()) filters.add(MongoViewerFilter(fieldName, MongoViewerFilterOperator.NOT_IN, value))
        }
      }
    }

    val result = mongoViewer.getViewer(
      collection = collection,
      filters = filters,
      sortField = sort,
      sortDesc = effectiveSortDesc,
      page = effectivePage,
      pageSize = effectivePageSize,
    )

    return viewerTemplate
      .data("result", result)
      .data("pageSizes", PAGE_SIZES)
  }

  companion object {
    private val PAGE_SIZES = listOf(10, 25, 50, 100)
    private const val DEFAULT_PAGE_SIZE = 50
  }
}
