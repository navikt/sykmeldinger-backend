package no.nav.syfo.sykmeldingstatus

import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockkClass
import io.mockk.verify
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.RuntimeException
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.SyfosmregisterStatusClient
import no.nav.syfo.sykmeldingstatus.api.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.opprettSykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.opprettSykmeldingSendEventDTO
import no.nav.syfo.sykmeldingstatus.exception.InvalidSykmeldingStatusException
import no.nav.syfo.sykmeldingstatus.exception.SykmeldingStatusNotFoundException
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisService
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SykmeldingStatusServiceSpek : Spek({
    val sykmeldingId = "id"
    val fnr = "fnr"
    val token = "token"
    val sykmeldingStatusKafkaProducer = mockkClass(SykmeldingStatusKafkaProducer::class)
    val sykmeldingStatusJedisService = mockkClass(SykmeldingStatusRedisService::class)
    val syfosmregisterClient = mockkClass(SyfosmregisterStatusClient::class)
    val sykmeldingStatusService = SykmeldingStatusService(sykmeldingStatusKafkaProducer, sykmeldingStatusJedisService, syfosmregisterClient)

    fun checkStatusFails(newStatus: StatusEventDTO, oldStatus: StatusEventDTO) {
        runBlocking {
            coEvery { syfosmregisterClient.hentSykmeldingstatus(any(), any()) } returns getSykmeldingStatus(oldStatus)
            val expextedErrorMessage = "Kan ikke endre status fra $oldStatus til $newStatus for sykmeldingID $sykmeldingId"
            val error = assertFailsWith<InvalidSykmeldingStatusException> {
                when (newStatus) {
                    StatusEventDTO.SENDT -> sykmeldingStatusService.registrerSendt(opprettSykmeldingSendEventDTO(), sykmeldingId, "user", fnr, token)
                    StatusEventDTO.BEKREFTET -> sykmeldingStatusService.registrerBekreftet(opprettSykmeldingBekreftEventDTO(), sykmeldingId, "user", fnr, token)
                    else -> sykmeldingStatusService.registrerStatus(getSykmeldingStatus(newStatus), sykmeldingId, "user", fnr, token)
                }
            }
            error.message shouldEqual expextedErrorMessage
        }
    }
    fun checkStatusOk(newStatus: StatusEventDTO, oldStatus: StatusEventDTO) {
        runBlocking {
            coEvery { syfosmregisterClient.hentSykmeldingstatus(any(), any()) } returns getSykmeldingStatus(oldStatus)
            when (newStatus) {
                StatusEventDTO.SENDT -> sykmeldingStatusService.registrerSendt(opprettSykmeldingSendEventDTO(), sykmeldingId, "user", fnr, token)
                StatusEventDTO.BEKREFTET -> sykmeldingStatusService.registrerBekreftet(opprettSykmeldingBekreftEventDTO(), sykmeldingId, "user", fnr, token)
                else -> sykmeldingStatusService.registrerStatus(getSykmeldingStatus(newStatus), sykmeldingId, "user", fnr, token)
            }

            verify(exactly = 1) { sykmeldingStatusKafkaProducer.send(any(), any(), any()) }
            verify(exactly = 1) { sykmeldingStatusJedisService.updateStatus(any(), any()) }
        }
    }

    beforeEachTest {
        clearAllMocks()
        every { sykmeldingStatusKafkaProducer.send(any(), any(), any()) } just Runs
        every { sykmeldingStatusJedisService.updateStatus(any(), any()) } just Runs
        every { sykmeldingStatusJedisService.getStatus(any()) } returns null
        coEvery { syfosmregisterClient.hentSykmeldingstatus(any(), any()) } returns SykmeldingStatusEventDTO(StatusEventDTO.APEN, OffsetDateTime.now(ZoneOffset.UTC).minusHours(1))
    }

    describe("Hent nyeste status") {
        it("Skal hente sendt status fra Redis") {
            runBlocking {
                val redisSykmeldingSendEventDTO = getSykmeldingStatusRedisModel(StatusEventDTO.SENDT, OffsetDateTime.now(ZoneOffset.UTC))
                coEvery { sykmeldingStatusJedisService.getStatus(any()) } returns redisSykmeldingSendEventDTO
                coEvery { syfosmregisterClient.hentSykmeldingstatus(any(), any()) } returns getSykmeldingStatus(StatusEventDTO.APEN, redisSykmeldingSendEventDTO.timestamp.minusNanos(1))
                val sisteStatusEventDTO = sykmeldingStatusService.hentSisteStatusOgSjekkTilgang(sykmeldingId, token)
                sisteStatusEventDTO shouldEqual SykmeldingStatusEventDTO(StatusEventDTO.SENDT, redisSykmeldingSendEventDTO.timestamp)
            }
        }

        it("Skal hente nyeste status fra registeret") {
            runBlocking {
                val redisSykmeldingStatus = getSykmeldingStatusRedisModel(StatusEventDTO.APEN, OffsetDateTime.now(ZoneOffset.UTC))
                coEvery { sykmeldingStatusJedisService.getStatus(any()) } returns redisSykmeldingStatus
                coEvery { syfosmregisterClient.hentSykmeldingstatus(any(), any()) } returns getSykmeldingStatus(StatusEventDTO.SENDT, redisSykmeldingStatus.timestamp.plusNanos(1))
                val sisteStatus = sykmeldingStatusService.hentSisteStatusOgSjekkTilgang(sykmeldingId, token)
                sisteStatus shouldEqual SykmeldingStatusEventDTO(StatusEventDTO.SENDT, redisSykmeldingStatus.timestamp.plusNanos(1))
            }
        }

        it("Ikke tilgang til sykmeldingstatus") {
            runBlocking {
                coEvery { syfosmregisterClient.hentSykmeldingstatus(any(), any()) } throws RuntimeException("Ingen tilgang")
                val exception = assertFailsWith<SykmeldingStatusNotFoundException> {
                    sykmeldingStatusService.hentSisteStatusOgSjekkTilgang(sykmeldingId, token)
                }
                exception.message shouldEqual "Fant ikke sykmeldingstatus for sykmelding id $sykmeldingId"
            }
        }
    }

    describe("Spesialhåndtering for syfoservice") {
        it("Skal ikke sjekke status eller tilgang når source er syfoservice ved sending") {
            runBlocking {
                sykmeldingStatusService.registrerSendt(opprettSykmeldingSendEventDTO(), sykmeldingId, "syfoservice", fnr, token)

                coVerify(exactly = 0) { syfosmregisterClient.hentSykmeldingstatus(any(), any()) }
                verify(exactly = 0) { sykmeldingStatusJedisService.getStatus(any()) }
                verify(exactly = 0) { sykmeldingStatusJedisService.updateStatus(any(), any()) }
                verify(exactly = 1) { sykmeldingStatusKafkaProducer.send(any(), any(), any()) }
            }
        }
        it("Skal ikke sjekke status eller tilgang når source er syfoservice ved bekrefting") {
            runBlocking {
                sykmeldingStatusService.registrerBekreftet(opprettSykmeldingBekreftEventDTO(), sykmeldingId, "syfoservice", fnr, token)

                coVerify(exactly = 0) { syfosmregisterClient.hentSykmeldingstatus(any(), any()) }
                verify(exactly = 0) { sykmeldingStatusJedisService.getStatus(any()) }
                verify(exactly = 0) { sykmeldingStatusJedisService.updateStatus(any(), any()) }
                verify(exactly = 1) { sykmeldingStatusKafkaProducer.send(any(), any(), any()) }
            }
        }
        it("Skal ikke sjekke status eller tilgang når source er syfoservice ved statusendring") {
            runBlocking {
                sykmeldingStatusService.registrerStatus(SykmeldingStatusEventDTO(StatusEventDTO.AVBRUTT, OffsetDateTime.now(ZoneOffset.UTC)), sykmeldingId, "syfoservice", fnr, token)

                coVerify(exactly = 0) { syfosmregisterClient.hentSykmeldingstatus(any(), any()) }
                verify(exactly = 0) { sykmeldingStatusJedisService.getStatus(any()) }
                verify(exactly = 0) { sykmeldingStatusJedisService.updateStatus(any(), any()) }
                verify(exactly = 1) { sykmeldingStatusKafkaProducer.send(any(), any(), any()) }
            }
        }
    }

    describe("Test av BEKREFT for sluttbruker") {
        it("Happy-case") {
            runBlocking {
                sykmeldingStatusService.registrerBekreftet(SykmeldingBekreftEventDTO(OffsetDateTime.now(ZoneOffset.UTC), null), sykmeldingId, "user", fnr, token)

                coVerify { syfosmregisterClient.hentSykmeldingstatus(any(), any()) }
                verify { sykmeldingStatusJedisService.getStatus(any()) }
                verify { sykmeldingStatusJedisService.updateStatus(any(), any()) }
                verify { sykmeldingStatusKafkaProducer.send(any(), any(), any()) }
            }
        }
        it("Oppdaterer ikke status hvis bruker ikke har tilgang til sykmelding") {
            coEvery { syfosmregisterClient.hentSykmeldingstatus(any(), any()) } throws RuntimeException("Ingen tilgang")

            runBlocking {
                assertFailsWith<SykmeldingStatusNotFoundException> {
                    sykmeldingStatusService.registrerBekreftet(SykmeldingBekreftEventDTO(OffsetDateTime.now(ZoneOffset.UTC), null), sykmeldingId, "user", fnr, token)
                }

                coVerify { syfosmregisterClient.hentSykmeldingstatus(any(), any()) }
                verify(exactly = 0) { sykmeldingStatusJedisService.getStatus(any()) }
                verify(exactly = 0) { sykmeldingStatusJedisService.updateStatus(any(), any()) }
                verify(exactly = 0) { sykmeldingStatusKafkaProducer.send(any(), any(), any()) }
            }
        }
    }

    describe("Test SENDT status") {
        it("Skal kunne sende sykmelding med status APEN") {
            checkStatusOk(newStatus = StatusEventDTO.SENDT, oldStatus = StatusEventDTO.APEN)
        }
        it("Skal ikke kunne SENDE en allerede SENDT Sykmelding") {
            checkStatusFails(newStatus = StatusEventDTO.SENDT, oldStatus = StatusEventDTO.SENDT)
        }
        it("Skal ikke kunne SENDE en BEKREFTET sykmelding") {
            checkStatusFails(newStatus = StatusEventDTO.SENDT, oldStatus = StatusEventDTO.BEKREFTET)
        }
        it("skal ikke kunne SENDE en UTGÅTT sykmelding") {
            checkStatusFails(newStatus = StatusEventDTO.SENDT, oldStatus = StatusEventDTO.UTGATT)
        }
        it("SKal ikke kunne SENDE en AVBRUTT sykmelding") {
            checkStatusFails(newStatus = StatusEventDTO.SENDT, oldStatus = StatusEventDTO.AVBRUTT)
        }
    }

    describe("Test BEKREFT status") {
        it("Bruker skal få BEKREFTET sykmelding med status APEN") {
            checkStatusOk(StatusEventDTO.BEKREFTET, StatusEventDTO.APEN)
        }
        it("Bruker skal få BEKREFTET en sykmelding med status BEKREFTET") {
            checkStatusOk(StatusEventDTO.BEKREFTET, StatusEventDTO.BEKREFTET)
        }

        it("Bruker skal ikke få bekrefte sin egen sykmelding med status AVBRUTT") {
            checkStatusFails(newStatus = StatusEventDTO.BEKREFTET, oldStatus = StatusEventDTO.AVBRUTT)
        }

        it("Skal ikke kunne BEKREFTE når siste status er SENDT") {
            checkStatusFails(newStatus = StatusEventDTO.BEKREFTET, oldStatus = StatusEventDTO.SENDT)
        }

        it("Skal ikke kunne bekrefte når siste status er UTGATT") {
            checkStatusFails(newStatus = StatusEventDTO.BEKREFTET, oldStatus = StatusEventDTO.UTGATT)
        }
    }

    describe("Test APEN status") {
        it("Bruker skal kunne APNE en sykmelding med statsu BEKREFTET") {
            checkStatusOk(StatusEventDTO.APEN, StatusEventDTO.BEKREFTET)
        }
        it("Bruker skal kunne APNE en sykmeldimg med Status APEN") {
            checkStatusOk(StatusEventDTO.APEN, StatusEventDTO.APEN)
        }
        it("Skal kunne endre status til APEN fra AVBRUTT") {
            checkStatusOk(StatusEventDTO.APEN, StatusEventDTO.AVBRUTT)
        }
        it("Skal ikke kunne endre status til APEN fra UTGATT") {
            checkStatusFails(StatusEventDTO.APEN, StatusEventDTO.UTGATT)
        }
        it("Skal ikke kunne endre status til APEN fra SENDT") {
            checkStatusFails(StatusEventDTO.APEN, StatusEventDTO.SENDT)
        }
    }

    describe("Test AVBRUTT status") {
        it("Skal ikke kunne endre status til AVBRUTT om sykmeldingen er sendt") {
            checkStatusFails(StatusEventDTO.AVBRUTT, StatusEventDTO.SENDT)
        }
        it("Skal kunne avbryte en APEN sykmelding") {
            checkStatusOk(StatusEventDTO.AVBRUTT, StatusEventDTO.APEN)
        }
        it("Skal kunne avbryte en BEKREFTET sykmelding") {
            checkStatusOk(StatusEventDTO.AVBRUTT, StatusEventDTO.BEKREFTET)
        }
        it("Skal ikke kunne avbryte en allerede AVBRUTT sykmelding") {
            checkStatusFails(StatusEventDTO.AVBRUTT, StatusEventDTO.AVBRUTT)
        }
        it("Skal ikke kunne avbryte en UTGATT sykmelding") {
            checkStatusFails(StatusEventDTO.AVBRUTT, StatusEventDTO.UTGATT)
        }
    }
})
