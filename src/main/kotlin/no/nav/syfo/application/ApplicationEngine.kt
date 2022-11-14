package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.application.api.setupSwaggerDocApi
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.syfo.arbeidsgivere.client.organisasjon.client.OrganisasjonsinfoClient
import no.nav.syfo.arbeidsgivere.narmesteleder.db.NarmestelederDb
import no.nav.syfo.arbeidsgivere.redis.ArbeidsgiverRedisService
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.brukerinformasjon.api.registrerBrukerinformasjonApi
import no.nav.syfo.log
import no.nav.syfo.metrics.monitorHttpRequests
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.redis.PdlPersonRedisService
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.api.registerSykmeldingApi
import no.nav.syfo.sykmelding.api.registerSykmeldingApiV2
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmelding.exception.setUpSykmeldingExceptionHandler
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.api.v1.registerSykmeldingAvbrytApi
import no.nav.syfo.sykmeldingstatus.api.v1.registerSykmeldingAvbrytApiV2
import no.nav.syfo.sykmeldingstatus.api.v1.registerSykmeldingBekreftAvvistApi
import no.nav.syfo.sykmeldingstatus.api.v1.registerSykmeldingBekreftAvvistApiV2
import no.nav.syfo.sykmeldingstatus.api.v1.registerSykmeldingGjenapneApi
import no.nav.syfo.sykmeldingstatus.api.v1.registerSykmeldingGjenapneApiV2
import no.nav.syfo.sykmeldingstatus.api.v2.registrerSykmeldingSendApiV2
import no.nav.syfo.sykmeldingstatus.api.v2.registrerSykmeldingSendApiV3
import no.nav.syfo.sykmeldingstatus.api.v2.setUpSykmeldingSendApiV2ExeptionHandler
import no.nav.syfo.sykmeldingstatus.db.SykmeldingStatusDb
import no.nav.syfo.sykmeldingstatus.exception.setUpSykmeldingStatusExeptionHandler
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.tokenx.TokenXClient
import no.nav.syfo.tokenx.redis.TokenXRedisService
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
    jedisPool: JedisPool,
    jwkProviderTokenX: JwkProvider,
    tokenXIssuer: String,
    tokendingsUrl: String,
    narmestelederDb: NarmestelederDb,
    sykmeldingStatusDb: SykmeldingStatusDb,
    sykmeldingDb: SykmeldingDb
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
        setupAuth(
            loginserviceIdportenClientId = env.loginserviceIdportenAudience,
            jwkProvider = jwkProvider,
            issuer = issuer,
            jwkProviderTokenX = jwkProviderTokenX,
            tokenXIssuer = tokenXIssuer,
            clientIdTokenX = env.clientIdTokenX
        )
        install(CallId) {
            generate { UUID.randomUUID().toString() }
            verify { callId: String -> callId.isNotEmpty() }
            header(HttpHeaders.XCorrelationId)
        }
        install(StatusPages) {
            setUpSykmeldingSendApiV2ExeptionHandler()
            setUpSykmeldingStatusExeptionHandler()
            setUpSykmeldingExceptionHandler()
            exception<Throwable> { call, cause ->
                call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                log.error("Caught exception ${cause.message}", cause)
                if (cause is ExecutionException) {
                    log.error("Exception is ExecutionException, restarting", cause.cause)
                    applicationState.ready = false
                    applicationState.alive = false
                }
            }
        }
        install(CORS) {
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Options)
            env.allowedOrigin.forEach {
                hosts.add("https://$it")
            }
            allowHeader("nav_csrf_protection")
            allowCredentials = true
            allowNonSimpleContentTypes = true
        }

        val httpClient = getHttpClient()
        val tokenXRedisService = TokenXRedisService(jedisPool, vaultSecrets.redisSecret)
        val tokenXClient = TokenXClient(
            tokendingsUrl = tokendingsUrl,
            tokenXClientId = env.clientIdTokenX,
            tokenXRedisService = tokenXRedisService,
            httpClient = httpClient,
            privateKey = env.tokenXPrivateJwk
        )

        val arbeidsforholdClient = ArbeidsforholdClient(httpClient, env.aaregUrl, tokenXClient, env.aaregAudience)
        val organisasjonsinfoClient = OrganisasjonsinfoClient(httpClient, env.eregUrl)


        val pdlClient = PdlClient(
            httpClient,
            env.pdlGraphqlPath,
            PdlClient::class.java.getResource("/graphql/getPerson.graphql").readText().replace(Regex("[\n\t]"), "")
        )

        val pdlPersonRedisService = PdlPersonRedisService(jedisPool, vaultSecrets.redisSecret)
        val pdlService = PdlPersonService(pdlClient, pdlPersonRedisService, tokenXClient, env.pdlAudience)

        val arbeidsgiverRedisService = ArbeidsgiverRedisService(jedisPool, vaultSecrets.redisSecret)
        val arbeidsgiverService = ArbeidsgiverService(arbeidsforholdClient, organisasjonsinfoClient, narmestelederDb, pdlService, arbeidsgiverRedisService)

        val sykmeldingStatusService = SykmeldingStatusService(sykmeldingStatusKafkaProducer, arbeidsgiverService, sykmeldingStatusDb)
        val sykmeldingService = SykmeldingService(sykmeldingDb)

        routing {
            registerNaisApi(applicationState)
            if (env.cluster == "dev-gcp") {
                setupSwaggerDocApi()
            }
            authenticate("jwt") {
                route("/api/v1") {
                    registerSykmeldingApi(sykmeldingService)
                    registerSykmeldingBekreftAvvistApi(sykmeldingStatusService)
                    registerSykmeldingAvbrytApi(sykmeldingStatusService)
                    registerSykmeldingGjenapneApi(sykmeldingStatusService)
                    registrerBrukerinformasjonApi(arbeidsgiverService, pdlService)
                }
                route("/api/v2") {
                    registrerSykmeldingSendApiV2(sykmeldingStatusService)
                }
            }
            authenticate("tokenx") {
                route("/api/v2") {
                    registerSykmeldingApiV2(sykmeldingService)
                    registerSykmeldingBekreftAvvistApiV2(sykmeldingStatusService)
                    registerSykmeldingAvbrytApiV2(sykmeldingStatusService)
                    registerSykmeldingGjenapneApiV2(sykmeldingStatusService)
                    registrerBrukerinformasjonApi(arbeidsgiverService, pdlService)
                }
                route("/api/v3") {
                    registrerSykmeldingSendApiV3(sykmeldingStatusService)
                }
            }
        }
        intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
    }

fun getHttpClient(): HttpClient {
    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(HttpRequestRetry) {
            constantDelay(50, 0, false)
            retryOnExceptionIf(3) { _, throwable ->
                log.warn("Caught exception ${throwable.message}")
                true
            }
            retryIf(maxRetries) { _, response ->
                if (response.status.value.let { it in 500..599 }) {
                    log.warn("Retrying for statuscode ${response.status.value}")
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
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
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
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
        expectSuccess = true
    }
    return HttpClient(Apache, config)
}
