package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import java.net.URL
import java.util.concurrent.TimeUnit
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.getWellKnown
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.sykmeldingstatus.kafka.KafkaFactory.Companion.getSykmeldingStatusKafkaProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.sykmeldinger-backend")

@KtorExperimentalAPI
fun main() {
    val env = Environment()
    val vaultSecrets = VaultSecrets()

    val wellKnown = getWellKnown(vaultSecrets.oidcWellKnownUri)
    val jwkProvider = JwkProviderBuilder(URL(wellKnown.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val jwkProviderStsOidc = JwkProviderBuilder(URL(vaultSecrets.stsOidcWellKnownUri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val applicationState = ApplicationState()

    DefaultExports.initialize()

    val jedisPool = JedisPool(JedisPoolConfig(), env.redisHost, env.redisPort)

    val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets).envOverrides()
    val producerConfig = kafkaBaseConfig.toProducerConfig(
        "${env.applicationName}-producer", valueSerializer = StringSerializer::class
    )
    val sykmeldingStatusKafkaProducer = getSykmeldingStatusKafkaProducer(producerConfig, env)

    val applicationEngine = createApplicationEngine(
        env,
        applicationState,
        vaultSecrets,
        jwkProvider,
        wellKnown.issuer,
        sykmeldingStatusKafkaProducer,
        jwkProviderStsOidc,
        jedisPool
    )

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
    applicationState.ready = true
}
