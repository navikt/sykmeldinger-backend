package no.nav.syfo.sykmelding

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.auth.authenticate
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import io.mockk.every
import io.mockk.mockkClass
import no.nav.syfo.Environment
import no.nav.syfo.objectMapper
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.api.registerSykmeldingApi
import no.nav.syfo.sykmelding.client.SyfosmregisterSykmeldingClient
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.model.SykmeldingStatusDTO
import no.nav.syfo.sykmeldingstatus.api.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.getSykmeldingModel
import no.nav.syfo.sykmeldingstatus.getSykmeldingStatusDto
import no.nav.syfo.sykmeldingstatus.getSykmeldingStatusRedisModel
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisService
import no.nav.syfo.testutils.HttpClientTest
import no.nav.syfo.testutils.ResponseData
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.time.ZoneOffset

@KtorExperimentalAPI
class SykmeldingApiIntegrationTest : Spek({

    val httpClient = HttpClientTest()
    httpClient.responseData = ResponseData(HttpStatusCode.NotFound, "")

    val redisService = mockkClass(SykmeldingStatusRedisService::class)
    val pdlPersonService = mockkClass(PdlPersonService::class)
    val syfosmregisterSykmeldingClient = SyfosmregisterSykmeldingClient("url", httpClient.httpClient)
    val sykmeldingService = SykmeldingService(syfosmregisterSykmeldingClient, redisService, pdlPersonService)

    every { redisService.getStatus(any()) } returns null

    describe("Sykmeldinger api integration test") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()
            application.routing { authenticate("jwt") { registerSykmeldingApi(sykmeldingService) } }
            it("Should get list of sykmeldinger OK") {
                httpClient.respond(emptyList<SykmeldingDTO>())
                withGetSykmeldinger(env) {
                    response.status() shouldEqual HttpStatusCode.OK
                }
            }
            it("Should get sykmeldinger with updated status from redis") {
                val sykmeldingDTO = getSykmeldingModel(
                    getSykmeldingStatusDto(
                        StatusEventDTO.APEN,
                        OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1)
                    )
                )
                httpClient.respond(listOf(sykmeldingDTO))
                val newSykmeldingStatus = getSykmeldingStatusRedisModel(
                    StatusEventDTO.SENDT, sykmeldingDTO.sykmeldingStatus.timestamp.plusSeconds(1)
                )
                every { redisService.getStatus(any()) } returns newSykmeldingStatus

                withGetSykmeldinger(env) {
                    response.status() shouldEqual HttpStatusCode.OK
                    objectMapper.readValue<List<SykmeldingDTO>>(response.content!!) shouldEqual
                        listOf(
                            sykmeldingDTO.copy(
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

            it("Should get sykmeldinger with newest status in registeret") {
                val sykmeldingDTO = getSykmeldingModel(
                    getSykmeldingStatusDto(
                        StatusEventDTO.APEN,
                        OffsetDateTime.now(ZoneOffset.UTC)
                    )
                )
                httpClient.respond(listOf(sykmeldingDTO))
                val redisSykmeldingStatus = getSykmeldingStatusRedisModel(
                    StatusEventDTO.BEKREFTET,
                    OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)
                )
                every { redisService.getStatus(any()) } returns redisSykmeldingStatus

                withGetSykmeldinger(env) {
                    response.status() shouldEqual HttpStatusCode.OK
                    objectMapper.readValue<List<SykmeldingDTO>>(response.content!!) shouldEqual
                        listOf(sykmeldingDTO)
                }
            }
            it("Should get unauthorize when register returns unauthorized") {
                httpClient.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                withGetSykmeldinger(env) {
                    response.status() shouldEqual HttpStatusCode.Unauthorized
                    response.content shouldEqual "Unauthorized"
                }
            }
            it("Should get forbidden when register returns forbidden") {
                httpClient.respond(HttpStatusCode.Forbidden, "Forbidden")
                withGetSykmeldinger(env) {
                    response.status() shouldEqual HttpStatusCode.Forbidden
                    response.content shouldEqual "Forbidden"
                }
            }
            it("Should get 500 when register returns 500") {
                httpClient.respond(HttpStatusCode.InternalServerError, "Feil i registeret")
                withGetSykmeldinger(env) {
                    response.status() shouldEqual HttpStatusCode.InternalServerError
                    response.content shouldEqual "Feil i registeret"
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
