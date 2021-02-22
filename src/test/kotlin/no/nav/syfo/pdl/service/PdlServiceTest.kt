package no.nav.syfo.pdl.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.OidcToken
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.ErrorDetails
import no.nav.syfo.pdl.client.model.ErrorExtension
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.HentPerson
import no.nav.syfo.pdl.client.model.ResponseData
import no.nav.syfo.pdl.client.model.ResponseError
import no.nav.syfo.pdl.error.PersonNotFoundInPdl
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertFailsWith

@KtorExperimentalAPI
class PdlServiceTest : Spek({
    val pdlClient = mockkClass(PdlClient::class)
    val stsOidcClient = mockkClass(StsOidcClient::class)
    val pdlService = PdlPersonService(pdlClient, stsOidcClient)
    coEvery { stsOidcClient.oidcToken() } returns OidcToken("Token", "JWT", 1L)

    describe("PdlService") {
        it("hente person fra pdl") {
            coEvery { pdlClient.getPerson(any(), any(), any()) } returns getPdlResponse()
            runBlocking {
                val person = pdlService.getPersonnavn("01245678901", "Bearer token", "callId")
                person.navn.fornavn shouldEqual "fornavn"
                person.navn.mellomnavn shouldEqual null
                person.navn.etternavn shouldEqual "etternavn"
            }
        }

        it("Skal feile når person ikke finnes") {
            coEvery { pdlClient.getPerson(any(), any(), any()) } returns GetPersonResponse(
                ResponseData(null),
                listOf(
                    ResponseError("Ikke tilgang", null, null, ErrorExtension("unauthorized", ErrorDetails("abac-deny", "cause", "policy"), null))
                )
            )
            val exception = assertFailsWith<PersonNotFoundInPdl> {
                runBlocking {
                    pdlService.getPersonnavn("123", "Bearer token", "callId")
                }
            }
            exception.message shouldEqual "Fant ikke person i PDL"
        }

        it("Skal feile når navn er tom liste") {
            coEvery { pdlClient.getPerson(any(), any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentPerson = HentPerson(
                        navn = emptyList()
                    )
                ),
                listOf(ResponseError("melding", null, null, null))
            )
            val exception = assertFailsWith<PersonNotFoundInPdl> {
                runBlocking {
                    pdlService.getPersonnavn("123", "Bearer token", "callId")
                }
            }
            exception.message shouldEqual "Fant ikke navn på person i PDL"
        }
        it("Skal feile når navn ikke finnes") {
            coEvery { pdlClient.getPerson(any(), any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentPerson = HentPerson(
                        navn = null
                    )
                ),
                listOf(ResponseError("melding", null, null, null))
            )
            val exception = assertFailsWith<PersonNotFoundInPdl> {
                runBlocking {
                    pdlService.getPersonnavn("123", "Bearer token", "callId")
                }
            }
            exception.message shouldEqual "Fant ikke navn på person i PDL"
        }
    }
})
