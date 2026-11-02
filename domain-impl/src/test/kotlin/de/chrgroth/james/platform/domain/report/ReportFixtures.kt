package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.ComputedProperty
import de.chrgroth.james.platform.domain.model.app.ComputedPropertyId
import de.chrgroth.james.platform.domain.model.app.CurrencyGranularity
import de.chrgroth.james.platform.domain.model.app.DistanceGranularity
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.MassGranularity
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

  val rad = EntityDefinition(
    id = EntityDefinitionId("e-rad"),
    name = "Rad",
    displayText = "{name}",
    properties = listOf(Property(PropertyId("p-rad-name"), "name", PropertyType.STRING)),
  )

  /** Fahrt: datum, distanz (meters, entered in km), rad -> Rad, gewicht (kg, DOUBLE), elektrisch (BOOLEAN), bewertung { zufriedenheit }. */
  val fahrt = EntityDefinition(
    id = EntityDefinitionId("e-fahrt"),
    name = "Fahrt",
    properties = listOf(
      Property(PropertyId("p-f-datum"), "datum", PropertyType.DATE),
      Property(
        PropertyId("p-f-distanz"),
        "distanz",
        PropertyType.LONG,
        unit = PropertyUnit(UnitFamily.DISTANCE, DistanceGranularity.METERS, DistanceGranularity.KILOMETERS),
      ),
      Property(PropertyId("p-f-rad"), "rad", PropertyType.REF, targetEntityId = rad.id),
      Property(PropertyId("p-f-gewicht"), "gewicht", PropertyType.DOUBLE, unit = PropertyUnit(UnitFamily.MASS, MassGranularity.KILOGRAMS, MassGranularity.KILOGRAMS)),
      Property(PropertyId("p-f-elektrisch"), "elektrisch", PropertyType.BOOLEAN),
      Property(
        PropertyId("p-f-bewertung"),
        "bewertung",
        PropertyType.OBJECT,
        nestedProperties = listOf(Property(PropertyId("p-f-zufriedenheit"), "zufriedenheit", PropertyType.LONG)),
      ),
    ),
  )

  val entities = listOf(band, konzert, rad, fahrt)

  private var created = Instant.parse("2026-01-01T00:00:00Z")

  fun bandData(id: String, name: String, genre: String = "Rock") = appData(id, band, mapOf("p-band-name" to name, "p-band-genre" to genre))

  fun konzertData(id: String, datum: String, bandId: String?, ort: String, preisCents: Long?, besucher: Long = 100) = appData(
    id,
    konzert,
    mapOf("p-datum" to datum, "p-band" to bandId, "p-ort" to ort, "p-preis" to preisCents?.toString(), "p-besucher" to besucher.toString()),
  )

  fun radData(id: String, name: String) = appData(id, rad, mapOf("p-rad-name" to name))

  fun fahrtData(id: String, datum: String, distanzMeters: Long?, radId: String?, gewicht: Double? = null, elektrisch: Boolean = false, zufriedenheit: Long? = null) =
    appData(
      id,
      fahrt,
      mapOf(
        "p-f-datum" to datum,
        "p-f-distanz" to distanzMeters?.toString(),
        "p-f-rad" to radId,
        "p-f-gewicht" to gewicht?.toString(),
        "p-f-elektrisch" to elektrisch.toString(),
        "p-f-bewertung" to zufriedenheit?.let { "{\"p-f-zufriedenheit\":\"$it\"}" },
      ),
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
