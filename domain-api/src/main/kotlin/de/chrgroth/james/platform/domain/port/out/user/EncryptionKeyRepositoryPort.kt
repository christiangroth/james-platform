package de.chrgroth.james.platform.domain.port.out.user

interface EncryptionKeyRepositoryPort {
  fun findKey(): String?
  fun saveKey(key: String)
}
