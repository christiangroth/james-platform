package de.chrgroth.james.platform.adapter.`in`.web

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.App
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.SortCriteria
import de.chrgroth.james.platform.domain.model.app.SortDirection
import de.chrgroth.james.platform.domain.port.`in`.app.AggregationInput
import de.chrgroth.james.platform.domain.port.`in`.app.AppManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.AppVersionManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.MigrationStepInput
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

/**
 * The token owner's Apps as Developer:
 * - `DEVELOPER_READ`: all Apps, all Versions including drafts, version bump preview.
 * - `DEVELOPER_WRITE`: create, edit and delete drafts (entities, properties, computed properties, aggregations, migration steps, reports).
 * - `DEVELOPER_RELEASE`: release (publish) the draft.
 *
 * Every endpoint first checks that the App belongs to the token owner; foreign Apps are reported as not found. Edits are only accepted on drafts,
 * enforced by the domain (`VERSION_NOT_IN_DRAFT`). All edit endpoints answer with the full, updated draft.
 */
@Path("/api/v1/developer/apps")
@ApplicationScoped
@Suppress("Unused", "TooManyFunctions", "LargeClass")
class ApiDeveloperResource(
  private val appManagement: AppManagementPort,
  private val appVersionManagement: AppVersionManagementPort,
  private val apiRequest: ApiRequestContext,
  private val httpResponseMetrics: HttpResponseMetrics,
) {

  // region read

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_READ)
  fun listApps(): Response = httpResponseMetrics.timed("api.developer.apps-list") {
    ApiResponses.ok(appManagement.listApps(apiRequest.userId()).sortedBy { it.name.value }.map { ApiDtoMapper.app(it) })
  }

  @GET
  @Path("/{appId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_READ)
  fun getApp(@PathParam("appId") appId: String): Response = httpResponseMetrics.timed("api.developer.app-get") {
    owned(appId) { app ->
      appVersionManagement.listVersions(appId).fold(
        ifLeft = { ApiResponses.domainError(it) },
        ifRight = { versions -> ApiResponses.ok(ApiDeveloperAppDto(ApiDtoMapper.app(app), versions.map { ApiDtoMapper.versionSummary(it) })) },
      )
    }
  }

  @GET
  @Path("/{appId}/versions")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_READ)
  fun listVersions(@PathParam("appId") appId: String): Response = httpResponseMetrics.timed("api.developer.versions-list") {
    owned(appId) { appVersionManagement.listVersions(appId).toVersionsResponse() }
  }

  @GET
  @Path("/{appId}/versions/{versionId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_READ)
  fun getVersion(@PathParam("appId") appId: String, @PathParam("versionId") versionId: String): Response =
    httpResponseMetrics.timed("api.developer.version-get") {
      owned(appId) { appVersionManagement.getVersion(appId, versionId).toVersionResponse() }
    }

  @GET
  @Path("/{appId}/versions/{versionId}/version-bump")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_READ)
  fun versionBump(@PathParam("appId") appId: String, @PathParam("versionId") versionId: String): Response =
    httpResponseMetrics.timed("api.developer.version-bump") {
      owned(appId) {
        appVersionManagement.computeVersionBump(appId, versionId).fold(
          ifLeft = { ApiResponses.domainError(it) },
          ifRight = { ApiResponses.ok(ApiDtoMapper.versionBump(it)) },
        )
      }
    }

  // endregion

  // region release

  @POST
  @Path("/{appId}/versions/publish")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_RELEASE)
  fun publish(@PathParam("appId") appId: String, request: ApiPublishRequest?): Response = httpResponseMetrics.timed("api.developer.version-publish") {
    owned(appId) { appVersionManagement.publishVersion(appId, request?.bumpType, request?.releaseNotes.orEmpty()).toVersionResponse() }
  }

  // endregion

  // region drafts

  @POST
  @Path("/{appId}/versions")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun createDraft(@PathParam("appId") appId: String): Response = httpResponseMetrics.timed("api.developer.draft-create") {
    owned(appId) {
      appVersionManagement.createVersion(appId).fold(
        ifLeft = { ApiResponses.domainError(it) },
        ifRight = { ApiResponses.created(ApiDtoMapper.version(it)) },
      )
    }
  }

  @DELETE
  @Path("/{appId}/versions/{versionId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun deleteDraft(@PathParam("appId") appId: String, @PathParam("versionId") versionId: String): Response =
    httpResponseMetrics.timed("api.developer.draft-delete") {
      owned(appId) {
        appVersionManagement.deleteDraftVersion(appId, versionId).fold(
          ifLeft = { ApiResponses.domainError(it) },
          ifRight = { ApiResponses.noContent() },
        )
      }
    }

  // endregion

  // region draft: entities

  @POST
  @Path("/{appId}/versions/{versionId}/entities")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun addEntity(@PathParam("appId") appId: String, @PathParam("versionId") versionId: String, request: ApiNameRequest?): Response =
    edit("entity-add", appId) { appVersionManagement.addEntity(appId, versionId, request?.name.orEmpty()) }

  @DELETE
  @Path("/{appId}/versions/{versionId}/entities/{entityId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun deleteEntity(@PathParam("appId") appId: String, @PathParam("versionId") versionId: String, @PathParam("entityId") entityId: String): Response =
    edit("entity-delete", appId) { appVersionManagement.deleteEntity(appId, versionId, entityId) }

  @PUT
  @Path("/{appId}/versions/{versionId}/entities/order")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun reorderEntities(@PathParam("appId") appId: String, @PathParam("versionId") versionId: String, request: ApiIdsRequest?): Response =
    edit("entities-reorder", appId) { appVersionManagement.reorderEntities(appId, versionId, request?.ids.orEmpty()) }

  @PUT
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/display-text")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun setEntityDisplayText(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    request: ApiTextRequest?,
  ): Response = edit("entity-display-text", appId) {
    appVersionManagement.updateEntityDisplayText(appId, versionId, entityId, request?.value?.takeIf { it.isNotBlank() })
  }

  @PUT
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/sort-criteria")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun setEntitySortCriteria(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    request: ApiSortCriteriaRequest?,
  ): Response {
    val sortBy = request?.sortBy.orEmpty().map { item ->
      val direction = SortDirection.entries.firstOrNull { it.name == item.direction } ?: return ApiResponses.badRequest("Invalid sort direction: ${item.direction}")
      SortCriteria(propertyId = item.propertyId ?: return ApiResponses.badRequest("propertyId is required"), direction = direction)
    }
    return edit("entity-sort-criteria", appId) { appVersionManagement.updateEntitySortCriteria(appId, versionId, entityId, sortBy) }
  }

  @PUT
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/migration-script")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun setEntityMigrationScript(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    request: ApiTextRequest?,
  ): Response = edit("entity-migration-script", appId) {
    appVersionManagement.updateEntityMigrationScript(appId, versionId, entityId, request?.value?.takeIf { it.isNotBlank() })
  }

  // endregion

  // region draft: migration steps

  @POST
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/migration-steps")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun addMigrationStep(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    request: ApiMigrationStepRequest?,
  ): Response = edit("migration-step-add", appId) { appVersionManagement.addMigrationStep(appId, versionId, entityId, migrationStepInput(request)) }

  @PUT
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/migration-steps/{stepId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun updateMigrationStep(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("stepId") stepId: String,
    request: ApiMigrationStepRequest?,
  ): Response = edit("migration-step-update", appId) {
    appVersionManagement.updateMigrationStep(appId, versionId, entityId, stepId, migrationStepInput(request))
  }

  @DELETE
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/migration-steps/{stepId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun deleteMigrationStep(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("stepId") stepId: String,
  ): Response = edit("migration-step-delete", appId) { appVersionManagement.deleteMigrationStep(appId, versionId, entityId, stepId) }

  @PUT
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/migration-steps/order")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun reorderMigrationSteps(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    request: ApiIdsRequest?,
  ): Response = edit("migration-steps-reorder", appId) { appVersionManagement.reorderMigrationSteps(appId, versionId, entityId, request?.ids.orEmpty()) }

  // endregion

  // region draft: properties

  @POST
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/properties")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun addProperty(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    request: ApiPropertyRequest?,
  ): Response = edit("property-add", appId) {
    appVersionManagement.addProperty(
      appId = appId,
      versionId = versionId,
      entityId = entityId,
      name = request?.name.orEmpty(),
      type = request?.type.orEmpty(),
      nullable = request?.nullable ?: true,
      targetEntityId = request?.targetEntityId?.takeIf { it.isNotBlank() },
      listItemType = request?.listItemType?.takeIf { it.isNotBlank() },
      path = request?.path.orEmpty(),
    )
  }

  @PUT
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun updateProperty(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    request: ApiPropertyRequest?,
  ): Response = edit("property-update", appId) {
    appVersionManagement.updateProperty(
      appId = appId,
      versionId = versionId,
      entityId = entityId,
      propertyId = propertyId,
      name = request?.name.orEmpty(),
      type = request?.type.orEmpty(),
      nullable = request?.nullable ?: true,
      path = request?.path.orEmpty(),
    )
  }

  @DELETE
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun deleteProperty(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    @QueryParam("path") path: String?,
  ): Response = edit("property-delete", appId) { appVersionManagement.deleteProperty(appId, versionId, entityId, propertyId, parsePath(path)) }

  @PUT
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/properties/order")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun reorderProperties(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    request: ApiIdsRequest?,
  ): Response = edit("properties-reorder", appId) {
    appVersionManagement.reorderProperties(appId, versionId, entityId, request?.ids.orEmpty(), request?.path.orEmpty())
  }

  @PUT
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/constraints")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun setPropertyConstraints(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    request: ApiConstraintsRequest?,
  ): Response {
    val constraints = parseConstraints(request) ?: return ApiResponses.badRequest("Invalid constraint")
    return edit("property-constraints", appId) {
      appVersionManagement.setPropertyConstraints(appId, versionId, entityId, propertyId, constraints, request?.path.orEmpty())
    }
  }

  @PUT
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/item-constraints")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun setPropertyItemConstraints(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    request: ApiConstraintsRequest?,
  ): Response {
    val constraints = parseConstraints(request) ?: return ApiResponses.badRequest("Invalid constraint")
    return edit("property-item-constraints", appId) {
      appVersionManagement.setPropertyItemConstraints(appId, versionId, entityId, propertyId, constraints, request?.path.orEmpty())
    }
  }

  @PUT
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/default")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun setPropertyDefault(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    request: ApiTextRequest?,
  ): Response = edit("property-default", appId) {
    appVersionManagement.setPropertyDefault(appId, versionId, entityId, propertyId, request?.value?.takeIf { it.isNotBlank() }, request?.path.orEmpty())
  }

  @PUT
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/smart-default")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun setPropertySmartDefault(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    request: ApiTextRequest?,
  ): Response = edit("property-smart-default", appId) {
    appVersionManagement.setPropertySmartDefault(appId, versionId, entityId, propertyId, request?.value?.takeIf { it.isNotBlank() }, request?.path.orEmpty())
  }

  @PUT
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/value-proposals")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun setPropertyValueProposals(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    request: ApiTextListRequest?,
  ): Response = edit("property-value-proposals", appId) {
    appVersionManagement.setPropertyValueProposals(appId, versionId, entityId, propertyId, request?.values.orEmpty(), request?.path.orEmpty())
  }

  @PUT
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/target-entity")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun setPropertyTargetEntity(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    request: ApiTextRequest?,
  ): Response = edit("property-target-entity", appId) {
    appVersionManagement.setPropertyTargetEntity(appId, versionId, entityId, propertyId, request?.value?.takeIf { it.isNotBlank() }, request?.path.orEmpty())
  }

  @PUT
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/list-item-type")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun setPropertyListItemType(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    request: ApiTextRequest?,
  ): Response = edit("property-list-item-type", appId) {
    appVersionManagement.setPropertyListItemType(appId, versionId, entityId, propertyId, request?.value?.takeIf { it.isNotBlank() }, request?.path.orEmpty())
  }

  @PUT
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/unit")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun setPropertyUnit(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    request: ApiUnitRequest?,
  ): Response = edit("property-unit", appId) {
    appVersionManagement.setPropertyUnit(
      appId = appId,
      versionId = versionId,
      entityId = entityId,
      propertyId = propertyId,
      family = request?.family?.takeIf { it.isNotBlank() },
      storageGranularity = request?.storageGranularity?.takeIf { it.isNotBlank() },
      defaultGranularity = request?.defaultGranularity?.takeIf { it.isNotBlank() },
      path = request?.path.orEmpty(),
    )
  }

  // endregion

  // region draft: computed properties

  @POST
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/computed-properties")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun addComputedProperty(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    request: ApiComputedPropertyRequest?,
  ): Response = edit("computed-property-add", appId) {
    appVersionManagement.addComputedProperty(appId, versionId, entityId, request?.name.orEmpty(), request?.type.orEmpty())
  }

  @PUT
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/computed-properties/{computedPropertyId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun updateComputedProperty(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("computedPropertyId") computedPropertyId: String,
    request: ApiComputedPropertyRequest?,
  ): Response = edit("computed-property-update", appId) {
    appVersionManagement.updateComputedProperty(appId, versionId, entityId, computedPropertyId, request?.name.orEmpty(), request?.type.orEmpty())
  }

  @PUT
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/computed-properties/{computedPropertyId}/script")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun setComputedPropertyScript(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("computedPropertyId") computedPropertyId: String,
    request: ApiTextRequest?,
  ): Response = edit("computed-property-script", appId) {
    appVersionManagement.setComputedPropertyScript(appId, versionId, entityId, computedPropertyId, request?.value?.takeIf { it.isNotBlank() })
  }

  @DELETE
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/computed-properties/{computedPropertyId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun deleteComputedProperty(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("computedPropertyId") computedPropertyId: String,
  ): Response = edit("computed-property-delete", appId) { appVersionManagement.deleteComputedProperty(appId, versionId, entityId, computedPropertyId) }

  @PUT
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/computed-properties/order")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun reorderComputedProperties(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    request: ApiIdsRequest?,
  ): Response = edit("computed-properties-reorder", appId) {
    appVersionManagement.reorderComputedProperties(appId, versionId, entityId, request?.ids.orEmpty())
  }

  // endregion

  // region draft: aggregations

  @POST
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/aggregations")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun addAggregation(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    request: ApiAggregationRequest?,
  ): Response = edit("aggregation-add", appId) { appVersionManagement.addAggregation(appId, versionId, entityId, aggregationInput(request)) }

  @PUT
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/aggregations/{aggregationId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun updateAggregation(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("aggregationId") aggregationId: String,
    request: ApiAggregationRequest?,
  ): Response = edit("aggregation-update", appId) {
    appVersionManagement.updateAggregation(appId, versionId, entityId, aggregationId, aggregationInput(request))
  }

  @DELETE
  @Path("/{appId}/versions/{versionId}/entities/{entityId}/aggregations/{aggregationId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun deleteAggregation(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("aggregationId") aggregationId: String,
  ): Response = edit("aggregation-delete", appId) { appVersionManagement.deleteAggregation(appId, versionId, entityId, aggregationId) }

  // endregion

  // region draft: reports

  @POST
  @Path("/{appId}/versions/{versionId}/reports")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun addReport(@PathParam("appId") appId: String, @PathParam("versionId") versionId: String, request: ApiNameRequest?): Response =
    edit("report-add", appId) { appVersionManagement.addReport(appId, versionId, request?.name.orEmpty()) }

  @PUT
  @Path("/{appId}/versions/{versionId}/reports/{reportId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun updateReport(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("reportId") reportId: String,
    request: ApiReportRequest?,
  ): Response = edit("report-update", appId) { appVersionManagement.updateReport(appId, versionId, reportId, request?.html.orEmpty(), request?.script.orEmpty()) }

  @DELETE
  @Path("/{appId}/versions/{versionId}/reports/{reportId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.DEVELOPER_WRITE)
  fun deleteReport(@PathParam("appId") appId: String, @PathParam("versionId") versionId: String, @PathParam("reportId") reportId: String): Response =
    edit("report-delete", appId) { appVersionManagement.deleteReport(appId, versionId, reportId) }

  // endregion

  /** Runs [block] only if [appId] exists and belongs to the token owner. */
  private fun owned(appId: String, block: (App) -> Response): Response =
    appManagement.getApp(appId, apiRequest.userId()).fold(
      ifLeft = { ApiResponses.domainError(it) },
      ifRight = { block(it) },
    )

  /** Ownership check plus a draft edit answering with the full updated draft. */
  private fun edit(operation: String, appId: String, block: () -> Either<DomainError, AppVersion>): Response =
    httpResponseMetrics.timed("api.developer.$operation") {
      owned(appId) { block().toVersionResponse() }
    }

  private fun Either<DomainError, AppVersion>.toVersionResponse(): Response =
    fold(ifLeft = { ApiResponses.domainError(it) }, ifRight = { ApiResponses.ok(ApiDtoMapper.version(it)) })

  private fun Either<DomainError, List<AppVersion>>.toVersionsResponse(): Response =
    fold(ifLeft = { ApiResponses.domainError(it) }, ifRight = { versions -> ApiResponses.ok(versions.map { ApiDtoMapper.version(it) }) })

  /** Null if any constraint is invalid. */
  private fun parseConstraints(request: ApiConstraintsRequest?): Set<PropertyConstraint>? {
    val items = request?.constraints.orEmpty()
    val parsed = items.mapNotNull { item -> ApiDtoMapper.parseConstraint(ApiConstraintDto(item.type.orEmpty(), item.value)) }
    return if (parsed.size == items.size) parsed.toSet() else null
  }

  private fun parsePath(path: String?): List<String> = path?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

  private fun migrationStepInput(request: ApiMigrationStepRequest?) = MigrationStepInput(
    type = request?.type.orEmpty(),
    propertyId = request?.propertyId,
    sourcePropertyId = request?.sourcePropertyId,
    targetPropertyId = request?.targetPropertyId,
    sourceGranularity = request?.sourceGranularity,
    value = request?.value,
  )

  private fun aggregationInput(request: ApiAggregationRequest?) = AggregationInput(
    name = request?.name.orEmpty(),
    function = request?.function.orEmpty(),
    sourceProperty = request?.sourceProperty.orEmpty(),
    refPath = request?.refPath,
    timeBucket = request?.timeBucket,
    timeProperty = request?.timeProperty,
    groupBy = request?.groupBy,
    periodStartDay = request?.periodStartDay,
    periodStartMonth = request?.periodStartMonth,
  )
}
