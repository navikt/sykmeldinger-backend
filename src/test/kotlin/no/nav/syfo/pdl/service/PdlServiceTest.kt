package no.nav.syfo.pdl.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.TokenXClient
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.Adressebeskyttelse
import no.nav.syfo.pdl.client.model.ErrorDetails
import no.nav.syfo.pdl.client.model.ErrorExtension
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.Gradering
import no.nav.syfo.pdl.client.model.Ident
import no.nav.syfo.pdl.client.model.IdentResponse
import no.nav.syfo.pdl.client.model.Navn
import no.nav.syfo.pdl.client.model.PersonResponse
import no.nav.syfo.pdl.client.model.ResponseData
import no.nav.syfo.pdl.client.model.ResponseError
import no.nav.syfo.pdl.error.PersonNotFoundInPdl
import no.nav.syfo.pdl.redis.NavnRedisModel
import no.nav.syfo.pdl.redis.PdlPersonRedisModel
import no.nav.syfo.pdl.redis.PdlPersonRedisService
import org.amshove.kluent.shouldBeEqualTo
import kotlin.test.assertFailsWith

class PdlServiceTest : FunSpec({
    val pdlClient = mockkClass(PdlClient::class)
    val tokenXClient = mockk<TokenXClient>()
    val pdlPersonRedisService = mockkClass(PdlPersonRedisService::class, relaxed = true)
    val pdlService = PdlPersonService(pdlClient, pdlPersonRedisService, tokenXClient, "audience")

    coEvery { tokenXClient.getAccessToken(any(), any()) } returns "token"

    beforeTest {
        clearMocks(pdlClient, pdlPersonRedisService)
        coEvery { pdlPersonRedisService.getPerson(any()) } returns null
    }

    context("PdlService") {
        test("hente person fra pdl") {
            coEvery { pdlClient.getPersonTokenX(any(), any()) } returns getPdlResponse()

            val person = pdlService.getPerson("01245678901", "Bearer token", "callId")
            person.navn.fornavn shouldBeEqualTo "fornavn"
            person.navn.mellomnavn shouldBeEqualTo null
            person.navn.etternavn shouldBeEqualTo "etternavn"
            person.aktorId shouldBeEqualTo "aktorId"
            person.diskresjonskode shouldBeEqualTo false

            coVerify { pdlPersonRedisService.updatePerson(any(), any()) }
        }
        test("skal ha diskresjonskode hvis gradering er strengt fortrolig") {
            coEvery { pdlClient.getPersonTokenX(any(), any()) } returns getPdlResponse(Gradering.STRENGT_FORTROLIG)

            val person = pdlService.getPerson("01245678901", "Bearer token", "callId")
            person.diskresjonskode shouldBeEqualTo true
        }
        test("skal ha diskresjonskode hvis gradering er strengt fortrolig utland") {
            coEvery { pdlClient.getPersonTokenX(any(), any()) } returns getPdlResponse(Gradering.STRENGT_FORTROLIG_UTLAND)

            val person = pdlService.getPerson("01245678901", "Bearer token", "callId")
            person.diskresjonskode shouldBeEqualTo true
        }
        test("feiler ikke hvis diskresjonskode mangler") {
            coEvery { pdlClient.getPersonTokenX(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    person = PersonResponse(listOf(Navn("fornavn", null, "etternavn")), adressebeskyttelse = null),
                    identer = IdentResponse(listOf(Ident("aktorId", AKTORID_GRUPPE)))
                ),
                errors = null
            )

            val person = pdlService.getPerson("01245678901", "Bearer token", "callId")
            person.diskresjonskode shouldBeEqualTo false
        }
        test("hente person fra redis") {
            coEvery { pdlPersonRedisService.getPerson(any()) } returns PdlPersonRedisModel(NavnRedisModel("fornavn", null, "etternavn"), "aktørid", false)

            val person = pdlService.getPerson("01245678901", "Bearer token", "callId")
            person.aktorId shouldBeEqualTo "aktørid"

            coVerify(exactly = 0) { pdlClient.getPersonTokenX(any(), any()) }
            coVerify(exactly = 0) { pdlPersonRedisService.updatePerson(any(), any()) }
        }

        test("Skal feile når person ikke finnes") {
            coEvery { pdlClient.getPersonTokenX(any(), any()) } returns GetPersonResponse(
                ResponseData(null, null),
                listOf(
                    ResponseError(
                        "Ikke tilgang",
                        null,
                        null,
                        ErrorExtension("unauthorized", ErrorDetails("abac-deny", "cause", "policy"), null)
                    )
                )
            )
            val exception = assertFailsWith<PersonNotFoundInPdl> {
                runBlocking {
                    pdlService.getPerson("123", "Bearer token", "callId")
                }
            }
            exception.message shouldBeEqualTo "Fant ikke person i PDL"
        }

        test("Skal feile når navn er tom liste") {
            coEvery { pdlClient.getPersonTokenX(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    person = PersonResponse(
                        navn = emptyList(),
                        adressebeskyttelse = listOf(Adressebeskyttelse(Gradering.UGRADERT))
                    ),
                    identer = IdentResponse(listOf(Ident("aktorId", AKTORID_GRUPPE)))
                ),
                listOf(ResponseError("melding", null, null, null))
            )
            val exception = assertFailsWith<PersonNotFoundInPdl> {
                runBlocking {
                    pdlService.getPerson("123", "Bearer token", "callId")
                }
            }
            exception.message shouldBeEqualTo "Fant ikke navn på person i PDL"
        }
        test("Skal feile når navn ikke finnes") {
            coEvery { pdlClient.getPersonTokenX(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    person = PersonResponse(
                        navn = null,
                        adressebeskyttelse = listOf(Adressebeskyttelse(Gradering.UGRADERT))
                    ),
                    identer = IdentResponse(listOf(Ident("aktorId", AKTORID_GRUPPE)))
                ),
                listOf(ResponseError("melding", null, null, null))
            )
            val exception = assertFailsWith<PersonNotFoundInPdl> {
                runBlocking {
                    pdlService.getPerson("123", "Bearer token", "callId")
                }
            }
            exception.message shouldBeEqualTo "Fant ikke navn på person i PDL"
        }
        test("Skal feile når aktørid ikke finnes") {
            coEvery { pdlClient.getPersonTokenX(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    person = PersonResponse(
                        navn = listOf(Navn("fornavn", null, "etternavn")),
                        adressebeskyttelse = listOf(
                            Adressebeskyttelse(Gradering.UGRADERT)
                        )
                    ),
                    identer = IdentResponse(listOf(Ident("fnr", "FOLKEREGISTERIDENT")))
                ),
                listOf(ResponseError("melding", null, null, null))
            )
            val exception = assertFailsWith<PersonNotFoundInPdl> {
                runBlocking {
                    pdlService.getPerson("123", "Bearer token", "callId")
                }
            }
            exception.message shouldBeEqualTo "Fant ikke aktør i PDL"
        }
    }
})
