package de.chrgroth.james.platform.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import de.chrgroth.james.platform.domain.port.out.user.EncryptionKeyRepositoryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class EncryptionKeyRepositoryAdapter(
  private val encryptionKeyDocumentRepository: EncryptionKeyDocumentRepository,
) : EncryptionKeyRepositoryPort {

  override fun findKey(): String? = encryptionKeyDocumentRepository.findById(KEY_ID)?.key

  override fun saveKey(key: String) {
    val doc = EncryptionKeyDocument().also {
      it.id = KEY_ID
      it.key = key
    }
    encryptionKeyDocumentRepository.mongoCollection().replaceOne(
      Filters.eq(ID_FIELD, KEY_ID),
      doc,
      ReplaceOptions().upsert(true),
    )
  }

  companion object {
    internal const val KEY_ID = "token-encryption"
    internal const val ID_FIELD = "_id"
  }
}
