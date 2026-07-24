package de.chrgroth.james.platform.domain.port.`in`.imports

interface ImportCleanupPort {
  fun cleanupStaleImportDocuments(): Int
}
