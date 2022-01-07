package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.prometheus.client.hotspot.DefaultExports
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.getWellKnown
import no.nav.syfo.sykmeldingstatus.kafka.KafkaFactory.Companion.getSykmeldingStatusKafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.net.URL
import java.util.concurrent.TimeUnit

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.sykmeldinger-backend")

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

fun main() {
    val env = Environment()
    val vaultSecrets = VaultSecrets()

    val wellKnown = getWellKnown(env.loginserviceIdportenDiscoveryUrl)
    val jwkProvider = JwkProviderBuilder(URL(wellKnown.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val applicationState = ApplicationState()

    DefaultExports.initialize()

    val jedisPool = JedisPool(JedisPoolConfig(), env.redisHost, env.redisPort)

    val sykmeldingStatusKafkaProducer = getSykmeldingStatusKafkaProducer(env)

    val applicationEngine = createApplicationEngine(
        env,
        applicationState,
        vaultSecrets,
        jwkProvider,
        wellKnown.issuer,
        sykmeldingStatusKafkaProducer,
        jedisPool
    )

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
    applicationState.ready = true
}
