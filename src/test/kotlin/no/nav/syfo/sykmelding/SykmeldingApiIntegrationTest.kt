package no.nav.syfo.sykmelding

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import no.nav.syfo.Environment
import no.nav.syfo.arbeidsgivere.service.getPdlPerson
import no.nav.syfo.client.TokenXClient
import no.nav.syfo.objectMapper
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.api.registerSykmeldingApi
import no.nav.syfo.sykmelding.client.SyfosmregisterSykmeldingClient
import no.nav.syfo.sykmelding.model.Sykmelding
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.model.SykmeldingStatusDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.getSykmeldingDTO
import no.nav.syfo.sykmeldingstatus.getSykmeldingModel
import no.nav.syfo.sykmeldingstatus.getSykmeldingStatusRedisModel
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisService
import no.nav.syfo.testutils.HttpClientTest
import no.nav.syfo.testutils.ResponseData
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SykmeldingApiIntegrationTest : FunSpec({

    val httpClient = HttpClientTest()
    httpClient.responseData = ResponseData(HttpStatusCode.NotFound, "")

    val redisService = mockkClass(SykmeldingStatusRedisService::class)
    val pdlPersonService = mockkClass(PdlPersonService::class)
    val tokenXClient = mockk<TokenXClient>()
    val syfosmregisterSykmeldingClient = SyfosmregisterSykmeldingClient("url", httpClient.httpClient, tokenXClient, "audience")
    val sykmeldingService = SykmeldingService(syfosmregisterSykmeldingClient, redisService, pdlPersonService)

    every { redisService.getStatus(any()) } returns null
    coEvery { pdlPersonService.getPerson(any(), any(), any()) } returns getPdlPerson()

    context("Sykmeldinger api integration test") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()
            application.routing {
                authenticate("jwt") {
                    route("/api/v1") {
                        registerSykmeldingApi(sykmeldingService)
                    }
                }
            }
            test("Should get list of sykmeldinger OK") {
                httpClient.respond(emptyList<SykmeldingDTO>())
                withGetSykmeldinger(env) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                }
            }
            test("Should get sykmeldinger with updated status from redis") {
                val sykmeldingWithPasientInfoDTO = getSykmeldingDTO()
                httpClient.respond(listOf(sykmeldingWithPasientInfoDTO))
                val newSykmeldingStatus = getSykmeldingStatusRedisModel(
                    StatusEventDTO.SENDT, sykmeldingWithPasientInfoDTO.sykmeldingStatus.timestamp.plusSeconds(1)
                )
                every { redisService.getStatus(any()) } returns newSykmeldingStatus

                withGetSykmeldinger(env) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    objectMapper.readValue<List<SykmeldingDTO>>(response.content!!) shouldBeEqualTo
                        listOf(
                            sykmeldingWithPasientInfoDTO.copy(
                                sykmeldingStatus = SykmeldingStatusDTO(
                                    timestamp = newSykmeldingStatus.timestamp,
                                    statusEvent = newSykmeldingStatus.statusEvent.name,
                                    arbeidsgiver = null,
                                    sporsmalOgSvarListe = emptyList()
                                )
                            )
                        )
                }
            }

            test("Should get sykmeldinger with newest status in registeret") {
                val sykmeldingDTO = getSykmeldingModel()
                httpClient.respond(listOf(sykmeldingDTO))
                val redisSykmeldingStatus = getSykmeldingStatusRedisModel(
                    StatusEventDTO.BEKREFTET,
                    OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)
                )
                every { redisService.getStatus(any()) } returns redisSykmeldingStatus

                withGetSykmeldinger(env) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    objectMapper.readValue<List<Sykmelding>>(response.content!!) shouldBeEqualTo
                        listOf(sykmeldingDTO)
                }
            }
            test("Should get unauthorize when register returns unauthorized") {
                httpClient.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                withGetSykmeldinger(env) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                    response.content shouldBeEqualTo "Unauthorized"
                }
            }
            test("Should get forbidden when register returns forbidden") {
                httpClient.respond(HttpStatusCode.Forbidden, "Forbidden")
                withGetSykmeldinger(env) {
                    response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                    response.content shouldBeEqualTo "Forbidden"
                }
            }
            test("Should get 500 when register returns 500") {
                httpClient.respond(HttpStatusCode.InternalServerError, "Feil i registeret")
                withGetSykmeldinger(env) {
                    response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.content shouldBeEqualTo "Feil i registeret"
                }
            }
        }
    }
})

private fun TestApplicationEngine.withGetSykmeldinger(env: Environment, block: TestApplicationCall.() -> Unit) {
    with(
        handleRequest(HttpMethod.Get, "api/v1/sykmeldinger") {
            setUpAuthHeader(env)
        }
    ) {
        block()
    }
}

fun TestApplicationRequest.setUpAuthHeader(env: Environment) {
    addHeader(
        "Authorization",
        "Bearer ${generateJWT(
            "client",
            "loginserviceId1",
            subject = "12345678901",
            issuer = env.jwtIssuer
        )}"
    )
}
