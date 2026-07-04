package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.adapter.`in`.web.i18n.AppMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.DeveloperMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.UserMessages
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyType
import io.quarkus.arc.Arc
import io.quarkus.qute.TemplateExtension

/**
 * Provides translated display labels for property types and constraints in Qute templates.
 * Uses [Arc] to look up the CDI-managed message bundles because template extension methods must be static.
 */
@TemplateExtension
@Suppress("Unused")
object PropertyLabelTemplateExtensions {

  private val appMessages: AppMessages by lazy { Arc.container().instance(AppMessages::class.java).get() }
  private val developerMessages: DeveloperMessages by lazy { Arc.container().instance(DeveloperMessages::class.java).get() }
  private val userMessages: UserMessages by lazy { Arc.container().instance(UserMessages::class.java).get() }

  /** Returns the translated label for the given property type (e.g. "Text", "Ganzzahl"). */
  @JvmStatic
  fun propertyTypeLabel(type: PropertyType): String = when (type) {
    PropertyType.STRING -> appMessages.propertyTypeString()
    PropertyType.LONG -> appMessages.propertyTypeLong()
    PropertyType.DOUBLE -> appMessages.propertyTypeDouble()
    PropertyType.BOOLEAN -> appMessages.propertyTypeBoolean()
    PropertyType.DATE -> appMessages.propertyTypeDate()
    PropertyType.TIME -> appMessages.propertyTypeTime()
    PropertyType.DATETIME -> appMessages.propertyTypeDatetime()
    PropertyType.DURATION -> appMessages.propertyTypeDuration()
    PropertyType.REF -> appMessages.propertyTypeReference()
    PropertyType.LIST -> appMessages.propertyTypeList()
    PropertyType.OBJECT -> appMessages.propertyTypeObject()
  }

  /** Returns the translated label for the property's LIST item type, or empty string if not a LIST or not set.
   * Named distinctly from [propertyTypeLabel] because `listItemType` already resolves to a String-returning
   * template extension elsewhere, which would shadow a direct `prop.listItemType.propertyTypeLabel` call.
   */
  @JvmStatic
  fun listItemTypeLabel(property: Property): String = property.listItemType?.let { propertyTypeLabel(it) } ?: ""

  /** Returns a sorted list of translated, human-readable constraint text representations for the property
   * (e.g. "Minimalwert: 0", "Eindeutiger Schlüssel"). Returns an empty list if no constraints are defined.
   */
  @JvmStatic
  fun constraintTexts(property: Property): List<String> {
    val texts = constraintTextsFor(property.constraints).toMutableList()
    if (property.type == PropertyType.LIST) {
      texts += constraintTextsFor(property.itemConstraints).map { "${developerMessages.developerItemConstraintsLabel()}: $it" }
    }
    return texts
  }

  /** Explicit display order for constraint kinds: groups each property's own min/max pair together with min
   * before max. Sorting by class name alone (the natural alternative) would put "Max…" before "Min…" for every
   * pair, since they only differ from the third letter on, which reads confusingly to users.
   */
  private val CONSTRAINT_ORDER: List<Class<out PropertyConstraint>> = listOf(
    PropertyConstraint.UniqueKey::class.java,
    PropertyConstraint.MinLong::class.java,
    PropertyConstraint.MaxLong::class.java,
    PropertyConstraint.StepLong::class.java,
    PropertyConstraint.MinDouble::class.java,
    PropertyConstraint.MaxDouble::class.java,
    PropertyConstraint.StepDouble::class.java,
    PropertyConstraint.MinLength::class.java,
    PropertyConstraint.MaxLength::class.java,
    PropertyConstraint.Pattern::class.java,
    PropertyConstraint.MinSize::class.java,
    PropertyConstraint.MaxSize::class.java,
    PropertyConstraint.MinDate::class.java,
    PropertyConstraint.MaxDate::class.java,
    PropertyConstraint.MinTime::class.java,
    PropertyConstraint.MaxTime::class.java,
    PropertyConstraint.MinDatetime::class.java,
    PropertyConstraint.MaxDatetime::class.java,
    PropertyConstraint.MinDuration::class.java,
    PropertyConstraint.MaxDuration::class.java,
  )

  private fun constraintTextsFor(constraints: Set<PropertyConstraint>): List<String> =
    constraints
      .sortedBy { CONSTRAINT_ORDER.indexOf(it.javaClass) }
      .map { constraint ->
        when (constraint) {
          is PropertyConstraint.UniqueKey -> developerMessages.developerUniqueKeyLabel()
          is PropertyConstraint.MinLong -> "${developerMessages.developerMinValueLabel()}: ${constraint.min}"
          is PropertyConstraint.MaxLong -> "${developerMessages.developerMaxValueLabel()}: ${constraint.max}"
          is PropertyConstraint.StepLong -> "${developerMessages.developerStepLabel()}: ${constraint.step}"
          is PropertyConstraint.MinDouble -> "${developerMessages.developerMinValueLabel()}: ${constraint.min}"
          is PropertyConstraint.MaxDouble -> "${developerMessages.developerMaxValueLabel()}: ${constraint.max}"
          is PropertyConstraint.StepDouble -> "${developerMessages.developerStepLabel()}: ${constraint.step}"
          is PropertyConstraint.MinLength -> "${developerMessages.developerMinLengthLabel()}: ${constraint.min}"
          is PropertyConstraint.MaxLength -> "${developerMessages.developerMaxLengthLabel()}: ${constraint.max}"
          is PropertyConstraint.Pattern -> "${developerMessages.developerPatternLabel()}: ${constraint.regex}"
          is PropertyConstraint.MinSize -> "${developerMessages.developerMinSizeLabel()}: ${constraint.min}"
          is PropertyConstraint.MaxSize -> "${developerMessages.developerMaxSizeLabel()}: ${constraint.max}"
          is PropertyConstraint.MinDate -> "${developerMessages.developerMinDateLabel()}: ${constraint.min}"
          is PropertyConstraint.MaxDate -> "${developerMessages.developerMaxDateLabel()}: ${constraint.max}"
          is PropertyConstraint.MinTime -> "${developerMessages.developerMinTimeLabel()}: ${constraint.min}"
          is PropertyConstraint.MaxTime -> "${developerMessages.developerMaxTimeLabel()}: ${constraint.max}"
          is PropertyConstraint.MinDatetime -> "${developerMessages.developerMinDatetimeLabel()}: ${constraint.min}"
          is PropertyConstraint.MaxDatetime -> "${developerMessages.developerMaxDatetimeLabel()}: ${constraint.max}"
          is PropertyConstraint.MinDuration -> "${developerMessages.developerMinDurationLabel()}: ${constraint.min}"
          is PropertyConstraint.MaxDuration -> "${developerMessages.developerMaxDurationLabel()}: ${constraint.max}"
        }
      }

  /** Returns the short, translated hint text for a single constraint (e.g. "Min: 0", "Format: ^[A-Z].*"), meant
   * to be combined with other constraints and shown directly under the input.
   */
  private fun shortConstraintText(constraint: PropertyConstraint): String = when (constraint) {
    is PropertyConstraint.UniqueKey -> userMessages.userHintUniqueKeyLabel()
    is PropertyConstraint.MinLong -> "${userMessages.userHintMinLabel()}: ${constraint.min}"
    is PropertyConstraint.MaxLong -> "${userMessages.userHintMaxLabel()}: ${constraint.max}"
    is PropertyConstraint.StepLong -> "${userMessages.userHintStepLabel()}: ${constraint.step}"
    is PropertyConstraint.MinDouble -> "${userMessages.userHintMinLabel()}: ${constraint.min}"
    is PropertyConstraint.MaxDouble -> "${userMessages.userHintMaxLabel()}: ${constraint.max}"
    is PropertyConstraint.StepDouble -> "${userMessages.userHintStepLabel()}: ${constraint.step}"
    is PropertyConstraint.MinLength -> "${userMessages.userHintMinLengthLabel()}: ${constraint.min}"
    is PropertyConstraint.MaxLength -> "${userMessages.userHintMaxLengthLabel()}: ${constraint.max}"
    is PropertyConstraint.Pattern -> "${userMessages.userHintPatternLabel()}: ${constraint.regex}"
    is PropertyConstraint.MinSize -> "${userMessages.userHintMinSizeLabel()}: ${constraint.min}"
    is PropertyConstraint.MaxSize -> "${userMessages.userHintMaxSizeLabel()}: ${constraint.max}"
    is PropertyConstraint.MinDate -> "${userMessages.userHintMinDateLabel()}: ${constraint.min}"
    is PropertyConstraint.MaxDate -> "${userMessages.userHintMaxDateLabel()}: ${constraint.max}"
    is PropertyConstraint.MinTime -> "${userMessages.userHintMinTimeLabel()}: ${constraint.min}"
    is PropertyConstraint.MaxTime -> "${userMessages.userHintMaxTimeLabel()}: ${constraint.max}"
    is PropertyConstraint.MinDatetime -> "${userMessages.userHintMinDatetimeLabel()}: ${constraint.min}"
    is PropertyConstraint.MaxDatetime -> "${userMessages.userHintMaxDatetimeLabel()}: ${constraint.max}"
    is PropertyConstraint.MinDuration -> "${userMessages.userHintMinDurationLabel()}: ${constraint.min}"
    is PropertyConstraint.MaxDuration -> "${userMessages.userHintMaxDurationLabel()}: ${constraint.max}"
  }

  private fun constraintHintFor(constraints: Set<PropertyConstraint>): String =
    constraints.sortedBy { CONSTRAINT_ORDER.indexOf(it.javaClass) }.joinToString(" · ") { shortConstraintText(it) }

  /** Returns a short, combined hint text for all of the property's own constraints (e.g. "Min: 0 · Max: 100"),
   * in logical (min-before-max) order, meant to be shown directly under the input. Returns empty string if the
   * property has no constraints.
   */
  @JvmStatic
  fun constraintHint(property: Property): String = constraintHintFor(property.constraints)

  /** Same as [constraintHint], but for a LIST property's item constraints. */
  @JvmStatic
  fun itemConstraintHint(property: Property): String = constraintHintFor(property.itemConstraints)
}
