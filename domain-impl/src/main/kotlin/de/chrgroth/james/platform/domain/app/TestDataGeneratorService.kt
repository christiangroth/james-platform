package de.chrgroth.james.platform.domain.app

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.error.TestDataGeneratorError
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.encodeListValue
import de.chrgroth.james.platform.domain.model.app.encodeObjectValue
import de.chrgroth.james.platform.domain.port.`in`.app.AppDataPort
import de.chrgroth.james.platform.domain.port.`in`.app.TestDataGeneratorPort
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random

/**
 * Implements the automatic test data generator (see docs/dev-tests.md, "Phase 1"). One generation strategy per [PropertyType],
 * respecting every [PropertyConstraint], [Property.nullable], and (for `LONG`/`DOUBLE`) the property's [de.chrgroth.james.platform.domain.model.app.PropertyUnit] -
 * generated numeric values are already expressed in the property's fixed `storageGranularity` (see ADR 0016), the same
 * representation constraints and [AppData.data] use, so no unit-text conversion is needed here.
 *
 * Computed Properties and Smart Defaults are intentionally never generated; they stay derived from the generated (non-computed)
 * values exactly as for real data. Every generated object is run through [AppDataPort.validateEntityData] (which itself uses
 * [de.chrgroth.james.platform.domain.port.`in`.app.PropertyConstraintPort.checkValue]) before being persisted, as a safety net -
 * generation failures surface as [TestDataGeneratorError.GENERATION_FAILED] rather than silently storing invalid data.
 */
@ApplicationScoped
@Suppress("Unused", "TooManyFunctions")
class TestDataGeneratorService(
  private val appRepository: AppRepositoryPort,
  private val appVersionRepository: AppVersionRepositoryPort,
  private val installedAppRepository: InstalledAppRepositoryPort,
  private val appDataRepository: AppDataRepositoryPort,
  private val appDataPort: AppDataPort,
) : TestDataGeneratorPort {

  override fun generateTestData(
    appId: String,
    installedAppId: String,
    entityId: String,
    count: Int,
    developerId: String,
    seed: Long?,
  ): Either<DomainError, List<AppData>> {
    val app = appRepository.findById(AppId(appId))
    if (app == null || app.developerId != developerId) {
      logger.warn { "Generate test data failed: app not found: $appId for developer: $developerId" }
      return TestDataGeneratorError.APP_NOT_FOUND.left()
    }

    val installedApp = installedAppRepository.findById(InstalledAppId(installedAppId))
    if (installedApp == null || installedApp.appId != app.id || !installedApp.isTest) {
      logger.warn { "Generate test data failed: test installation not found: $installedAppId for app: $appId" }
      return TestDataGeneratorError.INSTALLATION_NOT_FOUND.left()
    }

    val appVersion = resolveAppVersion(installedApp)
    if (appVersion == null) {
      logger.warn { "Generate test data failed: app version not found for installed app: $installedAppId" }
      return TestDataGeneratorError.INSTALLATION_NOT_FOUND.left()
    }

    val entityDef = appVersion.entityDefinitions.find { it.id.value == entityId }
    if (entityDef == null) {
      logger.warn { "Generate test data failed: entity not found: $entityId in version ${appVersion.id.value}" }
      return TestDataGeneratorError.ENTITY_NOT_FOUND.left()
    }

    if (count !in 1..MAX_GENERATE_COUNT) {
      logger.warn { "Generate test data failed: invalid count: $count" }
      return TestDataGeneratorError.INVALID_COUNT.left()
    }

    val context = GenerationContext(
      rng = seed?.let { Random(it) } ?: Random.Default,
      installedApp = installedApp,
      entitiesById = appVersion.entityDefinitions.associateBy { it.id },
      visiting = mutableSetOf(entityDef.id),
    )
    val result = generateEntityObjects(entityDef, count, context)
    result.onRight { logger.info { "Test data generated: ${it.size} object(s) for entity $entityId in installed app: $installedAppId" } }
    return result
  }

  /** Test installations (see docs/dev-tests.md) pin their version by id since a DRAFT version has no [InstalledApp.installedVersionNumber] yet. */
  private fun resolveAppVersion(installedApp: InstalledApp): AppVersion? =
    installedApp.installedVersionId?.let { appVersionRepository.findById(it) }
      ?: appVersionRepository.findByAppIdAndVersionNumber(installedApp.appId, installedApp.installedVersionNumber)

  /**
   * Generates and persists [count] objects for [entityDef]. Shared by the top-level requested entity and by [ensureTargetsExist]
   * for `REF` target entities that have no data yet. Returns only the objects created by this specific call - transitively
   * generated prerequisite objects (for referenced entities) are persisted as a side effect but not included in the result.
   */
  private fun generateEntityObjects(entityDef: EntityDefinition, count: Int, context: GenerationContext): Either<DomainError, List<AppData>> {
    val existingSiblings = appDataRepository.findAllByInstalledAppIdAndEntityType(context.installedApp.id, entityDef.id)
    val uniquenessTracking = entityDef.properties.associate { property ->
      property.id.value to existingSiblings.mapNotNull { it.data[property.id.value] }.toMutableSet()
    }

    val generated = mutableListOf<AppData>()
    repeat(count) {
      val dataMap = LinkedHashMap<String, String?>()
      for (property in entityDef.properties) {
        val value = generateStoredValue(property, uniquenessTracking[property.id.value], context).getOrElse { return it.left() }
        dataMap[property.id.value] = value
      }

      val validation = appDataPort.validateEntityData(entityDef, dataMap, context.installedApp.id.value)
      if (validation.isLeft()) {
        logger.warn { "Test data generation safety net rejected a generated object for entity ${entityDef.name}: ${validation.leftOrNull()}" }
        return TestDataGeneratorError.GENERATION_FAILED.left()
      }

      val now = Instant.now()
      val appData = AppData(
        id = AppDataId(UUID.randomUUID().toString()),
        userId = context.installedApp.userId,
        installedAppId = context.installedApp.id,
        appVersion = context.installedApp.installedVersionNumber,
        lastValidatedWithVersion = context.installedApp.installedVersionNumber,
        entityType = entityDef.id,
        objectVersion = 1,
        createdAt = now,
        lastChangedAt = now,
        data = dataMap,
      )
      appDataRepository.save(appData)
      generated += appData
    }
    return generated.right()
  }

  /** Generates the fully-encoded, ready-to-store value for [property] (uniqueness-checked against [existingValues] when non-null and `UniqueKey` applies). */
  private fun generateStoredValue(property: Property, existingValues: MutableSet<String>?, context: GenerationContext): Either<DomainError, String?> {
    if (property.nullable && context.rng.nextInt(PERCENT_RANGE) < NULL_PROBABILITY_PERCENT) {
      return null.right()
    }
    return when (property.type) {
      PropertyType.LONG -> generateUnique(property, existingValues) { generateLong(property.constraints, context.rng).toString() }.right()
      PropertyType.DOUBLE -> generateUnique(property, existingValues) { generateDouble(property.constraints, context.rng) }.right()
      PropertyType.BOOLEAN -> context.rng.nextBoolean().toString().right()
      PropertyType.STRING -> generateStringValue(property, existingValues, context.rng)
      PropertyType.DATE -> generateUnique(property, existingValues) { generateDate(property.constraints, context.rng).toString() }.right()
      PropertyType.TIME -> generateUnique(property, existingValues) { generateTime(property.constraints, context.rng).toString() }.right()
      PropertyType.DATETIME -> generateUnique(property, existingValues) { generateDateTime(property.constraints, context.rng).toString() }.right()
      PropertyType.REF -> generateRefValue(property, context)
      PropertyType.LIST -> generateListValue(property, context)
      PropertyType.OBJECT -> generateRawObjectMap(property, context).map { encodeObjectValue(it) }
    }
  }

  /** Regenerates [produce] until it yields a value not already in [existingValues] (or [PropertyConstraint.UniqueKey] does not apply), up to [MAX_UNIQUE_ATTEMPTS] tries. */
  private fun generateUnique(property: Property, existingValues: MutableSet<String>?, produce: () -> String): String {
    if (existingValues == null || PropertyConstraint.UniqueKey !in property.constraints) {
      return produce()
    }
    var candidate = produce()
    var attempts = 0
    while (candidate in existingValues && attempts < MAX_UNIQUE_ATTEMPTS) {
      candidate = produce()
      attempts++
    }
    existingValues += candidate
    return candidate
  }

  private fun generateStringValue(property: Property, existingValues: MutableSet<String>?, rng: Random): Either<DomainError, String?> {
    val minLen = property.constraints.filterIsInstance<PropertyConstraint.MinLength>().firstOrNull()?.min ?: 0
    val maxLen = property.constraints.filterIsInstance<PropertyConstraint.MaxLength>().firstOrNull()?.max
      ?: (minLen + DEFAULT_STRING_LENGTH_SPAN)
    val pattern = property.constraints.filterIsInstance<PropertyConstraint.Pattern>().firstOrNull()?.regex

    fun produceCandidate(): String? {
      if (pattern == null) {
        val length = if (maxLen <= minLen) minLen else minLen + rng.nextInt(maxLen - minLen + 1)
        return randomAlphaNumeric(rng, length)
      }
      repeat(MAX_PATTERN_ATTEMPTS) {
        val candidate = TestDataPatternGenerator.generate(pattern, rng)
        if (candidate.length in minLen..maxLen && Regex(pattern).matches(candidate)) return candidate
      }
      return null
    }

    val hasUniqueKey = existingValues != null && PropertyConstraint.UniqueKey in property.constraints
    var candidate = produceCandidate() ?: return TestDataGeneratorError.GENERATION_FAILED.left()
    if (hasUniqueKey) {
      var attempts = 0
      while (candidate in existingValues && attempts < MAX_UNIQUE_ATTEMPTS) {
        candidate = produceCandidate() ?: return TestDataGeneratorError.GENERATION_FAILED.left()
        attempts++
      }
      existingValues += candidate
    }
    return candidate.right()
  }

  private fun generateLong(constraints: Set<PropertyConstraint>, rng: Random): Long {
    val min = constraints.filterIsInstance<PropertyConstraint.MinLong>().firstOrNull()?.min
    val max = constraints.filterIsInstance<PropertyConstraint.MaxLong>().firstOrNull()?.max
    val step = constraints.filterIsInstance<PropertyConstraint.StepLong>().firstOrNull()?.step?.takeIf { it > 0 }
    val effectiveMin = min ?: (max?.let { it - DEFAULT_LONG_RANGE } ?: -DEFAULT_LONG_RANGE)
    val effectiveMax = max ?: (min?.let { it + DEFAULT_LONG_RANGE } ?: DEFAULT_LONG_RANGE)
    if (effectiveMin >= effectiveMax) return effectiveMin
    if (step == null) return effectiveMin + rng.nextLong(effectiveMax - effectiveMin + 1)

    var candidateMin = Math.floorDiv(effectiveMin, step) * step
    if (candidateMin < effectiveMin) candidateMin += step
    val candidateMax = Math.floorDiv(effectiveMax, step) * step
    if (candidateMax < candidateMin) return candidateMin
    val count = (candidateMax - candidateMin) / step + 1
    return candidateMin + rng.nextLong(count) * step
  }

  private fun generateDouble(constraints: Set<PropertyConstraint>, rng: Random): String {
    val min = constraints.filterIsInstance<PropertyConstraint.MinDouble>().firstOrNull()?.min
    val max = constraints.filterIsInstance<PropertyConstraint.MaxDouble>().firstOrNull()?.max
    val step = constraints.filterIsInstance<PropertyConstraint.StepDouble>().firstOrNull()?.step?.takeIf { it > 0.0 }
    val effectiveMin = min ?: (max?.minus(DEFAULT_DOUBLE_RANGE) ?: -DEFAULT_DOUBLE_RANGE)
    val effectiveMax = max ?: (min?.plus(DEFAULT_DOUBLE_RANGE) ?: DEFAULT_DOUBLE_RANGE)
    if (effectiveMin >= effectiveMax) return formatDouble(effectiveMin)

    if (step == null) {
      val raw = effectiveMin + rng.nextDouble() * (effectiveMax - effectiveMin)
      val rounded = BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP).toDouble().coerceIn(effectiveMin, effectiveMax)
      return formatDouble(rounded)
    }

    val stepBd = BigDecimal.valueOf(step)
    val minBd = BigDecimal.valueOf(effectiveMin)
    val maxBd = BigDecimal.valueOf(effectiveMax)
    val candidateMinBd = minBd.divide(stepBd, 0, RoundingMode.CEILING).multiply(stepBd)
    val candidateMaxBd = maxBd.divide(stepBd, 0, RoundingMode.FLOOR).multiply(stepBd)
    if (candidateMaxBd < candidateMinBd) return formatDouble(candidateMinBd.toDouble())
    val stepCount = candidateMaxBd.subtract(candidateMinBd).divide(stepBd, 0, RoundingMode.FLOOR).toLong()
    val value = candidateMinBd.add(stepBd.multiply(BigDecimal.valueOf(rng.nextLong(stepCount + 1))))
    return formatDouble(value.toDouble())
  }

  private fun formatDouble(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

  private fun randomAlphaNumeric(rng: Random, length: Int): String = (0 until length).map { ALPHANUMERIC[rng.nextInt(ALPHANUMERIC.size)] }.joinToString("")

  private fun generateDate(constraints: Set<PropertyConstraint>, rng: Random): LocalDate {
    val min = constraints.filterIsInstance<PropertyConstraint.MinDate>().firstOrNull()?.min ?: LocalDate.now().minusYears(1)
    val max = constraints.filterIsInstance<PropertyConstraint.MaxDate>().firstOrNull()?.max ?: LocalDate.now().plusYears(1)
    if (!min.isBefore(max)) return min
    val range = ChronoUnit.DAYS.between(min, max)
    return min.plusDays(rng.nextLong(range + 1))
  }

  private fun generateTime(constraints: Set<PropertyConstraint>, rng: Random): LocalTime {
    val min = constraints.filterIsInstance<PropertyConstraint.MinTime>().firstOrNull()?.min ?: LocalTime.MIN
    val max = constraints.filterIsInstance<PropertyConstraint.MaxTime>().firstOrNull()?.max ?: LocalTime.MAX.withNano(0)
    if (!min.isBefore(max)) return min
    val range = ChronoUnit.SECONDS.between(min, max)
    return min.plusSeconds(rng.nextLong(range + 1))
  }

  private fun generateDateTime(constraints: Set<PropertyConstraint>, rng: Random): LocalDateTime {
    val min = constraints.filterIsInstance<PropertyConstraint.MinDatetime>().firstOrNull()?.min ?: LocalDateTime.now().minusYears(1)
    val max = constraints.filterIsInstance<PropertyConstraint.MaxDatetime>().firstOrNull()?.max ?: LocalDateTime.now().plusYears(1)
    if (!min.isBefore(max)) return min
    val range = ChronoUnit.SECONDS.between(min, max)
    return min.plusSeconds(rng.nextLong(range + 1))
  }

  private fun generateRefValue(property: Property, context: GenerationContext): Either<DomainError, String?> {
    val targetEntityId = property.targetEntityId ?: return if (property.nullable) null.right() else TestDataGeneratorError.GENERATION_FAILED.left()
    val pool = ensureTargetsExist(targetEntityId, context).getOrElse { return it.left() }
    if (pool.isEmpty()) {
      return if (property.nullable) null.right() else TestDataGeneratorError.GENERATION_FAILED.left()
    }
    return pool[context.rng.nextInt(pool.size)].id.value.right()
  }

  /**
   * Returns existing objects of [targetEntityId] within the test installation, generating exactly one first if none exist yet
   * (topologically - [GenerationContext.visiting] guards against infinite recursion for cyclic `REF` graphs, returning an empty
   * pool for the cycle-closing edge; the caller falls back to `null`/[TestDataGeneratorError.GENERATION_FAILED] depending on nullability).
   */
  private fun ensureTargetsExist(targetEntityId: EntityDefinitionId, context: GenerationContext): Either<DomainError, List<AppData>> {
    val existing = appDataRepository.findAllByInstalledAppIdAndEntityType(context.installedApp.id, targetEntityId)
    if (existing.isNotEmpty()) return existing.right()
    if (targetEntityId in context.visiting) return emptyList<AppData>().right()
    val targetEntityDef = context.entitiesById[targetEntityId] ?: return emptyList<AppData>().right()

    context.visiting += targetEntityId
    val generated = generateEntityObjects(targetEntityDef, 1, context)
    context.visiting -= targetEntityId
    return generated
  }

  private fun generateListValue(property: Property, context: GenerationContext): Either<DomainError, String?> {
    val itemType = property.listItemType ?: return null.right()
    val minSizeConstraint = property.constraints.filterIsInstance<PropertyConstraint.MinSize>().firstOrNull()?.min ?: 0
    val maxSizeConstraint = property.constraints.filterIsInstance<PropertyConstraint.MaxSize>().firstOrNull()?.max
    val effectiveMin = if (!property.nullable) maxOf(minSizeConstraint, 1) else minSizeConstraint
    val effectiveMax = maxSizeConstraint?.let { maxOf(it, effectiveMin) } ?: (effectiveMin + DEFAULT_LIST_SIZE_SPAN)
    val size = if (effectiveMax <= effectiveMin) effectiveMin else effectiveMin + context.rng.nextInt(effectiveMax - effectiveMin + 1)
    if (size == 0) return null.right()

    val itemProperty = property.copy(type = itemType, constraints = property.itemConstraints, nullable = false, listItemType = null)
    val items = mutableListOf<String>()
    repeat(size) {
      val value = generateStoredValue(itemProperty, null, context).getOrElse { return it.left() }
      items += value.orEmpty()
    }
    return encodeListValue(items).right()
  }

  /** Builds the raw (pre-JSON-encode) nested value map for an `OBJECT` property, recursing into further `OBJECT` nesting - see [encodeObjectValue]. */
  private fun generateRawObjectMap(property: Property, context: GenerationContext): Either<DomainError, Map<String, Any?>> {
    val result = LinkedHashMap<String, Any?>()
    for (nested in property.nestedProperties) {
      val value: Any? = if (nested.type == PropertyType.OBJECT) {
        generateRawObjectMap(nested, context).getOrElse { return it.left() }
      } else {
        generateStoredValue(nested, null, context).getOrElse { return it.left() }
      }
      result[nested.id.value] = value
    }
    return result.right()
  }

  private data class GenerationContext(
    val rng: Random,
    val installedApp: InstalledApp,
    val entitiesById: Map<EntityDefinitionId, EntityDefinition>,
    val visiting: MutableSet<EntityDefinitionId>,
  )

  companion object : KLogging() {
    private const val MAX_GENERATE_COUNT = 500
    private const val PERCENT_RANGE = 100
    private const val NULL_PROBABILITY_PERCENT = 15
    private const val MAX_UNIQUE_ATTEMPTS = 30
    private const val MAX_PATTERN_ATTEMPTS = 200
    private const val DEFAULT_LONG_RANGE = 1000L
    private const val DEFAULT_DOUBLE_RANGE = 1000.0
    private const val DEFAULT_STRING_LENGTH_SPAN = 15
    private const val DEFAULT_LIST_SIZE_SPAN = 5
    private val ALPHANUMERIC = (('a'..'z') + ('A'..'Z') + ('0'..'9')).toList()
  }
}
