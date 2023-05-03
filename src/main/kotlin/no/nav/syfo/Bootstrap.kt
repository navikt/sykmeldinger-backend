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
import no.nav.syfo.application.database.Database
import no.nav.syfo.application.getWellKnownTokenX
import no.nav.syfo.arbeidsgivere.db.ArbeidsforholdDb
import no.nav.syfo.arbeidsgivere.narmesteleder.db.NarmestelederDb
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmeldingstatus.db.SykmeldingStatusDb
import no.nav.syfo.sykmeldingstatus.kafka.KafkaFactory.Companion.getSykmeldingStatusKafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.TimeUnit

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.sykmeldinger-backend")
val securelog: Logger = LoggerFactory.getLogger("securelog")

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

fun main() {
    val env = Environment()

    val wellKnownTokenX = getWellKnownTokenX(env.tokenXWellKnownUrl)
    val jwkProviderTokenX = JwkProviderBuilder(URL(wellKnownTokenX.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val applicationState = ApplicationState()

    val database = Database(env)
        .initializeDatasource()
        .runFlywayMigrations()

    DefaultExports.initialize()

    val sykmeldingStatusKafkaProducer = getSykmeldingStatusKafkaProducer(env)
    val arbeidsforholdDb = ArbeidsforholdDb(database)
    val narmestelederDb = NarmestelederDb(database)
    val sykmeldingStatusDb = SykmeldingStatusDb(database)
    val sykmeldingDb = SykmeldingDb(database)
    val applicationEngine = createApplicationEngine(
        env = env,
        applicationState = applicationState,
        sykmeldingStatusKafkaProducer = sykmeldingStatusKafkaProducer,
        jwkProviderTokenX = jwkProviderTokenX,
        tokenXIssuer = wellKnownTokenX.issuer,
        narmestelederDb = narmestelederDb,
        sykmeldingStatusDb = sykmeldingStatusDb,
        sykmeldingDb = sykmeldingDb,
        arbeidsforholdDb = arbeidsforholdDb,
    )

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
}
