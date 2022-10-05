package no.nav.syfo.testutils

import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.Environment
import no.nav.syfo.application.database.Database
import no.nav.syfo.application.database.DatabaseInterface
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection

class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:14")

class TestDB private constructor() {
    companion object {
        var database: DatabaseInterface
        private val psqlContainer: PsqlContainer = PsqlContainer()
            .withExposedPorts(5432)
            .withUsername("username")
            .withPassword("password")
            .withDatabaseName("database")

        init {
            psqlContainer.start()
            val mockEnv = mockk<Environment>(relaxed = true)
            every { mockEnv.databaseUsername } returns "username"
            every { mockEnv.databasePassword } returns "password"
            every { mockEnv.dbName } returns "database"
            every { mockEnv.jdbcUrl() } returns psqlContainer.jdbcUrl
            database = Database(mockEnv).initializeDatasource().runFlywayMigrations()
        }
    }
}
