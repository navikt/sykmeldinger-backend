package no.nav.syfo.arbeidsgivere.api

import com.auth0.jwt.interfaces.Payload
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
class ArbeidsgiverAPIKtTest : Spek({
    val arbeidsgiverService = mockkClass(ArbeidsgiverService::class)

    val mockPayload = mockk<Payload>()

    every { mockPayload.subject } returns "12345678901"

    describe("test arbeidsgiver api") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            application.routing {
                registrerArbeidsgiverApi(arbeidsgiverService)
            }

            it("should get list of arbeidsgivere") {
                coEvery { arbeidsgiverService.getArbeidsgivere(any(), any(), any()) } returns listOf(Arbeidsgiverinfo(orgnummer = "orgnummer", juridiskOrgnummer = "juridiskOrgnummer", navn = "", stillingsprosent = "50.0", stilling = "", aktivtArbeidsforhold = true, naermesteLeder = null))
                with(
                    handleRequest(HttpMethod.Get, "api/v1/syforest/arbeidsforhold") {
                        addHeader("Authorization", "Bearer token")
                        call.authentication.principal = JWTPrincipal(mockPayload)
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                }
            }
            it("Should get error if error happens") {
                coEvery { arbeidsgiverService.getArbeidsgivere(any(), any(), any()) } throws RuntimeException("Bad thing")
                with(
                    handleRequest(HttpMethod.Get, "api/v1/syforest/arbeidsforhold") {
                        addHeader("Authorization", "Bearer token")
                        call.authentication.principal = JWTPrincipal(mockPayload)
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                }
            }
        }
    }
})
