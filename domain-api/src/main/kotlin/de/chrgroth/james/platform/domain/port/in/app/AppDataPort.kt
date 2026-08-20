package de.chrgroth.james.platform.domain.port.`in`.app

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.EntityDefinition

interface AppDataPort {
  fun createAppData(
    userId: String,
    installedAppId: String,
    entityTypeId: String,
    data: Map<String, List<String>>,
  ): Either<DomainError, AppData>

  fun listAppData(userId: String, installedAppId: String): Either<DomainError, List<AppData>>

  fun getAppData(userId: String, installedAppId: String, dataId: String): Either<DomainError, AppData>

  fun updateAppData(
    userId: String,
    installedAppId: String,
    dataId: String,
    data: Map<String, List<String>>,
  ): Either<DomainError, AppData>

  fun deleteAppData(userId: String, installedAppId: String, dataId: String): Either<DomainError, Int>

  fun getValueProposals(
    userId: String,
    installedAppId: String,
    entityTypeId: String,
    propertyId: String,
    currentData: Map<String, String>,
  ): Either<DomainError, List<String>>

  /**
   * Runs the same constraint validation [createAppData]/[updateAppData] apply, against already-stored-form [data] (no unit conversion or
   * form-value decoding), for reuse by callers that already hold data in that form, e.g. App Version Migration.
   * [excludingDataId], if given, excludes that object's own AppData row from sibling-uniqueness checks (as when re-validating that same object).
   */
  fun validateEntityData(entityDef: EntityDefinition, data: Map<String, String?>, installedAppId: String, excludingDataId: String? = null): Either<DomainError, Unit>
}
