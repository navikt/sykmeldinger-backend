package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
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

val objectMapper: ObjectMapper = ObjectMapper()
    .registerModule(JavaTimeModule())
    .registerKotlinModule()
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.sykmeldinger-backend")

@KtorExperimentalAPI
fun main() {
    val env = Environment()
    val vaultSecrets = objectMapper.readValue<VaultSecrets>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())

    val wellKnown = getWellKnown(vaultSecrets.oidcWellKnownUri)
    val jwkProvider = JwkProviderBuilder(URL(wellKnown.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val jwkProviderForRerun = JwkProviderBuilder(URL(env.jwkKeysUrl))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val jwkProviderStsOidc = JwkProviderBuilder(URL(vaultSecrets.stsOidcWellKnownUri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val applicationState = ApplicationState()

    DefaultExports.initialize()

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
        jwkProviderStsOidc
    )

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
    applicationState.ready = true
}

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
