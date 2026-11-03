package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.model.app.ComputedProperty
import de.chrgroth.james.platform.domain.model.app.ComputedPropertyId
import de.chrgroth.james.platform.domain.model.app.DistanceGranularity
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.UnitFamily
import de.chrgroth.james.platform.domain.model.app.encodeObjectValue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class ComputedPropertyServiceTests {

  private val service = ComputedPropertyService()

  // 23:30 UTC is already the next day in the application time zone (Europe/Berlin)
  private val now = Instant.parse("2026-06-14T23:30:00Z")

  private val gewicht = Property(id = PropertyId("p-gewicht"), name = "gewicht", type = PropertyType.DOUBLE)
  private val groesse = Property(id = PropertyId("p-groesse"), name = "groesse", type = PropertyType.DOUBLE)
  private val geburt = Property(id = PropertyId("p-geburt"), name = "geburt", type = PropertyType.DATE)
  private val aktiv = Property(id = PropertyId("p-aktiv"), name = "aktiv", type = PropertyType.BOOLEAN)
  private val schuhe = Property(id = PropertyId("p-schuhe"), name = "schuhe", type = PropertyType.REF, targetEntityId = EntityDefinitionId("e-schuh"))
  private val plz = Property(id = PropertyId("p-plz"), name = "plz", type = PropertyType.LONG)
  private val adresse = Property(id = PropertyId("p-adresse"), name = "adresse", type = PropertyType.OBJECT, nestedProperties = listOf(plz))

  private val config = EntityDefinition(
    id = EntityDefinitionId("e-config"),
    name = "Einstellungen",
    configuration = true,
    properties = listOf(Property(id = PropertyId("c-ziel"), name = "zielGewicht", type = PropertyType.DOUBLE)),
  )

  private fun entity(vararg computed: ComputedProperty) = EntityDefinition(
    id = EntityDefinitionId("e-1"),
    name = "Koerper",
    properties = listOf(gewicht, groesse, geburt, aktiv, schuhe, adresse),
    computedProperties = computed.toList(),
  )

  private fun computed(id: String, type: PropertyType, expression: String?, unit: PropertyUnit? = null) =
    ComputedProperty(id = ComputedPropertyId(id), name = id, type = type, expression = expression, unit = unit)

  private val data = mapOf(
    "p-gewicht" to "81",
    "p-groesse" to "1.8",
    "p-geburt" to "1990-06-16",
    "p-aktiv" to "true",
    "p-schuhe" to "schuh-1",
    "p-adresse" to encodeObjectValue(mapOf("p-plz" to "10115")),
  )

  @Test
  fun `no expressions, no values`() {
    assertThat(service.computeValues(entity(), data, now)).isEmpty()
    assertThat(service.computeValues(entity(computed("x", PropertyType.LONG, null)), data, now)).isEmpty()
  }

  @Test
  fun `computed properties are evaluated in definition order and may read the ones before them`() {
    val result = service.computeValues(
      entity(
        computed("bmi", PropertyType.DOUBLE, "round(gewicht / groesse ^ 2, 1)"),
        computed("kategorie", PropertyType.STRING, "if(bmi >= 25, 'Übergewicht', 'Normal')"),
        computed("alter", PropertyType.LONG, "floor(days(geburt, today()) / 365.25)"),
      ),
      data,
      now,
    )

    assertThat(result).containsExactly(entry("bmi", "25.0"), entry("kategorie", "Übergewicht"), entry("alter", "35"))
  }

  @Test
  fun `results are stored in the computed property's type, numbers with a unit as raw values`() {
    val kilometers = PropertyUnit(UnitFamily.DISTANCE, DistanceGranularity.KILOMETERS, DistanceGranularity.KILOMETERS)
    val result = service.computeValues(
      entity(
        computed("ja", PropertyType.LONG, "aktiv"),
        computed("strecke", PropertyType.DOUBLE, "gewicht / 2", kilometers),
        computed("datum", PropertyType.DATE, "today()"),
        computed("zeit", PropertyType.DATETIME, "now()"),
        computed("plz", PropertyType.STRING, "adresse.plz"),
        computed("schuh", PropertyType.STRING, "schuhe"),
      ),
      data,
      now,
    )

    assertThat(result["ja"]).isEqualTo("1")
    assertThat(result["strecke"]).isEqualTo("40.5")
    assertThat(result["datum"]).isEqualTo("2026-06-15")
    assertThat(result["zeit"]).isEqualTo("2026-06-15T01:30")
    assertThat(result["plz"]).isEqualTo("10115")
    assertThat(result["schuh"]).isEqualTo("schuh-1")
  }

  @Test
  fun `config fields are read by name through the configuration entity`() {
    val abstand = entity(computed("abstand", PropertyType.DOUBLE, "gewicht - config.zielGewicht"))

    assertThat(service.computeValues(abstand, data, now, mapOf("c-ziel" to "75"), config)["abstand"]).isEqualTo("6.0")
    assertThat(service.computeValues(abstand, data, now)["abstand"]).isNull()
  }

  @Test
  fun `invalid expressions, missing values and Kotlin scripts stored before yield no value`() {
    val legacy = ComputedProperty(id = ComputedPropertyId("legacy"), name = "legacy", type = PropertyType.LONG, legacyScript = "42L")
    val result = service.computeValues(
      entity(
        computed("kaputt", PropertyType.DOUBLE, "gewicht +"),
        computed("leer", PropertyType.DOUBLE, "unbekannt * 2"),
        computed("division", PropertyType.DOUBLE, "gewicht / 0"),
        computed("ok", PropertyType.DOUBLE, "gewicht"),
        legacy,
      ),
      data,
      now,
    )

    assertThat(result["kaputt"]).isNull()
    assertThat(result["leer"]).isNull()
    assertThat(result["division"]).isNull()
    assertThat(result["legacy"]).isNull()
    assertThat(result["ok"]).isEqualTo("81.0")
  }
}
