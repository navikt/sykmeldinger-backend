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
import no.nav.syfo.arbeidsgivere.client.narmesteleder.NarmestelederClient
import no.nav.syfo.arbeidsgivere.client.organisasjon.client.OrganisasjonsinfoClient
import no.nav.syfo.arbeidsgivere.redis.ArbeidsgiverRedisService
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.brukerinformasjon.api.registrerBrukerinformasjonApi
import no.nav.syfo.client.SyfosmregisterStatusClient
import no.nav.syfo.log
import no.nav.syfo.metrics.monitorHttpRequests
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.redis.PdlPersonRedisService
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.api.registerSykmeldingApi
import no.nav.syfo.sykmelding.api.registerSykmeldingApiV2
import no.nav.syfo.sykmelding.client.SyfosmregisterSykmeldingClient
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
import no.nav.syfo.sykmeldingstatus.exception.setUpSykmeldingStatusExeptionHandler
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisService
import no.nav.syfo.tokenx.TokenXClient
import no.nav.syfo.tokenx.redis.TokenXRedisService
import org.apache.http.ConnectionClosedException
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
    tokendingsUrl: String
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

        val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
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
            install(HttpRequestRetry) {
                retryOnExceptionIf(maxRetries = 3) { _, throwable ->
                    when (throwable) {
                        is ConnectionClosedException -> {
                            log.error("Connection closed, retrying", throwable)
                            true
                        }
                        else -> {
                            log.error("Exception something else, not retrying", throwable)
                            false
                        }
                    }
                }
                constantDelay(100)
            }
            expectSuccess = true
        }
        val httpClient = HttpClient(Apache, config)

        val tokenXRedisService = TokenXRedisService(jedisPool, vaultSecrets.redisSecret)
        val tokenXClient = TokenXClient(
            tokendingsUrl = tokendingsUrl,
            tokenXClientId = env.clientIdTokenX,
            tokenXRedisService = tokenXRedisService,
            httpClient = httpClient,
            privateKey = env.tokenXPrivateJwk
        )
        val syfosmregisterClient = SyfosmregisterStatusClient(env.syfosmregisterUrl, httpClient, tokenXClient, env.syfosmregisterAudience)
        val syfosmregisterSykmeldingClient = SyfosmregisterSykmeldingClient(env.syfosmregisterUrl, httpClient, tokenXClient, env.syfosmregisterAudience)

        val smregisterSykmeldingClient = SyfosmregisterSykmeldingClient(env.smregisterUrl, httpClient, tokenXClient, env.smregisterAudience)
        val smregisterStatusClient = SyfosmregisterStatusClient(env.syfosmregisterUrl, httpClient, tokenXClient, env.smregisterAudience)

        val arbeidsforholdClient = ArbeidsforholdClient(httpClient, env.aaregUrl, tokenXClient, env.aaregAudience)
        val organisasjonsinfoClient = OrganisasjonsinfoClient(httpClient, env.eregUrl)
        val narmestelederClient = NarmestelederClient(httpClient, env.narmesteLederBasePath, tokenXClient, env.narmestelederAudience)

        val pdlClient = PdlClient(
            httpClient,
            env.pdlGraphqlPath,
            PdlClient::class.java.getResource("/graphql/getPerson.graphql").readText().replace(Regex("[\n\t]"), "")
        )

        val pdlPersonRedisService = PdlPersonRedisService(jedisPool, vaultSecrets.redisSecret)
        val pdlService = PdlPersonService(pdlClient, pdlPersonRedisService, tokenXClient, env.pdlAudience)

        val arbeidsgiverRedisService = ArbeidsgiverRedisService(jedisPool, vaultSecrets.redisSecret)
        val arbeidsgiverService = ArbeidsgiverService(arbeidsforholdClient, organisasjonsinfoClient, narmestelederClient, pdlService, arbeidsgiverRedisService)

        val sykmeldingStatusRedisService = SykmeldingStatusRedisService(jedisPool, vaultSecrets.redisSecret)
        val sykmeldingStatusService = SykmeldingStatusService(sykmeldingStatusKafkaProducer, sykmeldingStatusRedisService, syfosmregisterClient, arbeidsgiverService)
        val sykmeldingService = SykmeldingService(syfosmregisterSykmeldingClient, sykmeldingStatusRedisService, pdlService, smregisterSykmeldingClient)

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
