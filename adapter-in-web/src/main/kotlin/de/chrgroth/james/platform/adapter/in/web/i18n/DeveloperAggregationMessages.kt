package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * Aggregation editor labels for `version-editor.html`, split out of [DeveloperMessages] into their own bundle for the same
 * reason as [UserImportFilterMessages] - Quarkus Qute's generated message bundle resolver fails bytecode verification once a
 * single interface grows past roughly 300 `@Message` methods, and [DeveloperMessages] is right at that wall.
 */
@MessageBundle("developerAggregation")
interface DeveloperAggregationMessages {

  @Message
  fun developerAggregationsHeading(): String

  @Message
  fun developerNoAggregationsMessage(): String

  @Message
  fun developerEditAggregationAriaLabel(name: String): String

  @Message
  fun developerColAggregationFunction(): String

  @Message
  fun developerColAggregationSource(): String

  @Message
  fun developerColAggregationDetails(): String

  @Message
  fun developerAddAggregationModalTitle(): String

  @Message
  fun developerEditAggregationModalTitle(): String

  @Message
  fun developerAggregationNamePlaceholder(): String

  @Message
  fun developerAggregationFunctionLabel(): String

  @Message
  fun developerAggregationSourcePropertyLabel(): String

  @Message
  fun developerAggregationSourcePropertyHelp(): String

  @Message
  fun developerAggregationRefPathLabel(): String

  @Message
  fun developerAggregationRefPathHelp(): String

  @Message
  fun developerAggregationTimeBucketLabel(): String

  @Message
  fun developerAggregationTimePropertyLabel(): String

  @Message
  fun developerAggregationTimePropertyHelp(): String

  @Message
  fun developerAggregationGroupByLabel(): String

  @Message
  fun developerAggregationNoneOption(): String

  @Message
  fun developerAggregationFunctionSum(): String

  @Message
  fun developerAggregationFunctionCount(): String

  @Message
  fun developerAggregationFunctionAvg(): String

  @Message
  fun developerAggregationFunctionMin(): String

  @Message
  fun developerAggregationFunctionMax(): String

  @Message
  fun developerAggregationTimeBucketTag(): String

  @Message
  fun developerAggregationTimeBucketWoche(): String

  @Message
  fun developerAggregationTimeBucketMonat(): String

  @Message
  fun developerAggregationTimeBucketJahr(): String

  @Message
  fun developerAggregationDetailPerRef(name: String): String

  @Message
  fun developerAggregationDetailPerTimeBucket(bucket: String): String

  @Message
  fun developerAggregationDetailGroupBy(name: String): String

  @Message
  fun developerRemoveAggregationModalTitle(): String

  @Message
  fun developerRemoveAggregationConfirm(): String

  @Message
  fun developerAggregationAddedMessage(): String

  @Message
  fun developerAggregationUpdatedMessage(): String

  @Message
  fun developerAggregationDeletedMessage(): String

  @Message
  fun developerAggregationNameRequiredError(): String

  @Message
  fun developerAggregationNotFoundError(): String

  @Message
  fun developerAggregationNameExistsError(): String

  @Message
  fun developerAggregationFunctionInvalidError(): String

  @Message
  fun developerAggregationSourcePropertyInvalidError(): String

  @Message
  fun developerAggregationRefPathInvalidError(): String

  @Message
  fun developerAggregationTimeBucketInvalidError(): String

  @Message
  fun developerAggregationTimePropertyInvalidError(): String

  @Message
  fun developerAggregationGroupByInvalidError(): String

  @Message
  fun developerInvalidAggregationDefinitionError(names: String): String
}
