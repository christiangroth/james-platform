package de.chrgroth.james.platform.adapter.out.postgres.app

import de.chrgroth.james.expectSuccess
import de.chrgroth.james.platform.domain.app.App
import de.chrgroth.james.platform.domain.app.AppId
import de.chrgroth.james.platform.domain.app.port.out.AppPersistencePort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@QuarkusTest
class AppPersistenceAdapterTests {

  @Inject
  private lateinit var persistence: AppPersistencePort

  @Test
  @Transactional
  fun `ensure entity lifecycle`() {
    persistence.all().expectSuccess().let {
      assertThat(it).hasSize(0)
    }

    val initial = createTestEntity()
    persistence.create(initial).expectSuccess()

    persistence.all().expectSuccess().let {
      assertThat(it).hasSize(1)
      assertThat(it).contains(initial)
    }
    persistence.byId(initial.id).expectSuccess().let {
      assertThat(it).isEqualTo(initial)
    }
  }

  @Test
  @Transactional
  fun `find non existing`() {
    persistence.byId(AppId()).expectSuccess().let {
      assertThat(it).isNull()
    }
  }

  private fun createTestEntity() = App(
    id = AppId()
  )
}
