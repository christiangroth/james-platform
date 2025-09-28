package de.chrgroth.james.platform.adapter.out.postgres.user

import io.quarkus.test.junit.QuarkusTestProfile

/**
 * Test profile for UserPersistenceAdapter tests.
 * Configures the test environment with necessary settings.
 */
class UserPersistenceTestProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> {
        return mapOf(
            "quarkus.datasource.users.db-kind" to "h2",
            "quarkus.datasource.users.jdbc.url" to "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            "quarkus.hibernate-orm.database.generation" to "drop-and-create",
            "quarkus.flyway.migrate-at-start" to "false"
        )
    }
}
