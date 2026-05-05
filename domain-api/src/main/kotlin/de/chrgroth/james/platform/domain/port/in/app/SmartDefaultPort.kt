package de.chrgroth.james.platform.domain.port.`in`.app

import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import kotlin.time.Instant

interface SmartDefaultPort {
  fun computeSmartDefaults(entity: EntityDefinition, now: Instant): Map<String, String?>
}
