package no.nav.syfo.application.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.pool.HikariPool
import java.net.ConnectException
import java.net.SocketException
import java.sql.Connection
import java.sql.ResultSet
import no.nav.syfo.utils.Environment
import no.nav.syfo.utils.applog
import org.flywaydb.core.Flyway

class Database(
    private val env: Environment,
    private val retries: Long = 30,
    private val sleepTime: Long = 1_000
) : DatabaseInterface {
    private val logger = applog()

    private lateinit var dataSource: HikariDataSource
    override val connection: Connection
        get() = dataSource.connection

    fun initializeDatasource(): Database {
        var current = 0
        var connected = false
        var tempDatasource: HikariDataSource? = null
        while (!connected && current++ < retries) {
            logger.info("trying to connet to db current try $current")
            try {
                tempDatasource =
                    HikariDataSource(
                        HikariConfig().apply {
                            jdbcUrl = env.jdbcUrl()
                            username = env.databaseUsername
                            password = env.databasePassword
                            maximumPoolSize = 10
                            minimumIdle = 3
                            idleTimeout = 10000
                            maxLifetime = 300000
                            isAutoCommit = false
                            transactionIsolation = "TRANSACTION_READ_COMMITTED"
                            validate()
                        },
                    )
                connected = true
            } catch (ex: HikariPool.PoolInitializationException) {
                if (ex.cause?.cause is ConnectException || ex.cause?.cause is SocketException) {
                    logger.info("Could not connect to db")
                    Thread.sleep(sleepTime)
                } else {
                    throw ex
                }
            }
        }
        if (tempDatasource == null) {
            logger.error("Could not connect to DB")
            throw RuntimeException("Could not connect to DB")
        }
        dataSource = tempDatasource

        return this
    }

    fun runFlywayMigrations(): Database {
        Flyway.configure().run {
            locations("db")
            configuration(mapOf("flyway.postgresql.transactional.lock" to "false"))
            dataSource(env.jdbcUrl(), env.databaseUsername, env.databasePassword)
            load().migrate()
        }

        return this
    }
}

fun <T> ResultSet.toList(mapper: ResultSet.() -> T) =
    mutableListOf<T>().apply {
        while (next()) {
            add(mapper())
        }
    }
