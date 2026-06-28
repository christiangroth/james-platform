package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.app.App
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.ComputedProperty
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.Report
import de.chrgroth.james.platform.domain.model.app.formatDurationValue
import de.chrgroth.james.platform.domain.model.user.User
import io.quarkus.qute.TemplateExtension
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import java.time.Instant as JavaInstant

@TemplateExtension
@Suppress("Unused")
object TemplateFormattingExtensions {

  /**
   * Returns the username string value. Used because Username is a JvmInline value class,
   * whose JVM getter is name-mangled, preventing Qute from resolving it via reflection.
   */
  @JvmStatic
  fun username(user: User): String = user.username.value

  /**
   * Returns the App id string value. Used because AppId is a JvmInline value class
   * whose JVM getter is name-mangled, preventing Qute from resolving it via reflection.
   */
  @JvmStatic
  fun id(app: App): String = app.id.value

  /**
   * Returns the App name string value. Used because AppName is a JvmInline value class
   * whose JVM getter is name-mangled, preventing Qute from resolving it via reflection.
   */
  @JvmStatic
  fun name(app: App): String = app.name.value

  /**
   * Returns the AppVersion id string value. Used because AppVersionId is a JvmInline value class
   * whose JVM getter is name-mangled, preventing Qute from resolving it via reflection.
   */
  @JvmStatic
  fun id(version: AppVersion): String = version.id.value

  /**
   * Returns the AppVersion versionNumber string value. Used because VersionNumber is a JvmInline value class
   * whose JVM getter is name-mangled, preventing Qute from resolving it via reflection.
   * Returns null when the draft version has no version number yet.
   */
  @JvmStatic
  fun versionNumber(version: AppVersion): String? = version.versionNumber?.value

  /**
   * Returns the EntityDefinition id string value. Used because EntityDefinitionId is a JvmInline value class
   * whose JVM getter is name-mangled, preventing Qute from resolving it via reflection.
   */
  @JvmStatic
  fun id(entity: EntityDefinition): String = entity.id.value

  /**
   * Returns the Report id string value. Used because ReportId is a JvmInline value class
   * whose JVM getter is name-mangled, preventing Qute from resolving it via reflection.
   */
  @JvmStatic
  fun id(report: Report): String = report.id.value

  /**
   * Returns the Property id string value. Used because PropertyId is a JvmInline value class
   * whose JVM getter is name-mangled, preventing Qute from resolving it via reflection.
   */
  @JvmStatic
  fun id(property: Property): String = property.id.value

  private val DATETIME_FORMATTER by lazy { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()) }
  private val DATETIME_SHORT_FORMATTER by lazy { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()) }

  private const val SECONDS_PER_MINUTE = 60L
  private const val SECONDS_PER_HOUR = 3600L

  @JvmStatic
  fun formatted(value: Long): String = String.format(Locale.US, "%,d", value).replace(",", ".")

  @JvmStatic
  fun formatted(value: Int): String = String.format(Locale.US, "%,d", value).replace(",", ".")

  @JvmStatic
  fun formatted(instant: Instant): String = DATETIME_FORMATTER.format(instant.toJavaInstant())

  @JvmStatic
  fun formatted(instant: JavaInstant): String = DATETIME_FORMATTER.format(instant)

  @JvmStatic
  fun formattedShort(instant: Instant): String = DATETIME_SHORT_FORMATTER.format(instant.toJavaInstant())

  /** Formats a duration given in seconds as `m:ss` (e.g. for a recently-played track). */
  @JvmStatic
  fun formattedDuration(durationSeconds: Long): String {
    val minutes = durationSeconds / SECONDS_PER_MINUTE
    val seconds = durationSeconds % SECONDS_PER_MINUTE
    return "%d:%02d".format(minutes, seconds)
  }

  /** Returns the number of constraints on the property. */
  @JvmStatic
  fun constraintCount(property: Property): Int = property.constraints.size

  /** Returns true if the property has a UniqueKey constraint. */
  @JvmStatic
  fun constraintUniqueKey(property: Property): Boolean =
    property.constraints.any { it is PropertyConstraint.UniqueKey }

  private inline fun <reified T : PropertyConstraint> constraintValue(constraints: Set<PropertyConstraint>, selector: (T) -> Any): String =
    constraints.filterIsInstance<T>().firstOrNull()?.let { selector(it).toString() } ?: ""

  /** Returns the MinLong constraint value, or empty string if not set. */
  @JvmStatic
  fun constraintMinLong(property: Property): String = constraintValue<PropertyConstraint.MinLong>(property.constraints) { it.min }

  /** Returns the MaxLong constraint value, or empty string if not set. */
  @JvmStatic
  fun constraintMaxLong(property: Property): String = constraintValue<PropertyConstraint.MaxLong>(property.constraints) { it.max }

  /** Returns the StepLong constraint value, or empty string if not set. */
  @JvmStatic
  fun constraintStepLong(property: Property): String = constraintValue<PropertyConstraint.StepLong>(property.constraints) { it.step }

  /** Returns the MinDouble constraint value, or empty string if not set. */
  @JvmStatic
  fun constraintMinDouble(property: Property): String = constraintValue<PropertyConstraint.MinDouble>(property.constraints) { it.min }

  /** Returns the MaxDouble constraint value, or empty string if not set. */
  @JvmStatic
  fun constraintMaxDouble(property: Property): String = constraintValue<PropertyConstraint.MaxDouble>(property.constraints) { it.max }

  /** Returns the StepDouble constraint value, or empty string if not set. */
  @JvmStatic
  fun constraintStepDouble(property: Property): String = constraintValue<PropertyConstraint.StepDouble>(property.constraints) { it.step }

  /**
   * Returns the configured Step constraint value for LONG/DOUBLE properties, or empty string if none is set.
   * Used to decide whether to show increment/decrement buttons.
   */
  @JvmStatic
  fun constraintStep(property: Property): String = when (property.type) {
    PropertyType.LONG -> constraintStepLong(property)
    PropertyType.DOUBLE -> constraintStepDouble(property)
    else -> ""
  }

  /**
   * Returns the HTML `step` attribute value for LONG/DOUBLE properties: the configured Step constraint if set,
   * otherwise "any" for DOUBLE (to allow arbitrary decimals) or empty string for LONG (native default step of 1).
   * Returns empty string for all other property types.
   */
  @JvmStatic
  fun numberStepAttribute(property: Property): String {
    val step = constraintStep(property)
    return when {
      step.isNotEmpty() -> step
      property.type == PropertyType.DOUBLE -> "any"
      else -> ""
    }
  }

  /** Returns the MinDate constraint value, or empty string if not set. */
  @JvmStatic
  fun constraintMinDate(property: Property): String = constraintValue<PropertyConstraint.MinDate>(property.constraints) { it.min }

  /** Returns the MaxDate constraint value, or empty string if not set. */
  @JvmStatic
  fun constraintMaxDate(property: Property): String = constraintValue<PropertyConstraint.MaxDate>(property.constraints) { it.max }

  /** Returns the MinTime constraint value, or empty string if not set. */
  @JvmStatic
  fun constraintMinTime(property: Property): String = constraintValue<PropertyConstraint.MinTime>(property.constraints) { it.min }

  /** Returns the MaxTime constraint value, or empty string if not set. */
  @JvmStatic
  fun constraintMaxTime(property: Property): String = constraintValue<PropertyConstraint.MaxTime>(property.constraints) { it.max }

  /** Returns the MinDatetime constraint value, or empty string if not set. */
  @JvmStatic
  fun constraintMinDatetime(property: Property): String = constraintValue<PropertyConstraint.MinDatetime>(property.constraints) { it.min }

  /** Returns the MaxDatetime constraint value, or empty string if not set. */
  @JvmStatic
  fun constraintMaxDatetime(property: Property): String = constraintValue<PropertyConstraint.MaxDatetime>(property.constraints) { it.max }

  /** Returns the MinDuration constraint value formatted as "hh:mm:ss", or empty string if not set. */
  @JvmStatic
  fun constraintMinDuration(property: Property): String =
    property.constraints.filterIsInstance<PropertyConstraint.MinDuration>().firstOrNull()?.let { formatDurationValue(it.min) } ?: ""

  /** Returns the MaxDuration constraint value formatted as "hh:mm:ss", or empty string if not set. */
  @JvmStatic
  fun constraintMaxDuration(property: Property): String =
    property.constraints.filterIsInstance<PropertyConstraint.MaxDuration>().firstOrNull()?.let { formatDurationValue(it.max) } ?: ""

  /** Returns the MinLength constraint value, or empty string if not set. */
  @JvmStatic
  fun constraintMinLength(property: Property): String = constraintValue<PropertyConstraint.MinLength>(property.constraints) { it.min }

  /** Returns the MaxLength constraint value, or empty string if not set. */
  @JvmStatic
  fun constraintMaxLength(property: Property): String = constraintValue<PropertyConstraint.MaxLength>(property.constraints) { it.max }

  /** Returns the Pattern constraint regex value, or empty string if not set. */
  @JvmStatic
  fun constraintPattern(property: Property): String = constraintValue<PropertyConstraint.Pattern>(property.constraints) { it.regex }

  /** Returns the MinSize constraint value, or empty string if not set. */
  @JvmStatic
  fun constraintMinSize(property: Property): String = constraintValue<PropertyConstraint.MinSize>(property.constraints) { it.min }

  /** Returns the MaxSize constraint value, or empty string if not set. */
  @JvmStatic
  fun constraintMaxSize(property: Property): String = constraintValue<PropertyConstraint.MaxSize>(property.constraints) { it.max }

  /** Returns the LIST item's MinLong constraint value, or empty string if not set. */
  @JvmStatic
  fun itemConstraintMinLong(property: Property): String = constraintValue<PropertyConstraint.MinLong>(property.itemConstraints) { it.min }

  /** Returns the LIST item's MaxLong constraint value, or empty string if not set. */
  @JvmStatic
  fun itemConstraintMaxLong(property: Property): String = constraintValue<PropertyConstraint.MaxLong>(property.itemConstraints) { it.max }

  /** Returns the LIST item's MinDouble constraint value, or empty string if not set. */
  @JvmStatic
  fun itemConstraintMinDouble(property: Property): String = constraintValue<PropertyConstraint.MinDouble>(property.itemConstraints) { it.min }

  /** Returns the LIST item's MaxDouble constraint value, or empty string if not set. */
  @JvmStatic
  fun itemConstraintMaxDouble(property: Property): String = constraintValue<PropertyConstraint.MaxDouble>(property.itemConstraints) { it.max }

  /** Returns the LIST item's MinLength constraint value, or empty string if not set. */
  @JvmStatic
  fun itemConstraintMinLength(property: Property): String = constraintValue<PropertyConstraint.MinLength>(property.itemConstraints) { it.min }

  /** Returns the LIST item's MaxLength constraint value, or empty string if not set. */
  @JvmStatic
  fun itemConstraintMaxLength(property: Property): String = constraintValue<PropertyConstraint.MaxLength>(property.itemConstraints) { it.max }

  /** Returns the LIST item's Pattern constraint regex value, or empty string if not set. */
  @JvmStatic
  fun itemConstraintPattern(property: Property): String = constraintValue<PropertyConstraint.Pattern>(property.itemConstraints) { it.regex }

  /** Returns the default value of the property, or empty string if not set. */
  @JvmStatic
  fun defaultValue(property: Property): String = property.default ?: ""

  /** Returns the smart default script of the property, or empty string if not set. */
  @JvmStatic
  fun smartDefault(property: Property): String = property.smartDefault ?: ""

  /** Returns the value proposals property IDs as a JSON array string for use in data attributes. */
  @JvmStatic
  fun valueProposals(property: Property): String =
    property.valueProposals.joinToString(",")

  /** Returns true if the property has any value proposals defined. */
  @JvmStatic
  fun hasValueProposals(property: Property): Boolean = property.valueProposals.isNotEmpty()

  /** Returns the target entity id of a Reference property, or empty string if not set. */
  @JvmStatic
  fun targetEntityId(property: Property): String = property.targetEntityId?.value ?: ""

  /** Returns the name of the target entity of a Reference property within the given version,
   * or empty string if not set or no longer found.
   */
  @JvmStatic
  fun targetEntityName(property: Property, version: AppVersion): String =
    property.targetEntityId?.let { id -> version.entityDefinitions.find { it.id == id }?.name } ?: ""

  /** Returns a sorted list of human-readable constraint text representations for the property,
   * using the same format as the version diff view (e.g. "min:0", "max:100", "unique-key").
   * Returns an empty list if no constraints are defined.
   */
  @JvmStatic
  fun constraintTexts(property: Property): List<String> {
    val texts = constraintTextsFor(property.constraints).toMutableList()
    if (property.type == PropertyType.LIST) {
      texts += constraintTextsFor(property.itemConstraints).map { "item-$it" }
    }
    return texts
  }

  private fun constraintTextsFor(constraints: Set<PropertyConstraint>): List<String> =
    constraints
      .sortedWith(compareBy({ it.javaClass.name }, { it.toString() }))
      .map { constraint ->
        when (constraint) {
          is PropertyConstraint.UniqueKey -> "unique-key"
          is PropertyConstraint.MinLong -> "min:${constraint.min}"
          is PropertyConstraint.MaxLong -> "max:${constraint.max}"
          is PropertyConstraint.StepLong -> "step:${constraint.step}"
          is PropertyConstraint.MinDouble -> "min:${constraint.min}"
          is PropertyConstraint.MaxDouble -> "max:${constraint.max}"
          is PropertyConstraint.StepDouble -> "step:${constraint.step}"
          is PropertyConstraint.MinLength -> "min-length:${constraint.min}"
          is PropertyConstraint.MaxLength -> "max-length:${constraint.max}"
          is PropertyConstraint.Pattern -> "pattern:${constraint.regex}"
          is PropertyConstraint.MinSize -> "min-size:${constraint.min}"
          is PropertyConstraint.MaxSize -> "max-size:${constraint.max}"
          is PropertyConstraint.MinDate -> "min:${constraint.min}"
          is PropertyConstraint.MaxDate -> "max:${constraint.max}"
          is PropertyConstraint.MinTime -> "min:${constraint.min}"
          is PropertyConstraint.MaxTime -> "max:${constraint.max}"
          is PropertyConstraint.MinDatetime -> "min:${constraint.min}"
          is PropertyConstraint.MaxDatetime -> "max:${constraint.max}"
          is PropertyConstraint.MinDuration -> "min:${constraint.min}"
          is PropertyConstraint.MaxDuration -> "max:${constraint.max}"
        }
      }

  /** Returns the entity's property names joined as a comma-separated string,
   * or an empty string if the entity has no properties.
   */
  @JvmStatic
  fun propertyNames(entity: EntityDefinition): String =
    entity.properties.joinToString(", ") { it.name }

  /** Returns the entity's sort order as a comma-separated string of "PropertyName ASC/DESC",
   * or an empty string if no sort criteria are defined.
   */
  @JvmStatic
  fun sortOrderText(entity: EntityDefinition): String =
    entity.sortBy.joinToString(", ") { criteria ->
      val propName = entity.properties.find { it.id.value == criteria.propertyId }?.name ?: criteria.propertyId
      "$propName ${criteria.direction.name}"
    }

  /** Returns the AppData id string value. Used because AppDataId is a JvmInline value class
   * whose JVM getter is name-mangled, preventing Qute from resolving it via reflection.
   */
  @JvmStatic
  fun id(appData: AppData): String = appData.id.value

  /**
   * Returns the HTML input type suitable for generating a form field for the given property.
   * Used in the app-data-new template to render the correct input element per property type.
   */
  @JvmStatic
  fun htmlInputType(property: Property): String = inputTypeFor(property.type)

  /**
   * Returns the HTML input type suitable for generating a form field for the items of a LIST property.
   * Used in the app-data templates to render the correct input element per list item type.
   */
  @JvmStatic
  fun itemHtmlInputType(property: Property): String = inputTypeFor(property.listItemType)

  private fun inputTypeFor(type: PropertyType?): String = when (type) {
    PropertyType.BOOLEAN -> "checkbox"
    PropertyType.LONG, PropertyType.DOUBLE -> "number"
    PropertyType.DATE -> "date"
    PropertyType.TIME -> "time"
    PropertyType.DATETIME -> "datetime-local"
    PropertyType.REF -> "select"
    else -> "text"
  }

  /** Returns the LIST item type name of the property, or empty string if not a LIST or not set. */
  @JvmStatic
  fun listItemType(property: Property): String = property.listItemType?.name ?: ""

  /** Returns entity definitions in their stored order (as defined by the developer). */
  @JvmStatic
  fun sortedEntityDefinitions(version: AppVersion): List<EntityDefinition> =
    version.entityDefinitions

  /** Returns reports sorted alphabetically by name. */
  @JvmStatic
  fun sortedReports(version: AppVersion): List<Report> =
    version.reports.sortedBy { it.name }

  /**
   * Returns the ComputedProperty id string value. Used because ComputedPropertyId is a JvmInline value class
   * whose JVM getter is name-mangled, preventing Qute from resolving it via reflection.
   */
  @JvmStatic
  fun id(computedProperty: ComputedProperty): String = computedProperty.id.value

  /** Returns the script of the computed property, or empty string if not set. */
  @JvmStatic
  fun script(computedProperty: ComputedProperty): String = computedProperty.script ?: ""
}
