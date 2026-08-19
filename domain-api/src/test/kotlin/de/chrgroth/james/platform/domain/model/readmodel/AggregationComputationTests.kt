package de.chrgroth.james.platform.domain.model.readmodel

import de.chrgroth.james.platform.domain.model.app.AggregationDefinition
import de.chrgroth.james.platform.domain.model.app.AggregationDefinitionId
import de.chrgroth.james.platform.domain.model.app.AggregationFunction
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.TimeBucket
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class AggregationComputationTests {

  private val installedAppId = InstalledAppId("installed-app-1")
  private val amountPropId = PropertyId("amount")

  private fun appData(id: String, data: Map<String, String?>, createdAt: Instant = Instant.parse("2026-08-19T10:00:00Z")) = AppData(
    id = AppDataId(id),
    userId = "user-1",
    installedAppId = installedAppId,
    appVersion = VersionNumber("1.0.0"),
    lastValidatedWithVersion = VersionNumber("1.0.0"),
    entityType = EntityDefinitionId("entity-1"),
    objectVersion = 1,
    createdAt = createdAt,
    lastChangedAt = createdAt,
    data = data,
  )

  // region contributionOf

  @Test
  fun `contributionOf parses the numeric source value for SUM`() {
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Total", function = AggregationFunction.SUM, sourceProperty = amountPropId)

    assertThat(aggregation.contributionOf(appData("d1", mapOf(amountPropId.value to "12.5")))).isEqualTo(12.5)
  }

  @Test
  fun `contributionOf is null for a missing or non-numeric source value`() {
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Total", function = AggregationFunction.SUM, sourceProperty = amountPropId)

    assertThat(aggregation.contributionOf(appData("d1", mapOf(amountPropId.value to null)))).isNull()
    assertThat(aggregation.contributionOf(appData("d2", mapOf(amountPropId.value to "not-a-number")))).isNull()
    assertThat(aggregation.contributionOf(appData("d3", emptyMap()))).isNull()
  }

  @Test
  fun `contributionOf for COUNT is 1 for any non-blank value and null for blank or missing`() {
    val nameProp = PropertyId("name")
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Count", function = AggregationFunction.COUNT, sourceProperty = nameProp)

    assertThat(aggregation.contributionOf(appData("d1", mapOf(nameProp.value to "Alice")))).isEqualTo(1.0)
    assertThat(aggregation.contributionOf(appData("d2", mapOf(nameProp.value to "")))).isNull()
    assertThat(aggregation.contributionOf(appData("d3", mapOf(nameProp.value to null)))).isNull()
  }

  // endregion

  // region groupKeyOf

  @Test
  fun `groupKeyOf uses the refPath property's value when set`() {
    val refPropId = PropertyId("shoe")
    val aggregation = AggregationDefinition(
      id = AggregationDefinitionId("agg-1"),
      name = "Km per shoe",
      function = AggregationFunction.SUM,
      sourceProperty = amountPropId,
      refPath = refPropId,
    )

    assertThat(aggregation.groupKeyOf(appData("d1", mapOf(refPropId.value to "shoe-42")))).isEqualTo("shoe-42")
  }

  @Test
  fun `groupKeyOf falls back to the groupBy property's value when refPath is unset`() {
    val groupByPropId = PropertyId("category")
    val aggregation = AggregationDefinition(
      id = AggregationDefinitionId("agg-1"),
      name = "Total per category",
      function = AggregationFunction.SUM,
      sourceProperty = amountPropId,
      groupBy = groupByPropId,
    )

    assertThat(aggregation.groupKeyOf(appData("d1", mapOf(groupByPropId.value to "Books")))).isEqualTo("Books")
  }

  @Test
  fun `groupKeyOf is null without refPath or groupBy`() {
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Total", function = AggregationFunction.SUM, sourceProperty = amountPropId)

    assertThat(aggregation.groupKeyOf(appData("d1", mapOf(amountPropId.value to "5")))).isNull()
  }

  // endregion

  // region bucketKeyOf

  @Test
  fun `bucketKeyOf encodes createdAt per TAG, WOCHE, MONAT and JAHR`() {
    val createdAt = Instant.parse("2026-08-19T10:00:00Z")
    fun aggregationWith(bucket: TimeBucket) =
      AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Total", function = AggregationFunction.SUM, sourceProperty = amountPropId, timeBucket = bucket)

    assertThat(aggregationWith(TimeBucket.TAG).bucketKeyOf(appData("d1", emptyMap(), createdAt))).isEqualTo("2026-08-19")
    assertThat(aggregationWith(TimeBucket.MONAT).bucketKeyOf(appData("d1", emptyMap(), createdAt))).isEqualTo("2026-08")
    assertThat(aggregationWith(TimeBucket.JAHR).bucketKeyOf(appData("d1", emptyMap(), createdAt))).isEqualTo("2026")
    assertThat(aggregationWith(TimeBucket.WOCHE).bucketKeyOf(appData("d1", emptyMap(), createdAt))).isEqualTo("2026-W34")
  }

  @Test
  fun `bucketKeyOf is null without timeBucket`() {
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Total", function = AggregationFunction.SUM, sourceProperty = amountPropId)

    assertThat(aggregation.bucketKeyOf(appData("d1", emptyMap()))).isNull()
  }

  @Test
  fun `bucketKeyOf uses timeProperty instead of createdAt when set, for both DATE and DATETIME values`() {
    val eventDatePropId = PropertyId("eventDate")
    val aggregation = AggregationDefinition(
      id = AggregationDefinitionId("agg-1"),
      name = "Total",
      function = AggregationFunction.SUM,
      sourceProperty = amountPropId,
      timeBucket = TimeBucket.MONAT,
      timeProperty = eventDatePropId,
    )
    val createdAt = Instant.parse("2026-01-01T00:00:00Z")

    assertThat(aggregation.bucketKeyOf(appData("d1", mapOf(eventDatePropId.value to "2026-08-19"), createdAt))).isEqualTo("2026-08")
    assertThat(aggregation.bucketKeyOf(appData("d2", mapOf(eventDatePropId.value to "2026-08-19T10:15:00"), createdAt))).isEqualTo("2026-08")
  }

  @Test
  fun `bucketKeyOf is null when timeProperty is set but the item has no or an unparsable value for it`() {
    val eventDatePropId = PropertyId("eventDate")
    val aggregation = AggregationDefinition(
      id = AggregationDefinitionId("agg-1"),
      name = "Total",
      function = AggregationFunction.SUM,
      sourceProperty = amountPropId,
      timeBucket = TimeBucket.MONAT,
      timeProperty = eventDatePropId,
    )

    assertThat(aggregation.bucketKeyOf(appData("d1", emptyMap()))).isNull()
    assertThat(aggregation.bucketKeyOf(appData("d2", mapOf(eventDatePropId.value to "not-a-date")))).isNull()
  }

  // endregion

  // region computeAggregationValues

  @Test
  fun `computeAggregationValues sums per group and reports sample count`() {
    val refPropId = PropertyId("shoe")
    val aggregation = AggregationDefinition(
      id = AggregationDefinitionId("agg-1"),
      name = "Km per shoe",
      function = AggregationFunction.SUM,
      sourceProperty = amountPropId,
      refPath = refPropId,
    )
    val items = listOf(
      appData("d1", mapOf(amountPropId.value to "10", refPropId.value to "shoe-1")),
      appData("d2", mapOf(amountPropId.value to "5", refPropId.value to "shoe-1")),
      appData("d3", mapOf(amountPropId.value to "7", refPropId.value to "shoe-2")),
      appData("d4", mapOf(refPropId.value to "shoe-2")), // no amount -> does not contribute
    )
    val now = Instant.parse("2026-08-19T12:00:00Z")

    val values = aggregation.computeAggregationValues(installedAppId, items, now)

    assertThat(values).hasSize(2)
    val shoe1 = values.single { it.id.groupKey == "shoe-1" }
    assertThat(shoe1.value).isEqualTo(15.0)
    assertThat(shoe1.sampleCount).isEqualTo(2)
    assertThat(shoe1.status).isEqualTo(AggregationValueStatus.UP_TO_DATE)
    val shoe2 = values.single { it.id.groupKey == "shoe-2" }
    assertThat(shoe2.value).isEqualTo(7.0)
    assertThat(shoe2.sampleCount).isEqualTo(1)
  }

  @Test
  fun `computeAggregationValues computes MIN, MAX and AVG correctly`() {
    fun aggregationWith(function: AggregationFunction) =
      AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Agg", function = function, sourceProperty = amountPropId)
    val items = listOf(
      appData("d1", mapOf(amountPropId.value to "10")),
      appData("d2", mapOf(amountPropId.value to "30")),
      appData("d3", mapOf(amountPropId.value to "20")),
    )
    val now = Instant.now()

    assertThat(aggregationWith(AggregationFunction.MIN).computeAggregationValues(installedAppId, items, now).single().value).isEqualTo(10.0)
    assertThat(aggregationWith(AggregationFunction.MAX).computeAggregationValues(installedAppId, items, now).single().value).isEqualTo(30.0)
    assertThat(aggregationWith(AggregationFunction.AVG).computeAggregationValues(installedAppId, items, now).single().value).isEqualTo(20.0)
  }

  @Test
  fun `computeAggregationValues returns no values when nothing contributes`() {
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Total", function = AggregationFunction.SUM, sourceProperty = amountPropId)

    val values = aggregation.computeAggregationValues(installedAppId, listOf(appData("d1", emptyMap())), Instant.now())

    assertThat(values).isEmpty()
  }

  // endregion
}
