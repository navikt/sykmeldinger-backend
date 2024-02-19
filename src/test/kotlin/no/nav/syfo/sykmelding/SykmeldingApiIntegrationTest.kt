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
import io.mockk.mockk
import no.nav.syfo.sykmelding.api.registerSykmeldingApiV2
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmeldingstatus.getSykmeldingDTO
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import no.nav.syfo.utils.objectMapper
import org.amshove.kluent.shouldBeEqualTo

class SykmeldingApiIntegrationTest :
    FunSpec({
        val sykmeldingDb = mockk<SykmeldingDb>()
        val sykmeldingService =
            SykmeldingService(
                sykmeldingDb,
            )

        // TODO KOIN!!!!!!!!!!!

        context("Sykmeldinger api integration test") {
            with(TestApplicationEngine()) {
                setUpTestApplication()
                setUpAuth()
                application.routing {
                    authenticate("tokenx") { route("/api/v2") { registerSykmeldingApiV2() } }
                }
                test("Should get list of sykmeldinger OK") {
                    val sykmelding = getSykmeldingDTO()
                    coEvery { sykmeldingDb.getSykmeldinger(any()) } returns listOf(sykmelding)
                    withGetSykmeldinger {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        response.content?.let {
                            objectMapper.readValue<List<SykmeldingDTO>>(it)
                        } shouldBeEqualTo listOf(sykmelding)
                    }
                }
                test("should get empty list of sykmeldinger OK") {
                    coEvery { sykmeldingDb.getSykmeldinger(any()) } returns emptyList()
                    withGetSykmeldinger {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        response.content?.let {
                            objectMapper.readValue<List<SykmeldingDTO>>(it)
                        } shouldBeEqualTo emptyList()
                    }
                }
            }
        }
    })

private fun TestApplicationEngine.withGetSykmeldinger(block: TestApplicationCall.() -> Unit) {
    with(
        handleRequest(HttpMethod.Get, "api/v2/sykmeldinger") { setUpAuthHeader() },
    ) {
        block()
    }
}

fun TestApplicationRequest.setUpAuthHeader() {
    addHeader(
        "Authorization",
        "Bearer ${generateJWT(
            "client",
            "clientId",
            subject = "12345678901",
            issuer = "issuer",
        )}",
    )
}
