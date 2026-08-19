package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.model.app.AggregationDefinition
import de.chrgroth.james.platform.domain.model.app.AggregationDefinitionId
import de.chrgroth.james.platform.domain.model.app.AggregationFunction
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.TimeBucket
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValue
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValueId
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValueStatus
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent
import de.chrgroth.james.platform.domain.port.out.infra.OutboxPort
import de.chrgroth.james.platform.domain.port.out.readmodel.AggregationRepositoryPort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class AggregationInlineUpdateServiceTests {

  private val aggregationRepository: AggregationRepositoryPort = mockk()
  private val outbox: OutboxPort = mockk()
  private val service = AggregationInlineUpdateService(aggregationRepository, outbox)

  private val installedAppId = InstalledAppId("installed-app-1")
  private val amountPropId = PropertyId("amount")
  private val amountProp = Property(id = amountPropId, name = "Amount", type = PropertyType.LONG)

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

  // region SUM

  @Test
  fun `create seeds SUM from zero`() {
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Total", function = AggregationFunction.SUM, sourceProperty = amountPropId)
    val entityDef = EntityDefinition(id = EntityDefinitionId("entity-1"), name = "Entity", properties = listOf(amountProp), aggregations = listOf(aggregation))
    val id = AggregationValueId(installedAppId, aggregation.id)
    every { aggregationRepository.findByAggregationValueId(id) } returns null
    val savedSlot = slot<AggregationValue>()
    justRun { aggregationRepository.save(capture(savedSlot)) }

    service.onAppDataCreated(installedAppId, listOf(entityDef), entityDef, appData("d1", mapOf(amountPropId.value to "10")))

    assertThat(savedSlot.captured.value).isEqualTo(10.0)
    assertThat(savedSlot.captured.status).isEqualTo(AggregationValueStatus.UP_TO_DATE)
  }

  @Test
  fun `create adds to an existing SUM`() {
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Total", function = AggregationFunction.SUM, sourceProperty = amountPropId)
    val entityDef = EntityDefinition(id = EntityDefinitionId("entity-1"), name = "Entity", properties = listOf(amountProp), aggregations = listOf(aggregation))
    val id = AggregationValueId(installedAppId, aggregation.id)
    every { aggregationRepository.findByAggregationValueId(id) } returns
      AggregationValue(id, value = 10.0, status = AggregationValueStatus.UP_TO_DATE, updatedAt = Instant.now())
    val savedSlot = slot<AggregationValue>()
    justRun { aggregationRepository.save(capture(savedSlot)) }

    service.onAppDataCreated(installedAppId, listOf(entityDef), entityDef, appData("d2", mapOf(amountPropId.value to "5")))

    assertThat(savedSlot.captured.value).isEqualTo(15.0)
  }

  @Test
  fun `delete subtracts from SUM`() {
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Total", function = AggregationFunction.SUM, sourceProperty = amountPropId)
    val entityDef = EntityDefinition(id = EntityDefinitionId("entity-1"), name = "Entity", properties = listOf(amountProp), aggregations = listOf(aggregation))
    val id = AggregationValueId(installedAppId, aggregation.id)
    every { aggregationRepository.findByAggregationValueId(id) } returns
      AggregationValue(id, value = 15.0, status = AggregationValueStatus.UP_TO_DATE, updatedAt = Instant.now())
    val savedSlot = slot<AggregationValue>()
    justRun { aggregationRepository.save(capture(savedSlot)) }

    service.onAppDataDeleted(installedAppId, listOf(entityDef), entityDef, appData("d1", mapOf(amountPropId.value to "10")))

    assertThat(savedSlot.captured.value).isEqualTo(5.0)
  }

  @Test
  fun `update with unchanged group adjusts SUM by the delta`() {
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Total", function = AggregationFunction.SUM, sourceProperty = amountPropId)
    val entityDef = EntityDefinition(id = EntityDefinitionId("entity-1"), name = "Entity", properties = listOf(amountProp), aggregations = listOf(aggregation))
    val id = AggregationValueId(installedAppId, aggregation.id)
    every { aggregationRepository.findByAggregationValueId(id) } returnsMany listOf(
      AggregationValue(id, value = 10.0, status = AggregationValueStatus.UP_TO_DATE, updatedAt = Instant.now()),
      AggregationValue(id, value = 0.0, status = AggregationValueStatus.UP_TO_DATE, updatedAt = Instant.now()),
    )
    val savedSlots = mutableListOf<AggregationValue>()
    justRun { aggregationRepository.save(capture(savedSlots)) }

    val old = appData("d1", mapOf(amountPropId.value to "10"))
    val new = appData("d1", mapOf(amountPropId.value to "25"))
    service.onAppDataUpdated(installedAppId, listOf(entityDef), entityDef, old, new)

    assertThat(savedSlots.last().value).isEqualTo(25.0)
  }

  @Test
  fun `create does not contribute when source property is missing`() {
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Total", function = AggregationFunction.SUM, sourceProperty = amountPropId)
    val entityDef = EntityDefinition(id = EntityDefinitionId("entity-1"), name = "Entity", properties = listOf(amountProp), aggregations = listOf(aggregation))

    service.onAppDataCreated(installedAppId, listOf(entityDef), entityDef, appData("d1", mapOf(amountPropId.value to null)))

    verify(exactly = 0) { aggregationRepository.findByAggregationValueId(any()) }
    verify(exactly = 0) { aggregationRepository.save(any()) }
  }

  // endregion

  // region COUNT

  @Test
  fun `create increments COUNT for a non-blank source value regardless of type`() {
    val nameProp = Property(id = PropertyId("name"), name = "Name", type = PropertyType.STRING)
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Count", function = AggregationFunction.COUNT, sourceProperty = nameProp.id)
    val entityDef = EntityDefinition(id = EntityDefinitionId("entity-1"), name = "Entity", properties = listOf(nameProp), aggregations = listOf(aggregation))
    val id = AggregationValueId(installedAppId, aggregation.id)
    every { aggregationRepository.findByAggregationValueId(id) } returns
      AggregationValue(id, value = 3.0, status = AggregationValueStatus.UP_TO_DATE, updatedAt = Instant.now())
    val savedSlot = slot<AggregationValue>()
    justRun { aggregationRepository.save(capture(savedSlot)) }

    service.onAppDataCreated(installedAppId, listOf(entityDef), entityDef, appData("d1", mapOf(nameProp.id.value to "Alice")))

    assertThat(savedSlot.captured.value).isEqualTo(4.0)
  }

  // endregion

  // region AVG

  @Test
  fun `create updates the running mean and sample count for AVG`() {
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Average", function = AggregationFunction.AVG, sourceProperty = amountPropId)
    val entityDef = EntityDefinition(id = EntityDefinitionId("entity-1"), name = "Entity", properties = listOf(amountProp), aggregations = listOf(aggregation))
    val id = AggregationValueId(installedAppId, aggregation.id)
    every { aggregationRepository.findByAggregationValueId(id) } returns
      AggregationValue(id, value = 10.0, status = AggregationValueStatus.UP_TO_DATE, updatedAt = Instant.now(), sampleCount = 2)
    val savedSlot = slot<AggregationValue>()
    justRun { aggregationRepository.save(capture(savedSlot)) }

    // existing mean 10.0 over 2 samples (sum=20), add a 3rd sample of 40 -> new mean (20+40)/3 = 20.0
    service.onAppDataCreated(installedAppId, listOf(entityDef), entityDef, appData("d3", mapOf(amountPropId.value to "40")))

    assertThat(savedSlot.captured.value).isEqualTo(20.0)
    assertThat(savedSlot.captured.sampleCount).isEqualTo(3)
  }

  @Test
  fun `delete removes a sample from the running mean for AVG`() {
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Average", function = AggregationFunction.AVG, sourceProperty = amountPropId)
    val entityDef = EntityDefinition(id = EntityDefinitionId("entity-1"), name = "Entity", properties = listOf(amountProp), aggregations = listOf(aggregation))
    val id = AggregationValueId(installedAppId, aggregation.id)
    // mean 20.0 over 3 samples (sum=60), remove the sample of 40 -> new mean (60-40)/2 = 10.0
    every { aggregationRepository.findByAggregationValueId(id) } returns
      AggregationValue(id, value = 20.0, status = AggregationValueStatus.UP_TO_DATE, updatedAt = Instant.now(), sampleCount = 3)
    val savedSlot = slot<AggregationValue>()
    justRun { aggregationRepository.save(capture(savedSlot)) }

    service.onAppDataDeleted(installedAppId, listOf(entityDef), entityDef, appData("d3", mapOf(amountPropId.value to "40")))

    assertThat(savedSlot.captured.value).isEqualTo(10.0)
    assertThat(savedSlot.captured.sampleCount).isEqualTo(2)
  }

  // endregion

  // region MIN/MAX cheap path

  @Test
  fun `create updates MAX when the new value wins`() {
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Max", function = AggregationFunction.MAX, sourceProperty = amountPropId)
    val entityDef = EntityDefinition(id = EntityDefinitionId("entity-1"), name = "Entity", properties = listOf(amountProp), aggregations = listOf(aggregation))
    val id = AggregationValueId(installedAppId, aggregation.id)
    every { aggregationRepository.findByAggregationValueId(id) } returns
      AggregationValue(id, value = 10.0, status = AggregationValueStatus.UP_TO_DATE, updatedAt = Instant.now())
    val savedSlot = slot<AggregationValue>()
    justRun { aggregationRepository.save(capture(savedSlot)) }

    service.onAppDataCreated(installedAppId, listOf(entityDef), entityDef, appData("d2", mapOf(amountPropId.value to "20")))

    assertThat(savedSlot.captured.value).isEqualTo(20.0)
    verify(exactly = 0) { outbox.enqueue(any()) }
  }

  @Test
  fun `create is a no-op when the new value does not beat the current MAX`() {
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Max", function = AggregationFunction.MAX, sourceProperty = amountPropId)
    val entityDef = EntityDefinition(id = EntityDefinitionId("entity-1"), name = "Entity", properties = listOf(amountProp), aggregations = listOf(aggregation))
    val id = AggregationValueId(installedAppId, aggregation.id)
    every { aggregationRepository.findByAggregationValueId(id) } returns
      AggregationValue(id, value = 100.0, status = AggregationValueStatus.UP_TO_DATE, updatedAt = Instant.now())

    service.onAppDataCreated(installedAppId, listOf(entityDef), entityDef, appData("d2", mapOf(amountPropId.value to "20")))

    verify(exactly = 0) { aggregationRepository.save(any()) }
    verify(exactly = 0) { outbox.enqueue(any()) }
  }

  @Test
  fun `delete of a value that does not equal the stored MAX is a cheap no-op`() {
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Max", function = AggregationFunction.MAX, sourceProperty = amountPropId)
    val entityDef = EntityDefinition(id = EntityDefinitionId("entity-1"), name = "Entity", properties = listOf(amountProp), aggregations = listOf(aggregation))
    val id = AggregationValueId(installedAppId, aggregation.id)
    every { aggregationRepository.findByAggregationValueId(id) } returns
      AggregationValue(id, value = 100.0, status = AggregationValueStatus.UP_TO_DATE, updatedAt = Instant.now())

    service.onAppDataDeleted(installedAppId, listOf(entityDef), entityDef, appData("d1", mapOf(amountPropId.value to "20")))

    verify(exactly = 0) { aggregationRepository.save(any()) }
    verify(exactly = 0) { outbox.enqueue(any()) }
    verify(exactly = 0) { aggregationRepository.markStaleByInstalledAppIdAndAggregationDefinitionId(any(), any()) }
  }

  @Test
  fun `delete of the current MAX value marks stale and enqueues a recompute`() {
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Max", function = AggregationFunction.MAX, sourceProperty = amountPropId)
    val entityDef = EntityDefinition(id = EntityDefinitionId("entity-1"), name = "Entity", properties = listOf(amountProp), aggregations = listOf(aggregation))
    val id = AggregationValueId(installedAppId, aggregation.id)
    every { aggregationRepository.findByAggregationValueId(id) } returns
      AggregationValue(id, value = 100.0, status = AggregationValueStatus.UP_TO_DATE, updatedAt = Instant.now())
    justRun { aggregationRepository.markStaleByInstalledAppIdAndAggregationDefinitionId(installedAppId, aggregation.id) }
    justRun { outbox.enqueue(any()) }

    service.onAppDataDeleted(installedAppId, listOf(entityDef), entityDef, appData("d1", mapOf(amountPropId.value to "100")))

    verify(exactly = 0) { aggregationRepository.save(any()) }
    verify { aggregationRepository.markStaleByInstalledAppIdAndAggregationDefinitionId(installedAppId, aggregation.id) }
    verify { outbox.enqueue(DomainOutboxEvent.RecomputeAggregation(installedAppId.value, aggregation.id.value)) }
  }

  @Test
  fun `update lowering the value away from the current MAX marks stale and enqueues, without an immediate save`() {
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Max", function = AggregationFunction.MAX, sourceProperty = amountPropId)
    val entityDef = EntityDefinition(id = EntityDefinitionId("entity-1"), name = "Entity", properties = listOf(amountProp), aggregations = listOf(aggregation))
    val id = AggregationValueId(installedAppId, aggregation.id)
    every { aggregationRepository.findByAggregationValueId(id) } returns
      AggregationValue(id, value = 100.0, status = AggregationValueStatus.UP_TO_DATE, updatedAt = Instant.now())
    justRun { aggregationRepository.markStaleByInstalledAppIdAndAggregationDefinitionId(installedAppId, aggregation.id) }
    justRun { outbox.enqueue(any()) }

    val old = appData("d1", mapOf(amountPropId.value to "100"))
    val new = appData("d1", mapOf(amountPropId.value to "40"))
    service.onAppDataUpdated(installedAppId, listOf(entityDef), entityDef, old, new)

    verify(exactly = 0) { aggregationRepository.save(any()) }
    verify { aggregationRepository.markStaleByInstalledAppIdAndAggregationDefinitionId(installedAppId, aggregation.id) }
    verify(exactly = 1) { outbox.enqueue(any()) }
  }

  @Test
  fun `update raising an item that already held the MAX further stays a cheap O(1) overwrite`() {
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Max", function = AggregationFunction.MAX, sourceProperty = amountPropId)
    val entityDef = EntityDefinition(id = EntityDefinitionId("entity-1"), name = "Entity", properties = listOf(amountProp), aggregations = listOf(aggregation))
    val id = AggregationValueId(installedAppId, aggregation.id)
    every { aggregationRepository.findByAggregationValueId(id) } returns
      AggregationValue(id, value = 100.0, status = AggregationValueStatus.UP_TO_DATE, updatedAt = Instant.now())
    justRun { aggregationRepository.markStaleByInstalledAppIdAndAggregationDefinitionId(installedAppId, aggregation.id) }
    justRun { outbox.enqueue(any()) }
    val savedSlot = slot<AggregationValue>()
    justRun { aggregationRepository.save(capture(savedSlot)) }

    val old = appData("d1", mapOf(amountPropId.value to "100"))
    val new = appData("d1", mapOf(amountPropId.value to "150"))
    service.onAppDataUpdated(installedAppId, listOf(entityDef), entityDef, old, new)

    // 150 beats the previous stored max of 100 outright - guaranteed correct without a scan (invariant: no other
    // item could already exceed the old stored max of 100), so the STALE marking from the delete-half is overwritten.
    assertThat(savedSlot.captured.value).isEqualTo(150.0)
    assertThat(savedSlot.captured.status).isEqualTo(AggregationValueStatus.UP_TO_DATE)
  }

  // endregion

  // region cross-entity grouping (refPath) and time bucketing

  @Test
  fun `create groups the value by the refPath property's referenced instance id`() {
    val refPropId = PropertyId("shoe")
    val refProp = Property(id = refPropId, name = "Shoe", type = PropertyType.REF, targetEntityId = EntityDefinitionId("shoe-entity"))
    val aggregation = AggregationDefinition(
      id = AggregationDefinitionId("agg-1"),
      name = "Km per shoe",
      function = AggregationFunction.SUM,
      sourceProperty = amountPropId,
      refPath = refPropId,
    )
    val entityDef = EntityDefinition(id = EntityDefinitionId("entity-1"), name = "Run", properties = listOf(amountProp, refProp), aggregations = listOf(aggregation))
    val id = AggregationValueId(installedAppId, aggregation.id, groupKey = "shoe-42")
    every { aggregationRepository.findByAggregationValueId(id) } returns null
    val savedSlot = slot<AggregationValue>()
    justRun { aggregationRepository.save(capture(savedSlot)) }

    service.onAppDataCreated(installedAppId, listOf(entityDef), entityDef, appData("d1", mapOf(amountPropId.value to "5", refPropId.value to "shoe-42")))

    assertThat(savedSlot.captured.id.groupKey).isEqualTo("shoe-42")
    assertThat(savedSlot.captured.value).isEqualTo(5.0)
  }

  @Test
  fun `create groups the value into the MONAT time bucket derived from createdAt`() {
    val aggregation = AggregationDefinition(
      id = AggregationDefinitionId("agg-1"),
      name = "Total per month",
      function = AggregationFunction.SUM,
      sourceProperty = amountPropId,
      timeBucket = TimeBucket.MONAT,
    )
    val entityDef = EntityDefinition(id = EntityDefinitionId("entity-1"), name = "Entity", properties = listOf(amountProp), aggregations = listOf(aggregation))
    val id = AggregationValueId(installedAppId, aggregation.id, bucketKey = "2026-08")
    every { aggregationRepository.findByAggregationValueId(id) } returns null
    val savedSlot = slot<AggregationValue>()
    justRun { aggregationRepository.save(capture(savedSlot)) }

    service.onAppDataCreated(
      installedAppId,
      listOf(entityDef),
      entityDef,
      appData("d1", mapOf(amountPropId.value to "5"), createdAt = Instant.parse("2026-08-19T10:00:00Z")),
    )

    assertThat(savedSlot.captured.id.bucketKey).isEqualTo("2026-08")
  }

  @Test
  fun `create groups the value by timeProperty instead of createdAt when set`() {
    val eventDatePropId = PropertyId("eventDate")
    val eventDateProp = Property(id = eventDatePropId, name = "Event Date", type = PropertyType.DATE)
    val aggregation = AggregationDefinition(
      id = AggregationDefinitionId("agg-1"),
      name = "Total per month",
      function = AggregationFunction.SUM,
      sourceProperty = amountPropId,
      timeBucket = TimeBucket.MONAT,
      timeProperty = eventDatePropId,
    )
    val entityDef =
      EntityDefinition(id = EntityDefinitionId("entity-1"), name = "Entity", properties = listOf(amountProp, eventDateProp), aggregations = listOf(aggregation))
    val id = AggregationValueId(installedAppId, aggregation.id, bucketKey = "2025-01")
    every { aggregationRepository.findByAggregationValueId(id) } returns null
    val savedSlot = slot<AggregationValue>()
    justRun { aggregationRepository.save(capture(savedSlot)) }

    service.onAppDataCreated(
      installedAppId,
      listOf(entityDef),
      entityDef,
      appData("d1", mapOf(amountPropId.value to "5", eventDatePropId.value to "2025-01-10"), createdAt = Instant.parse("2026-08-19T10:00:00Z")),
    )

    assertThat(savedSlot.captured.id.bucketKey).isEqualTo("2025-01")
  }

  // endregion

  // region dependency index scoping

  @Test
  fun `only aggregations owned by the written entity are updated`() {
    val otherAggregation = AggregationDefinition(id = AggregationDefinitionId("agg-other"), name = "Other", function = AggregationFunction.SUM, sourceProperty = amountPropId)
    val otherEntityDef =
      EntityDefinition(id = EntityDefinitionId("entity-other"), name = "Other", properties = listOf(amountProp), aggregations = listOf(otherAggregation))
    val entityDef = EntityDefinition(id = EntityDefinitionId("entity-1"), name = "Entity", properties = listOf(amountProp))

    service.onAppDataCreated(installedAppId, listOf(entityDef, otherEntityDef), entityDef, appData("d1", mapOf(amountPropId.value to "5")))

    verify(exactly = 0) { aggregationRepository.findByAggregationValueId(any()) }
    verify(exactly = 0) { aggregationRepository.save(any()) }
  }

  // endregion
}
