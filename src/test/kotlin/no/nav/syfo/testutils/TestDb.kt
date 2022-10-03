package no.nav.syfo.testutils

import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.Environment
import no.nav.syfo.application.database.Database
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.log
import org.testcontainers.containers.PostgreSQLContainer

class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:12")

class TestDb {
    companion object {
        val database: DatabaseInterface

        private val psqlContainer: PsqlContainer

        init {

            try {
                psqlContainer = PsqlContainer()
                    .withExposedPorts(5432)
                    .withUsername("username")
                    .withPassword("password")
                    .withDatabaseName("database")

                psqlContainer.start()
                val mockEnv = mockk<Environment>(relaxed = true)
                every { mockEnv.databaseUsername } returns "username"
                every { mockEnv.databasePassword } returns "password"
                every { mockEnv.jdbcUrl() } returns psqlContainer.jdbcUrl
                database = Database(mockEnv)
            } catch (ex: Exception) {
                log.error("Error", ex)
                throw ex
            }
        }
    }
}
