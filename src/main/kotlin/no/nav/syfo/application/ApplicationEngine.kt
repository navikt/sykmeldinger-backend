package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.HttpResponseValidator
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.CallId
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.application.api.setupSwaggerDocApi
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.syfo.arbeidsgivere.client.narmesteleder.NarmestelederClient
import no.nav.syfo.arbeidsgivere.client.organisasjon.client.OrganisasjonsinfoClient
import no.nav.syfo.arbeidsgivere.redis.ArbeidsgiverRedisService
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.brukerinformasjon.api.registrerBrukerinformasjonApi
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.client.SyfosmregisterStatusClient
import no.nav.syfo.log
import no.nav.syfo.metrics.monitorHttpRequests
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.redis.PdlPersonRedisService
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.api.registerSykmeldingApi
import no.nav.syfo.sykmelding.client.SyfosmregisterSykmeldingClient
import no.nav.syfo.sykmelding.exception.setUpSykmeldingExceptionHandler
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.api.v1.registerSykmeldingAvbrytApi
import no.nav.syfo.sykmeldingstatus.api.v1.registerSykmeldingBekreftAvvistApi
import no.nav.syfo.sykmeldingstatus.api.v1.registerSykmeldingGjenapneApi
import no.nav.syfo.sykmeldingstatus.api.v2.registrerSykmeldingSendApiV2
import no.nav.syfo.sykmeldingstatus.api.v2.setUpSykmeldingSendApiV2ExeptionHandler
import no.nav.syfo.sykmeldingstatus.exception.setUpSykmeldingStatusExeptionHandler
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisService
import no.nav.syfo.sykmeldingstatus.soknadstatus.SoknadstatusService
import no.nav.syfo.sykmeldingstatus.soknadstatus.client.SyfosoknadClient
import redis.clients.jedis.JedisPool
import java.util.UUID
import java.util.concurrent.ExecutionException

fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState,
    vaultSecrets: VaultSecrets,
    jwkProvider: JwkProvider,
    issuer: String,
    sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer,
    jedisPool: JedisPool
): ApplicationEngine =
    embeddedServer(Netty, env.applicationPort) {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        setupAuth(env.loginserviceIdportenAudience, jwkProvider, issuer)
        install(CallId) {
            generate { UUID.randomUUID().toString() }
            verify { callId: String -> callId.isNotEmpty() }
            header(HttpHeaders.XCorrelationId)
        }
        install(StatusPages) {
            setUpSykmeldingSendApiV2ExeptionHandler()
            setUpSykmeldingStatusExeptionHandler()
            setUpSykmeldingExceptionHandler()
            exception<Throwable> { cause ->
                call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                log.error("Caught exception ${cause.message}", cause)
                if (cause is ExecutionException) {
                    log.error("Exception is ExecutionException, restarting", cause.cause)
                    applicationState.ready = false
                    applicationState.alive = false
                }
            }
        }

        val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
            install(JsonFeature) {
                serializer = JacksonSerializer {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
            HttpResponseValidator {
                handleResponseException { exception ->
                    when (exception) {
                        is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                    }
                }
            }
        }
        val httpClient = HttpClient(Apache, config)

        val stsOidcClient = StsOidcClient(
            username = vaultSecrets.serviceuserUsername,
            password = vaultSecrets.serviceuserPassword,
            stsUrl = env.stsUrl
        )
        val syfosmregisterClient = SyfosmregisterStatusClient(env.syfosmregisterUrl, httpClient)
        val syfosmregisterSykmeldingClient = SyfosmregisterSykmeldingClient(env.syfosmregisterUrl, httpClient)
        val syfosoknadClient = SyfosoknadClient(env.syfosoknadUrl, httpClient)
        val soknadstatusService = SoknadstatusService(syfosoknadClient)
        val arbeidsforholdClient = ArbeidsforholdClient(httpClient, env.registerBasePath)
        val organisasjonsinfoClient = OrganisasjonsinfoClient(httpClient, env.registerBasePath)
        val narmestelederClient = NarmestelederClient(httpClient, env.narmesteLederBasePath)

        val pdlClient = PdlClient(
            httpClient,
            env.pdlGraphqlPath,
            PdlClient::class.java.getResource("/graphql/getPerson.graphql").readText().replace(Regex("[\n\t]"), "")
        )

        val pdlPersonRedisService = PdlPersonRedisService(jedisPool, vaultSecrets.redisSecret)
        val pdlService = PdlPersonService(pdlClient, stsOidcClient, pdlPersonRedisService)

        val arbeidsgiverRedisService = ArbeidsgiverRedisService(jedisPool, vaultSecrets.redisSecret)
        val arbeidsgiverService = ArbeidsgiverService(arbeidsforholdClient, organisasjonsinfoClient, narmestelederClient, pdlService, stsOidcClient, arbeidsgiverRedisService)

        val sykmeldingStatusRedisService = SykmeldingStatusRedisService(jedisPool, vaultSecrets.redisSecret)
        val sykmeldingStatusService = SykmeldingStatusService(sykmeldingStatusKafkaProducer, sykmeldingStatusRedisService, syfosmregisterClient, soknadstatusService, arbeidsgiverService)
        val sykmeldingService = SykmeldingService(syfosmregisterSykmeldingClient, sykmeldingStatusRedisService, pdlService)
        routing {
            registerNaisApi(applicationState)
            if (env.cluster == "dev-fss") {
                setupSwaggerDocApi()
            }
            authenticate("jwt") {
                registerSykmeldingApi(sykmeldingService)
                registrerSykmeldingSendApiV2(sykmeldingStatusService)
                registerSykmeldingBekreftAvvistApi(sykmeldingStatusService)
                registerSykmeldingAvbrytApi(sykmeldingStatusService)
                registerSykmeldingGjenapneApi(sykmeldingStatusService)
                registrerBrukerinformasjonApi(arbeidsgiverService, pdlService, stsOidcClient)
            }
        }
        intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
    }
