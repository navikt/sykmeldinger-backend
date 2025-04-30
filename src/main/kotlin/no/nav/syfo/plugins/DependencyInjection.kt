package no.nav.syfo.plugins

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.*
import javax.naming.ServiceUnavailableException
import no.nav.syfo.application.database.Database
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.arbeidsforhold.ArbeidsforholdService
import no.nav.syfo.arbeidsforhold.client.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.syfo.arbeidsforhold.client.organisasjon.client.OrganisasjonsinfoClient
import no.nav.syfo.arbeidsgivere.db.ArbeidsforholdDb
import no.nav.syfo.arbeidsgivere.narmesteleder.db.NarmestelederDb
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.db.SykmeldingStatusDb
import no.nav.syfo.sykmeldingstatus.kafka.KafkaFactory.Companion.getSykmeldingStatusKafkaProducer
import no.nav.syfo.utils.Environment
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureModules() {
    install(Koin) {
        slf4jLogger()

        modules(
            environmentModule,
            authModule,
            applicationStateModule,
            databaseModule,
            arbeidsgiverServiceModule,
            arbeidsgiverModule,
            sykmeldingModule,
            sykmeldingStatusModule,
        )
    }
}

val environmentModule = module { single { Environment() } }

val applicationStateModule = module { single { ApplicationState() } }

val databaseModule = module {
    single<DatabaseInterface> { Database(get()).initializeDatasource().runFlywayMigrations() }
}

val arbeidsgiverServiceModule = module {
    single {
        val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
            install(HttpRequestRetry) {
                constantDelay(100, 0, false)
                retryOnExceptionIf(3) { request, throwable -> true }
                retryIf(maxRetries) { request, response ->
                    if (response.status.value.let { it in 500..599 }) {
                        true
                    } else {
                        false
                    }
                }
            }
            install(HttpTimeout) {
                socketTimeoutMillis = 40_000
                connectTimeoutMillis = 40_000
                requestTimeoutMillis = 40_000
            }
            install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
            HttpResponseValidator {
                handleResponseExceptionWithRequest { exception, _ ->
                    when (exception) {
                        is SocketTimeoutException ->
                            throw ServiceUnavailableException(exception.message)
                    }
                }
            }
            expectSuccess = true
        }
        val httpClient = HttpClient(Apache, config)
        val env = get<Environment>()
        val accessTokenClient =
            AccessTokenClient(
                aadAccessTokenUrl = env.aadAccessTokenUrl,
                clientId = env.clientId,
                clientSecret = env.clientSecret,
                httpClient = httpClient
            )
        val arbeidsforholdClient =
            ArbeidsforholdClient(httpClient, env.aaregUrl, accessTokenClient, env.aaregScope)
        val database = get<DatabaseInterface>()
        val arbeidsforholdDb = no.nav.syfo.arbeidsforhold.db.ArbeidsforholdDb(database)
        val organisasjonsinfoClient = OrganisasjonsinfoClient(httpClient, env.eregUrl)
        ArbeidsforholdService(arbeidsforholdClient, organisasjonsinfoClient, arbeidsforholdDb)
    }
}

val authModule = module { single { getProductionAuthConfig(get()) } }

val arbeidsgiverModule = module {
    single { NarmestelederDb(get()) }
    single { ArbeidsforholdDb(get()) }
    single { ArbeidsgiverService(get(), get(), get()) }
}

val sykmeldingModule = module {
    single { SykmeldingDb(get()) }
    single { SykmeldingService(get()) }
}

val sykmeldingStatusModule = module {
    single { getSykmeldingStatusKafkaProducer(get()) }
    single { SykmeldingStatusDb(get()) }
    single { SykmeldingStatusService(get(), get(), get()) }
}
