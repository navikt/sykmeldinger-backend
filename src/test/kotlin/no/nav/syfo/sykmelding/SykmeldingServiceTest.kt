package no.nav.syfo.sykmelding

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkClass
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.pdl.error.PersonNotFoundInPdl
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.client.SyfosmregisterSykmeldingClient
import no.nav.syfo.sykmelding.model.SykmeldingStatusDTO
import no.nav.syfo.sykmeldingstatus.api.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.getSykmeldingModel
import no.nav.syfo.sykmeldingstatus.getSykmeldingStatusRedisModel
import no.nav.syfo.sykmeldingstatus.lagSyforestSykmelding
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisService
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
class SykmeldingServiceTest : Spek({
    val sykmeldingStatusRedisService = mockkClass(SykmeldingStatusRedisService::class)
    val syfosmregisterSykmeldingClient = mockkClass(SyfosmregisterSykmeldingClient::class)
    val pdlPersonService = mockkClass(PdlPersonService::class)

    val sykmeldingService = SykmeldingService(syfosmregisterSykmeldingClient, sykmeldingStatusRedisService, pdlPersonService)

    beforeEachTest {
        clearAllMocks()
    }

    describe("Get Sykmeldinger and latest status") {
        it("Get sykmeldinger") {
            val sykmelding = getSykmeldingModel()
            coEvery { syfosmregisterSykmeldingClient.getSykmeldinger("token", null) } returns listOf(sykmelding)
            every { sykmeldingStatusRedisService.getStatus(any()) } returns null
            runBlocking {
                val returndSykmelding = sykmeldingService.hentSykmeldinger("token", null)
                returndSykmelding shouldEqual listOf(sykmelding)
            }
        }
        it("Get sykmeldinger with newest status from redis") {
            val sykmelding = getSykmeldingModel(SykmeldingStatusDTO(
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1), statusEvent = StatusEventDTO.APEN.name, arbeidsgiver = null, sporsmalOgSvarListe = emptyList()
            ))
            val statusFromRedis = getSykmeldingStatusRedisModel(
                    StatusEventDTO.SENDT, OffsetDateTime.now(ZoneOffset.UTC)
            )
            coEvery { syfosmregisterSykmeldingClient.getSykmeldinger("token", null) } returns listOf(sykmelding)
            every { sykmeldingStatusRedisService.getStatus(any()) } returns statusFromRedis
            runBlocking {
                val returndSykmelding = sykmeldingService.hentSykmeldinger("token", null)
                returndSykmelding shouldNotEqual listOf(sykmelding)
                returndSykmelding[0].sykmeldingStatus shouldEqual SykmeldingStatusDTO(
                        timestamp = statusFromRedis.timestamp, statusEvent = statusFromRedis.statusEvent.name, arbeidsgiver = null, sporsmalOgSvarListe = emptyList()
                )
            }
        }
    }

    describe("Hent sykmeldinger med syforest-format") {
        it("Hent sykmeldinger") {
            val sykmelding = getSykmeldingModel()
            coEvery { syfosmregisterSykmeldingClient.getSykmeldinger("token", null) } returns listOf(sykmelding)
            every { sykmeldingStatusRedisService.getStatus(any()) } returns null
            coEvery { pdlPersonService.getPersonnavn(any(), "token", any()) } returns PdlPerson(Navn("Fornavn", "Mellomnavn", "Etternavn"))

            runBlocking {
                val syforestSykmeldinger = sykmeldingService.hentSykmeldingerSyforestFormat("token", "fnr", null)

                syforestSykmeldinger shouldEqual listOf(lagSyforestSykmelding())
            }
            coVerify(exactly = 1) { pdlPersonService.getPersonnavn(any(), any(), any()) }
        }

        it("Skal ikke hente person fra PDL hvis bruker ikke har sykmeldinger") {
            coEvery { syfosmregisterSykmeldingClient.getSykmeldinger("token", null) } returns emptyList()
            every { sykmeldingStatusRedisService.getStatus(any()) } returns null
            coEvery { pdlPersonService.getPersonnavn(any(), "token", any()) } returns PdlPerson(Navn("Fornavn", "Mellomnavn", "Etternavn"))

            runBlocking {
                val syforestSykmeldinger = sykmeldingService.hentSykmeldingerSyforestFormat("token", "fnr", null)

                syforestSykmeldinger shouldEqual emptyList()
            }

            coVerify(exactly = 0) { pdlPersonService.getPersonnavn(any(), any(), any()) }
        }

        it("Feiler hvis person ikke finnes i PDL") {
            val sykmelding = getSykmeldingModel()
            coEvery { syfosmregisterSykmeldingClient.getSykmeldinger("token", null) } returns listOf(sykmelding)
            every { sykmeldingStatusRedisService.getStatus(any()) } returns null
            coEvery { pdlPersonService.getPersonnavn(any(), "token", any()) } throws PersonNotFoundInPdl("Fant ikke person i PDL")

            assertFailsWith<PersonNotFoundInPdl> {
                runBlocking {
                    sykmeldingService.hentSykmeldingerSyforestFormat("token", "fnr", null)
                }
            }
        }
    }
})
