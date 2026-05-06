package de.chrgroth.james.platform.domain.port.`in`.app

import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import kotlin.time.Instant

interface ComputedPropertyPort {
  fun computeValues(entity: EntityDefinition, data: Map<String, String?>, now: Instant): Map<String, String?>
}
