package de.chrgroth.james.platform.adapter.out.mongodb

import de.chrgroth.james.platform.domain.model.app.AggregationDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValue
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValueId
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValueStatus
import de.chrgroth.james.platform.domain.port.out.readmodel.AggregationRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@QuarkusTest
class AggregationRepositoryTests {

  @Inject
  lateinit var aggregationRepository: AggregationRepositoryPort

  @Test
  fun `save and findAllByInstalledAppIdAndAggregationDefinitionId round-trip a grouped, bucketed value`() {
    val installedAppId = InstalledAppId(UUID.randomUUID().toString())
    val aggregationDefinitionId = AggregationDefinitionId(UUID.randomUUID().toString())
    val value = AggregationValue(
      id = AggregationValueId(
        installedAppId = installedAppId,
        aggregationDefinitionId = aggregationDefinitionId,
        groupKey = "appdata-1",
        bucketKey = "2026-08",
      ),
      value = 42.5,
      status = AggregationValueStatus.UP_TO_DATE,
      updatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
    )

    aggregationRepository.save(value)

    val loaded = aggregationRepository.findAllByInstalledAppIdAndAggregationDefinitionId(installedAppId, aggregationDefinitionId)
    assertThat(loaded).containsExactly(value)
  }

  @Test
  fun `markStaleByInstalledAppIdAndAggregationDefinitionId flips status of matching values`() {
    val installedAppId = InstalledAppId(UUID.randomUUID().toString())
    val aggregationDefinitionId = AggregationDefinitionId(UUID.randomUUID().toString())
    val value = AggregationValue(
      id = AggregationValueId(installedAppId = installedAppId, aggregationDefinitionId = aggregationDefinitionId),
      value = 1.0,
      status = AggregationValueStatus.UP_TO_DATE,
      updatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
    )
    aggregationRepository.save(value)

    aggregationRepository.markStaleByInstalledAppIdAndAggregationDefinitionId(installedAppId, aggregationDefinitionId)

    val loaded = aggregationRepository.findAllByInstalledAppIdAndAggregationDefinitionId(installedAppId, aggregationDefinitionId)
    assertThat(loaded).singleElement().satisfies({ assertThat(it.status).isEqualTo(AggregationValueStatus.STALE) })
  }

  @Test
  fun `deleteAllByInstalledAppId removes all values of that installation only`() {
    val installedAppId = InstalledAppId(UUID.randomUUID().toString())
    val otherInstalledAppId = InstalledAppId(UUID.randomUUID().toString())
    val aggregationDefinitionId = AggregationDefinitionId(UUID.randomUUID().toString())
    aggregationRepository.save(
      AggregationValue(
        id = AggregationValueId(installedAppId = installedAppId, aggregationDefinitionId = aggregationDefinitionId),
        value = 1.0,
        status = AggregationValueStatus.UP_TO_DATE,
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
      ),
    )
    val otherValue = AggregationValue(
      id = AggregationValueId(installedAppId = otherInstalledAppId, aggregationDefinitionId = aggregationDefinitionId),
      value = 2.0,
      status = AggregationValueStatus.UP_TO_DATE,
      updatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
    )
    aggregationRepository.save(otherValue)

    aggregationRepository.deleteAllByInstalledAppId(installedAppId)

    assertThat(aggregationRepository.findAllByInstalledAppIdAndAggregationDefinitionId(installedAppId, aggregationDefinitionId)).isEmpty()
    assertThat(aggregationRepository.findAllByInstalledAppIdAndAggregationDefinitionId(otherInstalledAppId, aggregationDefinitionId)).containsExactly(otherValue)
  }

  @Test
  fun `deleteAllByInstalledAppIdAndAggregationDefinitionId removes only values of that aggregation definition`() {
    val installedAppId = InstalledAppId(UUID.randomUUID().toString())
    val aggregationDefinitionId = AggregationDefinitionId(UUID.randomUUID().toString())
    val otherAggregationDefinitionId = AggregationDefinitionId(UUID.randomUUID().toString())
    aggregationRepository.save(
      AggregationValue(
        id = AggregationValueId(installedAppId = installedAppId, aggregationDefinitionId = aggregationDefinitionId),
        value = 1.0,
        status = AggregationValueStatus.UP_TO_DATE,
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
      ),
    )
    val otherValue = AggregationValue(
      id = AggregationValueId(installedAppId = installedAppId, aggregationDefinitionId = otherAggregationDefinitionId),
      value = 2.0,
      status = AggregationValueStatus.UP_TO_DATE,
      updatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
    )
    aggregationRepository.save(otherValue)

    aggregationRepository.deleteAllByInstalledAppIdAndAggregationDefinitionId(installedAppId, aggregationDefinitionId)

    assertThat(aggregationRepository.findAllByInstalledAppIdAndAggregationDefinitionId(installedAppId, aggregationDefinitionId)).isEmpty()
    assertThat(aggregationRepository.findAllByInstalledAppIdAndAggregationDefinitionId(installedAppId, otherAggregationDefinitionId)).containsExactly(otherValue)
  }
}
