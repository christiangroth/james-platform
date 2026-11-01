package de.chrgroth.james.platform.domain.model.app

/**
 * The properties of this entity carrying [PropertyConstraint.UniqueKey]. Together they form the entity's single (possibly composite) unique key: an object
 * violates it only if another object of the same entity holds equal values in *all* of these properties - with a single key property this is plain
 * per-value uniqueness. There is deliberately no way to declare several independent keys on one entity.
 */
val EntityDefinition.uniqueKeyProperties: List<Property>
  get() = properties.filter { PropertyConstraint.UniqueKey in it.constraints }

/**
 * The values to hand to the per-property `UniqueKey` check of [property] (see `PropertyConstraintPort.checkValue`'s `existingValues`) for a [candidate]
 * object: the [property] values of those [existing] objects that equal [candidate] in every *other* key property of [keyProperties]. The per-property
 * check then reports a violation exactly when the whole composite key collides. Values are compared as given (callers pass parsed values); `null` in a
 * non-candidate key property only matches `null`. Returns an empty list for a property that is not part of the key.
 */
fun uniqueKeyComparisonValues(
  property: Property,
  keyProperties: List<Property>,
  candidate: Map<PropertyId, Any?>,
  existing: List<Map<PropertyId, Any?>>,
): List<Any?> {
  if (keyProperties.none { it.id == property.id }) return emptyList()
  val otherKeyProperties = keyProperties.filter { it.id != property.id }
  return existing
    .filter { other -> otherKeyProperties.all { other[it.id] == candidate[it.id] } }
    .mapNotNull { it[property.id] }
}
