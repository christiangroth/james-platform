package de.chrgroth.james.platform.adapter.`in`.web

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.TextNode
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.imports.DryRunIssue
import de.chrgroth.james.platform.domain.model.imports.DryRunObject
import de.chrgroth.james.platform.domain.model.imports.DryRunReport
import de.chrgroth.james.platform.domain.model.imports.FieldMapping
import de.chrgroth.james.platform.domain.model.imports.FieldMappingConversion
import de.chrgroth.james.platform.domain.model.imports.FilterMode
import de.chrgroth.james.platform.domain.model.imports.FilterOperator
import de.chrgroth.james.platform.domain.model.imports.FilterRule
import de.chrgroth.james.platform.domain.model.imports.FilterView
import de.chrgroth.james.platform.domain.model.imports.ImportConnection
import de.chrgroth.james.platform.domain.model.imports.ImportDefinition
import de.chrgroth.james.platform.domain.model.imports.ImportJob
import de.chrgroth.james.platform.domain.model.imports.MappingIssue
import de.chrgroth.james.platform.domain.model.imports.MappingView
import de.chrgroth.james.platform.domain.model.imports.ReferenceLookup
import de.chrgroth.james.platform.domain.model.imports.ReferenceLookupCriterion
import de.chrgroth.james.platform.domain.model.imports.SchemaProperty
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant

// DTOs of the import part of the `/api/v1` personal access token API (see ApiImportResource). Filter rules and field mappings are used both in
// responses and in request bodies, so they carry an explicit @JsonCreator constructor (see ApiRequests.kt) and nullable fields.

/** An import connection; the stored bearer token is never returned, only whether one is set. */
data class ApiImportConnectionDto(
  val id: String,
  val name: String,
  val baseUrl: String,
  val hasBearerToken: Boolean,
  val createdAt: Instant,
  val lastChangedAt: Instant,
)

data class ApiImportDefinitionDto(
  val id: String,
  val name: String,
  val connectionId: String,
  val urlPostfix: String?,
  val targetEntityDefinitionId: String,
  val selectedDataPath: String?,
  val filterRules: List<ApiFilterRuleDto>,
  val fieldMappings: List<ApiFieldMappingDto>?,
  val configured: Boolean,
  val schedule: String?,
  val lastRunAt: Instant?,
  val createdAt: Instant,
  val lastChangedAt: Instant,
)

data class ApiImportJobSummaryDto(
  val id: String,
  val importDefinitionId: String,
  val installedAppId: String,
  val status: String,
  val triggeredBy: String,
  val addedCount: Int?,
  val replacedCount: Int?,
  val discardedCount: Int?,
  val createdAt: Instant,
  val lastChangedAt: Instant,
)

/** An import job with what was detected in the fetched payload (the payload itself is not returned, use the filter/mapping samples). */
data class ApiImportJobDto(
  val job: ApiImportJobSummaryDto,
  val definition: ApiImportDefinitionDto,
  val detectedDataPaths: List<ApiDataPathDto>,
  val detectedSchema: List<ApiSchemaPropertyDto>,
  val filteredSchema: List<ApiSchemaPropertyDto>,
)

data class ApiDataPathDto(
  val path: String,
  val size: Int,
)

data class ApiSchemaPropertyDto(
  val path: String,
  val typeCounts: Map<String, Int>,
  val mandatory: Boolean,
  val numericMin: Double?,
  val numericMax: Double?,
  val stringLengthCounts: Map<String, Int>,
)

data class ApiFilterRuleDto @JsonCreator constructor(
  @param:JsonProperty("mode") val mode: String?,
  @param:JsonProperty("sourcePath") val sourcePath: String?,
  @param:JsonProperty("operator") val operator: String?,
  @param:JsonProperty("value") val value: String?,
  @param:JsonProperty("includeAbsent") val includeAbsent: Boolean?,
)

data class ApiFieldMappingDto @JsonCreator constructor(
  @param:JsonProperty("targetPropertyId") val targetPropertyId: String?,
  @param:JsonProperty("sourcePath") val sourcePath: String?,
  @param:JsonProperty("conversion") val conversion: String?,
  @param:JsonProperty("importGranularity") val importGranularity: String?,
  @param:JsonProperty("fallbackValue") val fallbackValue: String?,
  @param:JsonProperty("referenceLookup") val referenceLookup: List<ApiReferenceLookupCriterionDto>?,
)

data class ApiReferenceLookupCriterionDto @JsonCreator constructor(
  @param:JsonProperty("targetPropertyId") val targetPropertyId: String?,
  @param:JsonProperty("sourcePath") val sourcePath: String?,
)

data class ApiImportFilterDto(
  val importJobId: String,
  val rules: List<ApiFilterRuleDto>,
  val totalRecordCount: Int,
  val matchingRecordCount: Int,
)

/** One source record of a job; [sourceData] is the record as JSON, null when the requested index is out of range. */
data class ApiImportRecordDto(
  val total: Int,
  val index: Int,
  val sourceData: JsonNode?,
)

data class ApiMappingIssueDto(
  val type: String,
  val targetPropertyId: String,
  val blocking: Boolean,
  val details: Map<String, String>,
)

/** The target entity, the current mapping and its validation; [ready] is null as long as no mapping was saved. */
data class ApiImportMappingDto(
  val importJobId: String,
  val targetEntity: ApiEntityDto,
  val fieldMappings: List<ApiFieldMappingDto>?,
  val ready: Boolean?,
  val issues: List<ApiMappingIssueDto>,
)

data class ApiDryRunIssueDto(
  val type: String,
  val targetPropertyId: String,
  val violation: String?,
  val violationCode: String?,
  val staticallyChecked: Boolean,
  val expected: Boolean,
)

data class ApiDryRunObjectDto(
  val index: Int,
  val sourceData: JsonNode?,
  val targetData: Map<String, String?>,
  val issues: List<ApiDryRunIssueDto>,
)

/**
 * Result of a dry-run: what an accept would save (valid), skip by design (fan-in duplicates) and discard (invalid). The object lists are capped
 * at the requested limit, the counts are always complete.
 */
data class ApiDryRunDto(
  val importJobId: String,
  val totalCount: Int,
  val validCount: Int,
  val skippedCount: Int,
  val invalidCount: Int,
  val invalidObjects: List<ApiDryRunObjectDto>,
  val skippedObjects: List<ApiDryRunObjectDto>,
  val validObjects: List<ApiDryRunObjectDto>,
)

data class ApiMappingSampleDto(
  val total: Int,
  val index: Int,
  val result: ApiDryRunObjectDto?,
)

@ApplicationScoped
@Suppress("TooManyFunctions")
class ApiImportDtoMapper(private val objectMapper: ObjectMapper) {

  fun connection(connection: ImportConnection) = ApiImportConnectionDto(
    id = connection.id.value,
    name = connection.name,
    baseUrl = connection.baseUrl,
    hasBearerToken = !connection.encryptedBearerToken.isNullOrBlank(),
    createdAt = connection.createdAt,
    lastChangedAt = connection.lastChangedAt,
  )

  fun definition(definition: ImportDefinition) = ApiImportDefinitionDto(
    id = definition.id.value,
    name = definition.name,
    connectionId = definition.connectionId.value,
    urlPostfix = definition.urlPostfix,
    targetEntityDefinitionId = definition.targetEntityDefinitionId.value,
    selectedDataPath = definition.selectedDataPath,
    filterRules = definition.filterRules.map { filterRule(it) },
    fieldMappings = definition.mapping?.fieldMappings?.map { fieldMapping(it) },
    configured = definition.selectedDataPath != null && definition.mapping != null,
    schedule = definition.schedule,
    lastRunAt = definition.lastRunAt,
    createdAt = definition.createdAt,
    lastChangedAt = definition.lastChangedAt,
  )

  fun jobSummary(job: ImportJob) = ApiImportJobSummaryDto(
    id = job.id.value,
    importDefinitionId = job.importDefinitionId.value,
    installedAppId = job.installedAppId.value,
    status = job.status.name,
    triggeredBy = job.triggeredBy.name,
    addedCount = job.addedCount,
    replacedCount = job.replacedCount,
    discardedCount = job.discardedCount,
    createdAt = job.createdAt,
    lastChangedAt = job.lastChangedAt,
  )

  fun job(job: ImportJob, definition: ImportDefinition) = ApiImportJobDto(
    job = jobSummary(job),
    definition = definition(definition),
    detectedDataPaths = job.detectedDataPaths.map { ApiDataPathDto(it.path, it.size) },
    detectedSchema = job.detectedSchema.map { schemaProperty(it) },
    filteredSchema = job.filteredSchema.map { schemaProperty(it) },
  )

  fun filter(view: FilterView) = ApiImportFilterDto(
    importJobId = view.importJob.id.value,
    rules = view.importDefinition.filterRules.map { filterRule(it) },
    totalRecordCount = view.totalRecordCount,
    matchingRecordCount = view.matchingRecordCount,
  )

  fun record(total: Int, index: Int, json: String?) = ApiImportRecordDto(total, index, json?.let { parseJson(it) })

  fun mapping(view: MappingView) = ApiImportMappingDto(
    importJobId = view.importJob.id.value,
    targetEntity = ApiDtoMapper.entity(view.targetEntityDefinition),
    fieldMappings = view.importDefinition.mapping?.fieldMappings?.map { fieldMapping(it) },
    ready = view.validation?.isReady,
    issues = view.validation?.issues.orEmpty().map { mappingIssue(it) },
  )

  fun dryRun(report: DryRunReport, limit: Int) = ApiDryRunDto(
    importJobId = report.importJobId.value,
    totalCount = report.totalCount,
    validCount = report.validCount,
    skippedCount = report.skippedCount,
    invalidCount = report.invalidCount,
    invalidObjects = report.invalidObjects.take(limit).map { dryRunObject(it) },
    skippedObjects = report.skippedObjects.take(limit).map { dryRunObject(it) },
    validObjects = report.validObjects.take(limit).map { dryRunObject(it) },
  )

  fun dryRunObject(obj: DryRunObject) = ApiDryRunObjectDto(
    index = obj.index,
    sourceData = parseJson(obj.sourceDataJson),
    targetData = obj.targetData.mapKeys { it.key.value },
    issues = obj.issues.map { dryRunIssue(it) },
  )

  private fun dryRunIssue(issue: DryRunIssue): ApiDryRunIssueDto = when (issue) {
    is DryRunIssue.MissingMandatoryValue ->
      ApiDryRunIssueDto("MISSING_MANDATORY_VALUE", issue.targetPropertyId.value, null, null, issue.staticallyChecked, issue.expected)
    is DryRunIssue.ConstraintViolated -> ApiDryRunIssueDto(
      "CONSTRAINT_VIOLATED",
      issue.targetPropertyId.value,
      issue.violation.toString(),
      issue.violation.code,
      issue.staticallyChecked,
      issue.expected,
    )
  }

  private fun mappingIssue(issue: MappingIssue): ApiMappingIssueDto {
    val blocking = issue !is MappingIssue.NotStaticallyValidated
    val (type, details) = when (issue) {
      is MappingIssue.MissingMandatoryField -> "MISSING_MANDATORY_FIELD" to emptyMap()
      is MappingIssue.IncompatibleType ->
        "INCOMPATIBLE_TYPE" to mapOf("sourceType" to issue.sourceType.name, "targetType" to issue.targetType.name)
      is MappingIssue.NumericRangeViolation -> "NUMERIC_RANGE_VIOLATION" to mapOfNotNull(
        "constraintMin" to issue.constraintMin?.toString(),
        "constraintMax" to issue.constraintMax?.toString(),
        "observedMin" to issue.observedMin.toString(),
        "observedMax" to issue.observedMax.toString(),
      )
      is MappingIssue.StringLengthViolation -> "STRING_LENGTH_VIOLATION" to mapOfNotNull(
        "constraintMin" to issue.constraintMin?.toString(),
        "constraintMax" to issue.constraintMax?.toString(),
        "observedMinLength" to issue.observedMinLength.toString(),
        "observedMaxLength" to issue.observedMaxLength.toString(),
      )
      is MappingIssue.NotStaticallyValidated -> "NOT_STATICALLY_VALIDATED" to mapOf("regex" to issue.regex)
      is MappingIssue.ReferenceLookupMissingCriteria -> "REFERENCE_LOOKUP_MISSING_CRITERIA" to emptyMap()
      is MappingIssue.ReferenceLookupInvalidCriterion ->
        "REFERENCE_LOOKUP_INVALID_CRITERION" to mapOf("criterionPropertyId" to issue.criterionPropertyId.value)
      is MappingIssue.ReferenceLookupIncompatibleType -> "REFERENCE_LOOKUP_INCOMPATIBLE_TYPE" to mapOf(
        "criterionPropertyId" to issue.criterionPropertyId.value,
        "sourceType" to issue.sourceType.name,
        "targetType" to issue.targetType.name,
      )
      is MappingIssue.FallbackValueViolatesConstraint ->
        "FALLBACK_VALUE_VIOLATES_CONSTRAINT" to mapOf("violation" to issue.violation.toString(), "violationCode" to issue.violation.code)
      is MappingIssue.MissingImportGranularity -> "MISSING_IMPORT_GRANULARITY" to emptyMap()
    }
    return ApiMappingIssueDto(type, issue.targetPropertyId.value, blocking, details)
  }

  private fun schemaProperty(property: SchemaProperty) = ApiSchemaPropertyDto(
    path = property.path,
    typeCounts = property.typeCounts.mapKeys { it.key.name },
    mandatory = property.mandatory,
    numericMin = property.numericRange?.min,
    numericMax = property.numericRange?.max,
    stringLengthCounts = property.stringLengthCounts.mapKeys { it.key.toString() },
  )

  private fun filterRule(rule: FilterRule) = ApiFilterRuleDto(rule.mode.name, rule.sourcePath, rule.operator.name, rule.value, rule.includeAbsent)

  private fun fieldMapping(mapping: FieldMapping) = ApiFieldMappingDto(
    targetPropertyId = mapping.targetPropertyId.value,
    sourcePath = mapping.sourcePath,
    conversion = mapping.conversion.name,
    importGranularity = mapping.importGranularity,
    fallbackValue = mapping.fallbackValue,
    referenceLookup = mapping.referenceLookup?.criteria?.map { ApiReferenceLookupCriterionDto(it.targetPropertyId.value, it.sourcePath) },
  )

  private fun parseJson(json: String): JsonNode = runCatching { objectMapper.readTree(json) }.getOrElse { TextNode(json) }

  private fun mapOfNotNull(vararg pairs: Pair<String, String?>): Map<String, String> =
    pairs.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()

  companion object {

    /** Parses filter rules strictly: an unknown mode or operator, or a blank source path, is reported instead of being dropped silently. */
    fun parseFilterRules(rules: List<ApiFilterRuleDto>): Result<List<FilterRule>> = runCatching {
      rules.mapIndexed { index, rule ->
        FilterRule(
          mode = enumOrFail<FilterMode>(rule.mode, "rules[$index].mode"),
          sourcePath = rule.sourcePath?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("rules[$index].sourcePath is required"),
          operator = enumOrFail<FilterOperator>(rule.operator, "rules[$index].operator"),
          value = rule.value,
          includeAbsent = rule.includeAbsent ?: false,
        )
      }
    }

    /**
     * Parses field mappings strictly; a mapping needs a target property and at least one of source path, fallback value or reference lookup.
     * The conversion defaults to `NONE`.
     */
    fun parseFieldMappings(mappings: List<ApiFieldMappingDto>): Result<List<FieldMapping>> = runCatching {
      mappings.mapIndexed { index, mapping ->
        val targetPropertyId = mapping.targetPropertyId?.takeIf { it.isNotBlank() }
          ?: throw IllegalArgumentException("fieldMappings[$index].targetPropertyId is required")
        val criteria = mapping.referenceLookup.orEmpty().mapIndexed { criterionIndex, criterion ->
          val path = "fieldMappings[$index].referenceLookup[$criterionIndex]"
          ReferenceLookupCriterion(
            PropertyId(criterion.targetPropertyId?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("$path.targetPropertyId is required")),
            criterion.sourcePath?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("$path.sourcePath is required"),
          )
        }
        val sourcePath = mapping.sourcePath?.takeIf { it.isNotBlank() }
        val fallbackValue = mapping.fallbackValue?.takeIf { it.isNotBlank() }
        require(sourcePath != null || fallbackValue != null || criteria.isNotEmpty()) {
          "fieldMappings[$index] needs a sourcePath, a fallbackValue or a referenceLookup"
        }
        FieldMapping(
          targetPropertyId = PropertyId(targetPropertyId),
          sourcePath = sourcePath,
          conversion = mapping.conversion?.takeIf { it.isNotBlank() }?.let { enumOrFail<FieldMappingConversion>(it, "fieldMappings[$index].conversion") }
            ?: FieldMappingConversion.NONE,
          importGranularity = mapping.importGranularity?.takeIf { it.isNotBlank() },
          fallbackValue = fallbackValue,
          referenceLookup = criteria.takeIf { it.isNotEmpty() }?.let { ReferenceLookup(it) },
        )
      }
    }

    private inline fun <reified E : Enum<E>> enumOrFail(value: String?, field: String): E =
      enumValues<E>().firstOrNull { it.name == value }
        ?: throw IllegalArgumentException("$field must be one of ${enumValues<E>().joinToString { it.name }}")
  }
}
