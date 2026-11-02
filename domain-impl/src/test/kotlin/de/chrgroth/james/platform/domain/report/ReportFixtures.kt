package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.ComputedProperty
import de.chrgroth.james.platform.domain.model.app.ComputedPropertyId
import de.chrgroth.james.platform.domain.model.app.CurrencyGranularity
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.UnitFamily
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import java.time.Instant

/** A small "Konzerte" App: Band (name) and Konzert (datum, band -> Band, ort, preis in cents, besucher, computed jahr). */
internal object ReportFixtures {

  val band = EntityDefinition(
    id = EntityDefinitionId("e-band"),
    name = "Band",
    displayText = "{name}",
    properties = listOf(
      Property(PropertyId("p-band-name"), "name", PropertyType.STRING),
      Property(PropertyId("p-band-genre"), "genre", PropertyType.STRING),
    ),
  )

  val konzert = EntityDefinition(
    id = EntityDefinitionId("e-konzert"),
    name = "Konzert",
    properties = listOf(
      Property(PropertyId("p-datum"), "datum", PropertyType.DATE),
      Property(PropertyId("p-band"), "band", PropertyType.REF, targetEntityId = band.id),
      Property(PropertyId("p-ort"), "ort", PropertyType.STRING),
      Property(
        PropertyId("p-preis"),
        "preis",
        PropertyType.LONG,
        unit = PropertyUnit(UnitFamily.CURRENCY, CurrencyGranularity.CENTS, CurrencyGranularity.EUROS),
      ),
      Property(PropertyId("p-besucher"), "besucher", PropertyType.LONG),
    ),
    computedProperties = listOf(ComputedProperty(ComputedPropertyId("c-jahr"), "jahr", PropertyType.STRING, "data[\"p-datum\"]")),
  )

  val entities = listOf(band, konzert)

  private var created = Instant.parse("2026-01-01T00:00:00Z")

  fun bandData(id: String, name: String, genre: String = "Rock") = appData(id, band, mapOf("p-band-name" to name, "p-band-genre" to genre))

  fun konzertData(id: String, datum: String, bandId: String?, ort: String, preisCents: Long?, besucher: Long = 100) = appData(
    id,
    konzert,
    mapOf("p-datum" to datum, "p-band" to bandId, "p-ort" to ort, "p-preis" to preisCents?.toString(), "p-besucher" to besucher.toString()),
  )

  private fun appData(id: String, entity: EntityDefinition, data: Map<String, String?>): AppData {
    created = created.plusSeconds(60)
    return AppData(
      id = AppDataId(id),
      userId = "user",
      installedAppId = InstalledAppId("inst-1"),
      appVersion = VersionNumber("1.0.0"),
      lastValidatedWithVersion = VersionNumber("1.0.0"),
      entityType = entity.id,
      objectVersion = 1,
      createdAt = created,
      lastChangedAt = created,
      data = data,
    )
  }
}
