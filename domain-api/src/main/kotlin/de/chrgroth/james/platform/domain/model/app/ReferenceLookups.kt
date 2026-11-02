package de.chrgroth.james.platform.domain.model.app

/**
 * The target entity and lookup property of this REF property's [Property.referenceLookup] within [entities], or null if no lookup is
 * configured or it is no longer valid (see [isValidReferenceLookupProperty], ADR 0026).
 */
fun Property.referenceLookupTarget(entities: List<EntityDefinition>): Pair<EntityDefinition, Property>? {
  val lookup = referenceLookup ?: return null
  if (type != PropertyType.REF) return null
  val target = entities.find { it.id == targetEntityId } ?: return null
  val lookupProperty = target.properties.find { it.id == lookup.propertyId } ?: return null
  return if (isValidReferenceLookupProperty(target, lookupProperty)) target to lookupProperty else null
}

/** A lookup property must be a STRING property and the only unique key of its entity, so that a lookup text identifies at most one object. */
fun isValidReferenceLookupProperty(target: EntityDefinition, property: Property): Boolean =
  property.type == PropertyType.STRING && target.uniqueKeyProperties.map { it.id } == listOf(property.id)

/**
 * Form-data key prefix carrying the text typed into a REF property with a [ReferenceLookup], e.g. `reflookup_<propertyId>`. When present,
 * it replaces the property's regular `prop_<propertyId>` id value: the text is resolved to the target object's id on save.
 */
const val REFERENCE_LOOKUP_FORM_PREFIX = "reflookup_"

/**
 * Form-data key prefix for the further property values of a target object auto-created by a [ReferenceLookup], e.g.
 * `refnew_<refPropertyId>.<targetPropertyId>` — shaped like the target entity's own `prop_<targetPropertyId>` keys below that prefix.
 */
const val REFERENCE_NEW_FORM_PREFIX = "refnew_"
