package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.SortCriteria
import de.chrgroth.james.platform.domain.model.app.SortDirection
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.model.app.encodeListValue
import de.chrgroth.james.platform.domain.port.`in`.app.SmartDefaultContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class SmartDefaultServiceTests {

  private val service = SmartDefaultService()

  // Tuesday 2026-11-03, 09:15 in the application time zone (Europe/Berlin)
  private val now = Instant.parse("2026-11-03T08:15:42Z")

  private val config = EntityDefinition(
    id = EntityDefinitionId("e-config"),
    name = "Einstellungen",
    configuration = true,
    properties = listOf(Property(id = PropertyId("c-feiertage"), name = "feiertage", type = PropertyType.LIST, listItemType = PropertyType.DATE)),
  )

  private val start = Property(id = PropertyId("p-start"), name = "start", type = PropertyType.DATE, smartDefault = "today()")
  private val ende = Property(id = PropertyId("p-ende"), name = "ende", type = PropertyType.DATE, smartDefault = "start")
  private val tage = Property(id = PropertyId("p-tage"), name = "tage", type = PropertyType.LONG, smartDefault = "workdays(start, ende, config.feiertage)")
  private val urlaub = EntityDefinition(id = EntityDefinitionId("e-urlaub"), name = "Urlaub", properties = listOf(start, ende, tage))

  private val datum = Property(id = PropertyId("p-datum"), name = "datum", type = PropertyType.DATE, smartDefault = "coalesce(next_day(datum), today())")
  private val schuhe = Property(id = PropertyId("p-schuhe"), name = "schuhe", type = PropertyType.REF, targetEntityId = EntityDefinitionId("e-schuh"), smartDefault = "last(schuhe)")
  private val distanz = Property(id = PropertyId("p-distanz"), name = "distanz", type = PropertyType.DOUBLE, default = "10", smartDefault = null)
  private val tempo = Property(id = PropertyId("p-tempo"), name = "tempo", type = PropertyType.DOUBLE, smartDefault = "distanz * 6")
  private val lauf = EntityDefinition(
    id = EntityDefinitionId("e-lauf"),
    name = "Lauf",
    properties = listOf(datum, schuhe, distanz, tempo),
    sortBy = listOf(SortCriteria("p-datum", SortDirection.DESC)),
  )

  private fun entry(id: String, entity: EntityDefinition, createdAt: String, vararg values: Pair<String, String?>) = AppData(
    id = AppDataId(id),
    userId = "user",
    installedAppId = InstalledAppId("inst-1"),
    appVersion = VersionNumber("1.0.0"),
    lastValidatedWithVersion = VersionNumber("1.0.0"),
    entityType = entity.id,
    objectVersion = 1,
    createdAt = java.time.Instant.parse(createdAt),
    lastChangedAt = java.time.Instant.parse(createdAt),
    data = values.toMap(),
  )

  @Test
  fun `smart defaults see today, static defaults and the smart defaults before them`() {
    val result = service.computeSmartDefaults(urlaub, SmartDefaultContext(now))
    val runResult = service.computeSmartDefaults(lauf, SmartDefaultContext(now))

    assertThat(result).containsEntry("p-start", "2026-11-03").containsEntry("p-ende", "2026-11-03").containsEntry("p-tage", "1")
    assertThat(runResult).containsEntry("p-datum", "2026-11-03").containsEntry("p-schuhe", null).containsEntry("p-tempo", "60.0")
  }

  @Test
  fun `form values win over defaults, so smart defaults reading other fields follow the form`() {
    val context = SmartDefaultContext(
      now = now,
      formValues = mapOf("p-start" to "2026-12-21", "p-ende" to "2027-01-03"),
      config = mapOf("c-feiertage" to encodeListValue(listOf("2026-12-24", "2026-12-25", "2026-12-31", "2027-01-01"))),
      configEntity = config,
    )

    val result = service.computeSmartDefaults(urlaub, context)

    assertThat(result["p-tage"]).isEqualTo("6")
    // the form's own values are untouched, only defaults are answered
    assertThat(result["p-ende"]).isEqualTo("2026-12-21")
  }

  @Test
  fun `last and next_day read the latest object of the entity by its time field`() {
    val entries = listOf(
      entry("l1", lauf, "2026-10-01T10:00:00Z", "p-datum" to "2026-10-30", "p-schuhe" to "schuh-alt"),
      entry("l2", lauf, "2026-10-02T10:00:00Z", "p-datum" to "2026-11-01", "p-schuhe" to "schuh-neu"),
      entry("l3", lauf, "2026-10-03T10:00:00Z", "p-datum" to "2026-10-31", "p-schuhe" to "schuh-mittel"),
      entry("u1", urlaub, "2026-10-04T10:00:00Z", "p-start" to "2027-01-01"),
    )

    val result = service.computeSmartDefaults(lauf, SmartDefaultContext(now, entries = { entries }))

    assertThat(result["p-datum"]).isEqualTo("2026-11-02")
    assertThat(result["p-schuhe"]).isEqualTo("schuh-neu")
  }

  @Test
  fun `without a time field the latest object is the newest one created`() {
    val notiz = Property(id = PropertyId("p-notiz"), name = "notiz", type = PropertyType.STRING, smartDefault = "last(notiz)")
    val entity = EntityDefinition(id = EntityDefinitionId("e-notiz"), name = "Notiz", properties = listOf(notiz))
    val entries = listOf(
      entry("n2", entity, "2026-10-02T10:00:00Z", "p-notiz" to "zweite"),
      entry("n1", entity, "2026-10-01T10:00:00Z", "p-notiz" to "erste"),
    )

    assertThat(service.computeSmartDefaults(entity, SmartDefaultContext(now, entries = { entries }))["p-notiz"]).isEqualTo("zweite")
  }

  @Test
  fun `entries are only loaded when a smart default needs them, invalid expressions and Kotlin scripts yield no value`() {
    var loaded = false
    val broken = Property(id = PropertyId("p-x"), name = "x", type = PropertyType.STRING, smartDefault = "concat(")
    val legacy = Property(id = PropertyId("p-y"), name = "y", type = PropertyType.STRING, legacySmartDefaultScript = "now.toString()")
    val entity = EntityDefinition(id = EntityDefinitionId("e-x"), name = "X", properties = listOf(start, broken, legacy))

    val result = service.computeSmartDefaults(entity, SmartDefaultContext(now, entries = { loaded = true; emptyList() }))

    assertThat(loaded).isFalse()
    assertThat(result).containsEntry("p-start", "2026-11-03").containsEntry("p-x", null).doesNotContainKey("p-y")
  }

  @Test
  fun `time and date-time smart defaults use the current minute`() {
    val zeit = Property(id = PropertyId("p-zeit"), name = "zeit", type = PropertyType.TIME, smartDefault = "time(now())")
    val zeitpunkt = Property(id = PropertyId("p-zeitpunkt"), name = "zeitpunkt", type = PropertyType.DATETIME, smartDefault = "now()")
    val entity = EntityDefinition(id = EntityDefinitionId("e-z"), name = "Z", properties = listOf(zeit, zeitpunkt))

    val result = service.computeSmartDefaults(entity, SmartDefaultContext(now))

    assertThat(result).containsEntry("p-zeit", "09:15").containsEntry("p-zeitpunkt", "2026-11-03T09:15")
  }

  @Test
  fun `form dependencies name the fields a smart default reads directly`() {
    assertThat(service.formDependencies(urlaub)).isEqualTo(mapOf("p-ende" to setOf("p-start"), "p-tage" to setOf("p-start", "p-ende")))
    assertThat(service.formDependencies(lauf)).isEqualTo(mapOf("p-tempo" to setOf("p-distanz")))
  }
}
