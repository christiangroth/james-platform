package de.chrgroth.james.platform.domain.app

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.AppVersionError
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.VersionBumpResult
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.port.`in`.app.AppVersionManagementPort
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.time.Instant
import java.util.UUID

@ApplicationScoped
@Suppress("Unused")
class AppVersionManagementService(
  private val appRepository: AppRepositoryPort,
  private val appVersionRepository: AppVersionRepositoryPort,
) : AppVersionManagementPort {

  override fun listVersions(appId: String): Either<DomainError, List<AppVersion>> {
    appRepository.findById(AppId(appId)) ?: run {
      logger.warn { "List versions failed: app not found: $appId" }
      return AppVersionError.APP_NOT_FOUND.left()
    }
    return appVersionRepository.findAllByAppId(AppId(appId)).right()
  }

  override fun createVersion(appId: String, versionNumber: String): Either<DomainError, AppVersion> {
    if (versionNumber.isBlank()) {
      logger.warn { "Create version failed: blank version number" }
      return AppVersionError.BLANK_INPUT.left()
    }
    if (!SEMANTIC_VERSION_REGEX.matches(versionNumber)) {
      logger.warn { "Create version failed: invalid version number format: $versionNumber" }
      return AppVersionError.INVALID_VERSION_NUMBER_FORMAT.left()
    }
    appRepository.findById(AppId(appId)) ?: run {
      logger.warn { "Create version failed: app not found: $appId" }
      return AppVersionError.APP_NOT_FOUND.left()
    }
    val existingVersions = appVersionRepository.findAllByAppId(AppId(appId))
    if (existingVersions.any { it.status == AppVersionStatus.DRAFT }) {
      logger.warn { "Create version failed: draft version already exists for app $appId" }
      return AppVersionError.DRAFT_VERSION_ALREADY_EXISTS.left()
    }
    if (existingVersions.any { it.versionNumber == VersionNumber(versionNumber) }) {
      logger.warn { "Create version failed: version number already exists: $versionNumber in app $appId" }
      return AppVersionError.VERSION_NUMBER_ALREADY_EXISTS.left()
    }
    val latestPublished = existingVersions
      .filter { it.status == AppVersionStatus.PUBLISHED }
      .maxByOrNull { it.createdAt }
    val newVersion = if (latestPublished != null) {
      latestPublished.copy(
        id = AppVersionId(UUID.randomUUID().toString()),
        versionNumber = VersionNumber(versionNumber),
        releaseNotes = null,
        status = AppVersionStatus.DRAFT,
        createdAt = Instant.now(),
      )
    } else {
      AppVersion(
        id = AppVersionId(UUID.randomUUID().toString()),
        appId = AppId(appId),
        versionNumber = VersionNumber(versionNumber),
        releaseNotes = null,
        entityDefinitions = emptyList(),
        reports = emptyList(),
        status = AppVersionStatus.DRAFT,
        createdAt = Instant.now(),
      )
    }
    appVersionRepository.save(newVersion)
    logger.info { "App version created: $versionNumber for app $appId (${newVersion.id.value})" }
    return newVersion.right()
  }

  override fun getVersion(appId: String, versionId: String): Either<DomainError, AppVersion> {
    val version = appVersionRepository.findById(AppVersionId(versionId)) ?: run {
      logger.warn { "Get version failed: not found: $versionId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    if (version.appId != AppId(appId)) {
      logger.warn { "Get version failed: version $versionId does not belong to app $appId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    return version.right()
  }

  override fun publishVersion(appId: String, versionId: String): Either<DomainError, AppVersion> {
    val version = appVersionRepository.findById(AppVersionId(versionId)) ?: run {
      logger.warn { "Publish version failed: not found: $versionId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    if (version.appId != AppId(appId)) {
      logger.warn { "Publish version failed: version $versionId does not belong to app $appId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    if (version.status != AppVersionStatus.DRAFT) {
      logger.warn { "Publish version failed: version $versionId is not in DRAFT status" }
      return AppVersionError.VERSION_NOT_IN_DRAFT.left()
    }
    val publishedVersion = version.copy(status = AppVersionStatus.PUBLISHED)
    appVersionRepository.save(publishedVersion)
    logger.info { "App version published: ${version.versionNumber.value} (${version.id.value})" }
    return publishedVersion.right()
  }

  override fun computeVersionBump(appId: String, draftVersionId: String): Either<DomainError, VersionBumpResult> {
    appRepository.findById(AppId(appId)) ?: run {
      logger.warn { "Compute version bump failed: app not found: $appId" }
      return AppVersionError.APP_NOT_FOUND.left()
    }
    val draft = appVersionRepository.findById(AppVersionId(draftVersionId)) ?: run {
      logger.warn { "Compute version bump failed: version not found: $draftVersionId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    if (draft.appId != AppId(appId)) {
      logger.warn { "Compute version bump failed: version $draftVersionId does not belong to app $appId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    if (draft.status != AppVersionStatus.DRAFT) {
      logger.warn { "Compute version bump failed: version $draftVersionId is not in DRAFT status" }
      return AppVersionError.VERSION_NOT_IN_DRAFT.left()
    }
    val latestPublished = appVersionRepository.findAllByAppId(AppId(appId))
      .filter { it.status == AppVersionStatus.PUBLISHED }
      .maxByOrNull { it.createdAt }
    if (latestPublished == null) {
      val firstVersion = VersionNumber(FIRST_VERSION)
      logger.info { "Compute version bump: first release for app $appId → $FIRST_VERSION" }
      return VersionBumpResult(
        hasBreakingChanges = false,
        suggestedVersionOnBreaking = firstVersion,
        suggestedVersionOnFeature = firstVersion,
        suggestedVersionOnBugfix = firstVersion,
      ).right()
    }
    val hasBreaking = hasBreakingChanges(latestPublished, draft)
    val (onBreaking, onFeature, onBugfix) = nextVersions(latestPublished.versionNumber)
    logger.info { "Compute version bump for app $appId: breaking=$hasBreaking, breaking→${onBreaking.value}, feature→${onFeature.value}, bugfix→${onBugfix.value}" }
    return VersionBumpResult(
      hasBreakingChanges = hasBreaking,
      suggestedVersionOnBreaking = onBreaking,
      suggestedVersionOnFeature = onFeature,
      suggestedVersionOnBugfix = onBugfix,
    ).right()
  }

  private fun hasBreakingChanges(published: AppVersion, draft: AppVersion): Boolean {
    val publishedEntityIds = published.entityDefinitions.map { it.id }.toSet()
    val draftEntityIds = draft.entityDefinitions.map { it.id }.toSet()
    if (!draftEntityIds.containsAll(publishedEntityIds)) return true
    for (publishedEntity in published.entityDefinitions) {
      val draftEntity = draft.entityDefinitions.find { it.id == publishedEntity.id } ?: return true
      val publishedPropIds = publishedEntity.properties.map { it.id }.toSet()
      val draftPropIds = draftEntity.properties.map { it.id }.toSet()
      if (!draftPropIds.containsAll(publishedPropIds)) return true
      for (publishedProp in publishedEntity.properties) {
        val draftProp = draftEntity.properties.find { it.id == publishedProp.id } ?: return true
        if (draftProp.type != publishedProp.type) return true
        if (publishedProp.nullable && !draftProp.nullable) return true
        val addedConstraints = draftProp.constraints - publishedProp.constraints
        if (addedConstraints.any { it is PropertyConstraint.NotNull }) return true
        if (addedConstraints.any { isRestrictiveConstraint(it) }) return true
      }
    }
    return false
  }

  private fun isRestrictiveConstraint(constraint: PropertyConstraint): Boolean = when (constraint) {
    is PropertyConstraint.MinLong,
    is PropertyConstraint.MaxLong,
    is PropertyConstraint.MinDouble,
    is PropertyConstraint.MaxDouble,
    is PropertyConstraint.MinLength,
    is PropertyConstraint.MaxLength,
    is PropertyConstraint.Pattern,
    is PropertyConstraint.MinSize,
    is PropertyConstraint.MaxSize,
    -> true
    else -> false
  }

  private fun nextVersions(current: VersionNumber): Triple<VersionNumber, VersionNumber, VersionNumber> {
    // current is always a stored published version whose format was enforced by SEMANTIC_VERSION_REGEX on creation
    val parts = current.value.split(".").map { it.toInt() }
    val major = parts[0]
    val minor = parts[1]
    val patch = parts[2]
    return Triple(
      VersionNumber("${major + 1}.0.0"),
      VersionNumber("$major.${minor + 1}.0"),
      VersionNumber("$major.$minor.${patch + 1}"),
    )
  }

  companion object : KLogging() {
    private val SEMANTIC_VERSION_REGEX = Regex("""^\d+\.\d+\.\d+$""")
    internal const val FIRST_VERSION = "0.1.0"
  }
}
