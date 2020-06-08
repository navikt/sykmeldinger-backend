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
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.CallId
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import java.util.UUID
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.client.SyfosmregisterStatusClient
import no.nav.syfo.log
import no.nav.syfo.metrics.monitorHttpRequests
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.api.registerSykmeldingApi
import no.nav.syfo.sykmelding.client.SyfosmregisterSykmeldingClient
import no.nav.syfo.sykmelding.exception.setUpSykmeldingExceptionHandler
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.api.registerSykmeldingBekreftApi
import no.nav.syfo.sykmeldingstatus.api.registerSykmeldingBekreftSyfoServiceApi
import no.nav.syfo.sykmeldingstatus.api.registerSykmeldingSendSyfoServiceApi
import no.nav.syfo.sykmeldingstatus.api.registerSykmeldingStatusSyfoServiceApi
import no.nav.syfo.sykmeldingstatus.exception.setUpSykmeldingStatusExeptionHandler
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisService
import redis.clients.jedis.JedisPool

@KtorExperimentalAPI
fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState,
    vaultSecrets: VaultSecrets,
    jwkProvider: JwkProvider,
    issuer: String,
    sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer,
    jwkProviderStsOidc: JwkProvider,
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
        setupAuth(vaultSecrets, jwkProvider, issuer, env, jwkProviderStsOidc)
        install(CallId) {
            generate { UUID.randomUUID().toString() }
            verify { callId: String -> callId.isNotEmpty() }
            header(HttpHeaders.XCorrelationId)
        }
        install(StatusPages) {
            setUpSykmeldingStatusExeptionHandler()
            setUpSykmeldingExceptionHandler()
            exception<Throwable> { cause ->
                call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                log.error("Caught exception", cause)
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
            expectSuccess = false
        }
        val httpClient = HttpClient(Apache, config)
        val stsOidcClient = StsOidcClient(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword)

        val syfosmregisterClient = SyfosmregisterStatusClient(env.syfosmregisterUrl, httpClient)
        val syfosmregisterSykmeldingClient = SyfosmregisterSykmeldingClient(env.syfosmregisterUrl, httpClient)

        val pdlClient = PdlClient(httpClient,
            env.pdlGraphqlPath,
            PdlClient::class.java.getResource("/graphql/getPerson.graphql").readText().replace(Regex("[\n\t]"), ""))

        val pdlService = PdlPersonService(pdlClient, stsOidcClient)

        val sykmeldingStatusRedisService = SykmeldingStatusRedisService(jedisPool, vaultSecrets.redisSecret)
        val sykmeldingStatusService = SykmeldingStatusService(sykmeldingStatusKafkaProducer, sykmeldingStatusRedisService, syfosmregisterClient)
        val sykmeldingService = SykmeldingService(syfosmregisterSykmeldingClient, sykmeldingStatusRedisService, pdlService)
        routing {
            registerNaisApi(applicationState)
            authenticate("jwt") {
                registerSykmeldingApi(sykmeldingService)
                registerSykmeldingBekreftApi(sykmeldingStatusService)
            }
            authenticate("oidc") {
                registerSykmeldingStatusSyfoServiceApi(sykmeldingStatusService)
                registerSykmeldingSendSyfoServiceApi(sykmeldingStatusService)
                registerSykmeldingBekreftSyfoServiceApi(sykmeldingStatusService)
            }
        }
        intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
    }
