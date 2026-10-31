package de.chrgroth.james.platform.adapter.`in`.web

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

// Request bodies of the `/api/v1` personal access token API. They use an explicit @JsonCreator constructor (like SortCriteriaRequest), so Jackson
// does not need Kotlin reflection in native mode; every field is nullable so a missing field yields a 400 instead of a deserialization error.

/**
 * Values of a data object, keyed by property ID: a string for scalar properties, a list of strings for `LIST` properties and a nested map
 * (property ID to value) for `OBJECT` properties. Values are given in the same format as entered in the UI.
 */
data class ApiAppDataRequest @JsonCreator constructor(
  @param:JsonProperty("entityTypeId") val entityTypeId: String?,
  @param:JsonProperty("values") val values: Map<String, Any?>?,
)

data class ApiNameRequest @JsonCreator constructor(
  @param:JsonProperty("name") val name: String?,
)

data class ApiIdsRequest @JsonCreator constructor(
  @param:JsonProperty("ids") val ids: List<String>?,
  @param:JsonProperty("path") val path: List<String>?,
)

data class ApiTextRequest @JsonCreator constructor(
  @param:JsonProperty("value") val value: String?,
  @param:JsonProperty("path") val path: List<String>?,
)

data class ApiTextListRequest @JsonCreator constructor(
  @param:JsonProperty("values") val values: List<String>?,
  @param:JsonProperty("path") val path: List<String>?,
)

data class ApiSortCriteriaRequest @JsonCreator constructor(
  @param:JsonProperty("sortBy") val sortBy: List<ApiSortCriteriaItem>?,
)

data class ApiSortCriteriaItem @JsonCreator constructor(
  @param:JsonProperty("propertyId") val propertyId: String?,
  @param:JsonProperty("direction") val direction: String?,
)

data class ApiPropertyRequest @JsonCreator constructor(
  @param:JsonProperty("name") val name: String?,
  @param:JsonProperty("type") val type: String?,
  @param:JsonProperty("nullable") val nullable: Boolean?,
  @param:JsonProperty("targetEntityId") val targetEntityId: String?,
  @param:JsonProperty("listItemType") val listItemType: String?,
  @param:JsonProperty("path") val path: List<String>?,
)

data class ApiConstraintsRequest @JsonCreator constructor(
  @param:JsonProperty("constraints") val constraints: List<ApiConstraintItem>?,
  @param:JsonProperty("path") val path: List<String>?,
)

data class ApiConstraintItem @JsonCreator constructor(
  @param:JsonProperty("type") val type: String?,
  @param:JsonProperty("value") val value: String?,
)

data class ApiUnitRequest @JsonCreator constructor(
  @param:JsonProperty("family") val family: String?,
  @param:JsonProperty("storageGranularity") val storageGranularity: String?,
  @param:JsonProperty("defaultGranularity") val defaultGranularity: String?,
  @param:JsonProperty("path") val path: List<String>?,
)

data class ApiComputedPropertyRequest @JsonCreator constructor(
  @param:JsonProperty("name") val name: String?,
  @param:JsonProperty("type") val type: String?,
)

data class ApiAggregationRequest @JsonCreator constructor(
  @param:JsonProperty("name") val name: String?,
  @param:JsonProperty("function") val function: String?,
  @param:JsonProperty("sourceProperty") val sourceProperty: String?,
  @param:JsonProperty("refPath") val refPath: String?,
  @param:JsonProperty("timeBucket") val timeBucket: String?,
  @param:JsonProperty("timeProperty") val timeProperty: String?,
  @param:JsonProperty("groupBy") val groupBy: String?,
  @param:JsonProperty("periodStartDay") val periodStartDay: String?,
  @param:JsonProperty("periodStartMonth") val periodStartMonth: String?,
)

data class ApiMigrationStepRequest @JsonCreator constructor(
  @param:JsonProperty("type") val type: String?,
  @param:JsonProperty("propertyId") val propertyId: String?,
  @param:JsonProperty("sourcePropertyId") val sourcePropertyId: String?,
  @param:JsonProperty("targetPropertyId") val targetPropertyId: String?,
  @param:JsonProperty("sourceGranularity") val sourceGranularity: String?,
  @param:JsonProperty("value") val value: String?,
)

data class ApiReportRequest @JsonCreator constructor(
  @param:JsonProperty("html") val html: String?,
  @param:JsonProperty("script") val script: String?,
)

data class ApiPublishRequest @JsonCreator constructor(
  @param:JsonProperty("bumpType") val bumpType: String?,
  @param:JsonProperty("releaseNotes") val releaseNotes: String?,
)
