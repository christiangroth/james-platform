package de.chrgroth.james.platform.domain.model.app

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AggregationDependencyIndexTests {

  private val laufschuh = EntityDefinitionId("laufschuh")
  private val lauf = EntityDefinitionId("lauf")

  private val kilometerProperty = Property(id = PropertyId("kilometer"), name = "Kilometer", type = PropertyType.DOUBLE, nullable = false)
  private val laufschuhRefProperty = Property(id = PropertyId("laufschuh-id"), name = "Laufschuh", type = PropertyType.REF, targetEntityId = laufschuh)

  @Test
  fun `buildAggregationDependencyIndex maps a self aggregation only to its owning entity`() {
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Total", function = AggregationFunction.SUM, sourceProperty = kilometerProperty.id)
    val laufEntity = EntityDefinition(id = lauf, name = "Lauf", properties = listOf(kilometerProperty), aggregations = listOf(aggregation))

    val index = buildAggregationDependencyIndex(listOf(laufEntity))

    assertThat(index.byOwningEntity[lauf]).containsExactly(AggregationDependency(lauf, aggregation.id))
    assertThat(index.byReferencedEntity).isEmpty()
    assertThat(index.affectedBy(lauf)).containsExactly(AggregationDependency(lauf, aggregation.id))
    assertThat(index.affectedBy(laufschuh)).isEmpty()
  }

  @Test
  fun `buildAggregationDependencyIndex maps a cross-entity aggregation to both owning and referenced entity`() {
    val aggregation = AggregationDefinition(
      id = AggregationDefinitionId("agg-1"),
      name = "Sum per Laufschuh",
      function = AggregationFunction.SUM,
      sourceProperty = kilometerProperty.id,
      refPath = laufschuhRefProperty.id,
    )
    val laufEntity = EntityDefinition(id = lauf, name = "Lauf", properties = listOf(kilometerProperty, laufschuhRefProperty), aggregations = listOf(aggregation))
    val laufschuhEntity = EntityDefinition(id = laufschuh, name = "Laufschuh")

    val index = buildAggregationDependencyIndex(listOf(laufEntity, laufschuhEntity))

    val dependency = AggregationDependency(lauf, aggregation.id)
    assertThat(index.byOwningEntity[lauf]).containsExactly(dependency)
    assertThat(index.byReferencedEntity[laufschuh]).containsExactly(dependency)
    assertThat(index.affectedBy(lauf)).containsExactly(dependency)
    assertThat(index.affectedBy(laufschuh)).containsExactly(dependency)
  }

  @Test
  fun `buildAggregationDependencyIndex returns empty maps for entities without aggregations`() {
    val laufEntity = EntityDefinition(id = lauf, name = "Lauf", properties = listOf(kilometerProperty))

    val index = buildAggregationDependencyIndex(listOf(laufEntity))

    assertThat(index.byOwningEntity).isEmpty()
    assertThat(index.byReferencedEntity).isEmpty()
    assertThat(index.affectedBy(lauf)).isEmpty()
  }
}
