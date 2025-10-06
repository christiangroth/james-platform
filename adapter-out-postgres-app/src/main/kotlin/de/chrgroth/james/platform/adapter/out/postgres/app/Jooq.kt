package de.chrgroth.james.platform.adapter.out.postgres.app

import io.agroal.api.AgroalDataSource
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Qualifier
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class AppDatabase

@ApplicationScoped
@Suppress("Unused")
class JooqConfiguration {

  @Inject
  lateinit var dataSource: AgroalDataSource

  @Produces
  @AppDatabase
  @ApplicationScoped
  fun dslContext(): DSLContext {
    return DSL.using(dataSource, SQLDialect.POSTGRES)
  }
}
