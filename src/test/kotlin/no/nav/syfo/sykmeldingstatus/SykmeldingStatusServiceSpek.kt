package no.nav.syfo.sykmeldingstatus

import io.mockk.MockKMatcherScope
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockkClass
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.client.SyfosmregisterStatusClient
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.ArbeidsgiverStatusDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SporsmalOgSvarDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingSendEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.opprettSykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.v2.ArbeidssituasjonDTO
import no.nav.syfo.sykmeldingstatus.api.v2.JaEllerNei
import no.nav.syfo.sykmeldingstatus.api.v2.SporsmalSvar
import no.nav.syfo.sykmeldingstatus.api.v2.SykmeldingUserEvent
import no.nav.syfo.sykmeldingstatus.exception.InvalidSykmeldingStatusException
import no.nav.syfo.sykmeldingstatus.exception.SykmeldingStatusNotFoundException
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisService
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertFailsWith

class SykmeldingStatusServiceSpek : Spek({
    val sykmeldingId = "id"
    val fnr = "fnr"
    val token = "token"
    val sykmeldingStatusKafkaProducer = mockkClass(SykmeldingStatusKafkaProducer::class)
    val sykmeldingStatusJedisService = mockkClass(SykmeldingStatusRedisService::class)
    val syfosmregisterClient = mockkClass(SyfosmregisterStatusClient::class)
    val arbeidsgiverService = mockkClass(ArbeidsgiverService::class)
    val sykmeldingStatusService = SykmeldingStatusService(sykmeldingStatusKafkaProducer, sykmeldingStatusJedisService, syfosmregisterClient, arbeidsgiverService)

    fun checkStatusFails(newStatus: StatusEventDTO, oldStatus: StatusEventDTO, erAvvist: Boolean = false, erEgenmeldt: Boolean = false) {
        runBlocking {
            coEvery { syfosmregisterClient.hentSykmeldingstatus(any(), any()) } returns getSykmeldingStatus(
                oldStatus,
                erAvvist = erAvvist,
                erEgenmeldt = erEgenmeldt
            )
            val expextedErrorMessage =
                "Kan ikke endre status fra $oldStatus til $newStatus for sykmeldingID $sykmeldingId"
            val error = assertFailsWith<InvalidSykmeldingStatusException> {
                when (newStatus) {
                    StatusEventDTO.SENDT -> sykmeldingStatusService.registrerSendt(
                        opprettSykmeldingSendEventDTO(),
                        sykmeldingId,
                        "user",
                        fnr,
                        token
                    )
                    StatusEventDTO.BEKREFTET -> sykmeldingStatusService.registrerBekreftet(
                        opprettSykmeldingBekreftEventDTO(),
                        sykmeldingId,
                        "user",
                        fnr,
                        token
                    )
                    else -> sykmeldingStatusService.registrerStatus(
                        getSykmeldingStatus(newStatus),
                        sykmeldingId,
                        "user",
                        fnr,
                        token
                    )
                }
            }
            error.message shouldBeEqualTo expextedErrorMessage
        }
    }
    fun checkStatusOk(newStatus: StatusEventDTO, oldStatus: StatusEventDTO, erAvvist: Boolean = false, erEgenmeldt: Boolean = false) {
        runBlocking {
            coEvery { syfosmregisterClient.hentSykmeldingstatus(any(), any()) } returns getSykmeldingStatus(oldStatus, erAvvist = erAvvist, erEgenmeldt = erEgenmeldt)
            when (newStatus) {
                StatusEventDTO.SENDT -> sykmeldingStatusService.registrerSendt(
                    opprettSykmeldingSendEventDTO(),
                    sykmeldingId,
                    "user",
                    fnr,
                    token
                )
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
                val redisSykmeldingSendEventDTO = getSykmeldingStatusRedisModel(
                    StatusEventDTO.SENDT,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    erAvvist = true,
                    erEgenmeldt = false
                )
                coEvery { sykmeldingStatusJedisService.getStatus(any()) } returns redisSykmeldingSendEventDTO
                coEvery { syfosmregisterClient.hentSykmeldingstatus(any(), any()) } returns getSykmeldingStatus(
                    StatusEventDTO.APEN,
                    redisSykmeldingSendEventDTO.timestamp.minusNanos(1),
                    erAvvist = true,
                    erEgenmeldt = false
                )
                val sisteStatusEventDTO = sykmeldingStatusService.hentSisteStatusOgSjekkTilgang(sykmeldingId, token)
                sisteStatusEventDTO shouldBeEqualTo SykmeldingStatusEventDTO(
                    StatusEventDTO.SENDT,
                    redisSykmeldingSendEventDTO.timestamp,
                    erAvvist = true,
                    erEgenmeldt = false
                )
            }
        }

        it("Skal hente nyeste status fra registeret") {
            runBlocking {
                val redisSykmeldingStatus =
                    getSykmeldingStatusRedisModel(StatusEventDTO.APEN, OffsetDateTime.now(ZoneOffset.UTC))
                coEvery { sykmeldingStatusJedisService.getStatus(any()) } returns redisSykmeldingStatus
                coEvery { syfosmregisterClient.hentSykmeldingstatus(any(), any()) } returns getSykmeldingStatus(
                    StatusEventDTO.SENDT,
                    redisSykmeldingStatus.timestamp.plusNanos(1),
                    erAvvist = false,
                    erEgenmeldt = true
                )
                val sisteStatus = sykmeldingStatusService.hentSisteStatusOgSjekkTilgang(sykmeldingId, token)
                sisteStatus shouldBeEqualTo SykmeldingStatusEventDTO(
                    StatusEventDTO.SENDT,
                    redisSykmeldingStatus.timestamp.plusNanos(1),
                    erAvvist = false,
                    erEgenmeldt = true
                )
            }
        }

        it("Ikke tilgang til sykmeldingstatus") {
            runBlocking {
                coEvery {
                    syfosmregisterClient.hentSykmeldingstatus(
                        any(),
                        any()
                    )
                } throws RuntimeException("Ingen tilgang")
                val exception = assertFailsWith<SykmeldingStatusNotFoundException> {
                    sykmeldingStatusService.hentSisteStatusOgSjekkTilgang(sykmeldingId, token)
                }
                exception.message shouldBeEqualTo "Fant ikke sykmeldingstatus for sykmelding id $sykmeldingId"
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

    describe("Test bekrefting av avvist sykmelding") {
        it("Får bekrefte avvist sykmelding med status APEN") {
            coEvery { syfosmregisterClient.hentSykmeldingstatus(any(), any()) } returns SykmeldingStatusEventDTO(
                statusEvent = StatusEventDTO.APEN,
                timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                erAvvist = true
            )

            runBlocking {
                sykmeldingStatusService.registrerBekreftetAvvist(sykmeldingId, "user", fnr, token)
            }

            coVerify(exactly = 1) { sykmeldingStatusKafkaProducer.send(matchStatusWithEmptySporsmals("BEKREFTET"), "user", "fnr") }
            verify(exactly = 1) { sykmeldingStatusJedisService.getStatus(any()) }
            verify(exactly = 1) { sykmeldingStatusJedisService.updateStatus(any(), any()) }
        }

        it("Får ikke bekrefte avvist sykmelding med status BEKREFTET") {
            coEvery { syfosmregisterClient.hentSykmeldingstatus(any(), any()) } returns SykmeldingStatusEventDTO(
                statusEvent = StatusEventDTO.BEKREFTET,
                timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                erAvvist = true
            )

            runBlocking {
                assertFailsWith<InvalidSykmeldingStatusException> {
                    sykmeldingStatusService.registrerBekreftetAvvist(sykmeldingId, "user", fnr, token)
                }
            }

            coVerify(exactly = 0) { sykmeldingStatusKafkaProducer.send(matchStatusWithEmptySporsmals("BEKREFTET"), "user", "fnr") }
            verify(exactly = 1) { sykmeldingStatusJedisService.getStatus(any()) }
            verify(exactly = 0) { sykmeldingStatusJedisService.updateStatus(any(), any()) }
        }

        it("Får ikke bekrefte sykmelding som ikke er avvist") {
            coEvery { syfosmregisterClient.hentSykmeldingstatus(any(), any()) } returns SykmeldingStatusEventDTO(
                statusEvent = StatusEventDTO.BEKREFTET,
                timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                erAvvist = false
            )

            runBlocking {
                assertFailsWith<InvalidSykmeldingStatusException> {
                    sykmeldingStatusService.registrerBekreftetAvvist(sykmeldingId, "user", fnr, token)
                }
            }

            coVerify(exactly = 0) { sykmeldingStatusKafkaProducer.send(matchStatusWithEmptySporsmals("BEKREFTET"), "user", "fnr") }
            verify(exactly = 1) { sykmeldingStatusJedisService.getStatus(any()) }
            verify(exactly = 0) { sykmeldingStatusJedisService.updateStatus(any(), any()) }
        }
    }

    describe("Test user event") {
        it("Test SEND user event") {
            coEvery { arbeidsgiverService.getArbeidsgivere(any(), any(), any(), any()) } returns listOf(
                Arbeidsgiverinfo(
                    orgnummer = "123456789",
                    juridiskOrgnummer = "",
                    navn = "",
                    stillingsprosent = "",
                    stilling = "",
                    aktivtArbeidsforhold = true,
                    naermesteLeder = null
                )
            )
            val sykmeldingUserEvent = SykmeldingUserEvent(
                erOpplysningeneRiktige = SporsmalSvar(
                    sporsmaltekst = "",
                    svartekster = "",
                    svar = JaEllerNei.JA,
                ),
                uriktigeOpplysninger = null,
                arbeidssituasjon = SporsmalSvar(
                    sporsmaltekst = "",
                    svartekster = "",
                    svar = ArbeidssituasjonDTO.ARBEIDSTAKER,
                ),
                arbeidsgiverOrgnummer = SporsmalSvar(
                    sporsmaltekst = "",
                    svartekster = "",
                    svar = "123456789",
                ),
                riktigNarmesteLeder = SporsmalSvar(
                    sporsmaltekst = "",
                    svartekster = "",
                    svar = JaEllerNei.NEI,
                ),
                harBruktEgenmelding = null,
                egenmeldingsperioder = null,
                harForsikring = null,
            )

            runBlocking {
                sykmeldingStatusService.registrerUserEvent(sykmeldingUserEvent, "test", "fnr", "token")
            }

            coVerify(exactly = 1) { arbeidsgiverService.getArbeidsgivere(any(), any(), any()) }
            coVerify(exactly = 1) { sykmeldingStatusKafkaProducer.send(statusEquals("SENDT"), "user", "fnr") }
            verify(exactly = 1) { sykmeldingStatusJedisService.getStatus(any()) }
            verify(exactly = 1) { sykmeldingStatusJedisService.updateStatus(any(), any()) }
        }

        it("Test SEND user event - finner ikke riktig arbeidsgiver") {
            coEvery { arbeidsgiverService.getArbeidsgivere(any(), any(), any()) } returns listOf(
                Arbeidsgiverinfo(
                    orgnummer = "123456789",
                    juridiskOrgnummer = "",
                    navn = "",
                    stillingsprosent = "",
                    stilling = "",
                    aktivtArbeidsforhold = true,
                    naermesteLeder = null
                )
            )
            val sykmeldingUserEvent = SykmeldingUserEvent(
                erOpplysningeneRiktige = SporsmalSvar(
                    sporsmaltekst = "",
                    svartekster = "",
                    svar = JaEllerNei.JA,
                ),
                uriktigeOpplysninger = null,
                arbeidssituasjon = SporsmalSvar(
                    sporsmaltekst = "",
                    svartekster = "",
                    svar = ArbeidssituasjonDTO.ARBEIDSTAKER,
                ),
                arbeidsgiverOrgnummer = SporsmalSvar(
                    sporsmaltekst = "",
                    svartekster = "",
                    svar = "feilOrnummer",
                ),
                riktigNarmesteLeder = SporsmalSvar(
                    sporsmaltekst = "",
                    svartekster = "",
                    svar = JaEllerNei.NEI,
                ),
                harBruktEgenmelding = null,
                egenmeldingsperioder = null,
                harForsikring = null,
            )

            assertFailsWith(InvalidSykmeldingStatusException::class) {
                runBlocking {
                    sykmeldingStatusService.registrerUserEvent(sykmeldingUserEvent, "test", "fnr", "token")
                }
            }

            coVerify(exactly = 1) { arbeidsgiverService.getArbeidsgivere(any(), any(), any()) }
            coVerify(exactly = 0) { sykmeldingStatusKafkaProducer.send(statusEquals("SENDT"), "user", "fnr") }
            verify(exactly = 1) { sykmeldingStatusJedisService.getStatus(any()) }
            verify(exactly = 0) { sykmeldingStatusJedisService.updateStatus(any(), any()) }
        }

        it("Test BEKREFT user event") {
            val sykmeldingUserEvent = SykmeldingUserEvent(
                erOpplysningeneRiktige = SporsmalSvar(
                    sporsmaltekst = "",
                    svartekster = "",
                    svar = JaEllerNei.JA,
                ),
                uriktigeOpplysninger = null,
                arbeidssituasjon = SporsmalSvar(
                    sporsmaltekst = "",
                    svartekster = "",
                    svar = ArbeidssituasjonDTO.FRILANSER,
                ),
                arbeidsgiverOrgnummer = null,
                riktigNarmesteLeder = null,
                harBruktEgenmelding = null,
                egenmeldingsperioder = null,
                harForsikring = null,
            )

            runBlocking {
                sykmeldingStatusService.registrerUserEvent(sykmeldingUserEvent, "test", "fnr", "token")
            }

            coVerify(exactly = 0) { arbeidsgiverService.getArbeidsgivere(any(), any(), any()) }
            coVerify(exactly = 1) { sykmeldingStatusKafkaProducer.send(statusEquals("BEKREFTET"), "user", "fnr") }
            verify(exactly = 1) { sykmeldingStatusJedisService.getStatus(any()) }
            verify(exactly = 1) { sykmeldingStatusJedisService.updateStatus(any(), any()) }
        }

        it("Setter nyNarmesteLeder-spørsmal til NEI dersom Arbeidsgforholder er inaktivt") {
            coEvery { arbeidsgiverService.getArbeidsgivere(any(), any(), any()) } returns listOf(
                Arbeidsgiverinfo(
                    orgnummer = "123456789",
                    juridiskOrgnummer = "",
                    navn = "",
                    stillingsprosent = "",
                    stilling = "",
                    aktivtArbeidsforhold = false,
                    naermesteLeder = null
                )
            )

            val sykmeldingUserEvent = SykmeldingUserEvent(
                erOpplysningeneRiktige = SporsmalSvar(
                    sporsmaltekst = "",
                    svartekster = "",
                    svar = JaEllerNei.JA,
                ),
                uriktigeOpplysninger = null,
                arbeidssituasjon = SporsmalSvar(
                    sporsmaltekst = "",
                    svartekster = "",
                    svar = ArbeidssituasjonDTO.ARBEIDSTAKER,
                ),
                arbeidsgiverOrgnummer = SporsmalSvar(
                    sporsmaltekst = "",
                    svartekster = "",
                    svar = "123456789"
                ),
                riktigNarmesteLeder = null,
                harBruktEgenmelding = null,
                egenmeldingsperioder = null,
                harForsikring = null,
            )

            val expected = slot<SykmeldingStatusKafkaEventDTO>()

            runBlocking {
                sykmeldingStatusService.registrerUserEvent(sykmeldingUserEvent, "test", "fnr", "token")
            }

            coVerify(exactly = 1) { arbeidsgiverService.getArbeidsgivere(any(), any(), any()) }
            coVerify(exactly = 1) { sykmeldingStatusKafkaProducer.send(capture(expected), "user", "fnr") }
            verify(exactly = 1) { sykmeldingStatusJedisService.getStatus(any()) }
            verify(exactly = 1) { sykmeldingStatusJedisService.updateStatus(any(), any()) }

            val nlSvar = expected.captured.sporsmals?.filter { it.shortName == ShortNameDTO.NY_NARMESTE_LEDER }

            nlSvar?.size shouldBeEqualTo 1
            nlSvar?.first()?.svar shouldBeEqualTo "NEI"
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
        it("Bruker skal ikke få BEKREFTET en sykmelding med status BEKREFTET") {
            checkStatusFails(StatusEventDTO.BEKREFTET, StatusEventDTO.BEKREFTET)
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
        it("Skal kunne avbryte en UTGATT sykmelding") {
            checkStatusOk(StatusEventDTO.AVBRUTT, StatusEventDTO.UTGATT)
        }
    }

    describe("Test statusendring for avviste sykmeldinger") {
        it("Skal kunne bekrefte en APEN avvist sykmelding") {
            checkStatusOk(StatusEventDTO.BEKREFTET, StatusEventDTO.APEN, erAvvist = true)
        }
        it("Skal ikke kunne gjenåpne en bekreftet avvist sykmelding") {
            checkStatusFails(StatusEventDTO.APEN, StatusEventDTO.BEKREFTET, erAvvist = true)
        }
        it("Skal ikke kunne sende en avvist sykmelding") {
            checkStatusFails(StatusEventDTO.SENDT, StatusEventDTO.APEN, erAvvist = true)
        }
        it("Skal ikke kunne avbryte en avvist sykmelding") {
            checkStatusFails(StatusEventDTO.AVBRUTT, StatusEventDTO.APEN, erAvvist = true)
        }
    }

    describe("Test statusendring for egenmeldinger") {
        it("Skal kunne bekrefte en APEN egenmelding") {
            checkStatusOk(StatusEventDTO.BEKREFTET, StatusEventDTO.APEN, erEgenmeldt = true)
        }
        it("Skal ikke kunne gjenåpne en bekreftet egenmelding") {
            checkStatusFails(StatusEventDTO.APEN, StatusEventDTO.BEKREFTET, erEgenmeldt = true)
        }
        it("Skal ikke kunne sende en egenmelding") {
            checkStatusFails(StatusEventDTO.SENDT, StatusEventDTO.APEN, erEgenmeldt = true)
        }
        it("Skal kunne avbryte en egenmelding") {
            checkStatusOk(StatusEventDTO.AVBRUTT, StatusEventDTO.APEN, erEgenmeldt = true)
        }
        it("Skal ikke kunne gjenåpne en avbrutt egenmelding") {
            checkStatusFails(StatusEventDTO.APEN, StatusEventDTO.AVBRUTT, erEgenmeldt = true)
        }
    }
})

fun MockKMatcherScope.statusEquals(statusEvent: String) = match<SykmeldingStatusKafkaEventDTO> {
    statusEvent == it.statusEvent
}

fun MockKMatcherScope.matchStatusWithEmptySporsmals(statusEvent: String) = match<SykmeldingStatusKafkaEventDTO> {
    statusEvent == it.statusEvent && it.sporsmals?.isEmpty() ?: true
}

fun opprettSykmeldingSendEventDTO(): SykmeldingSendEventDTO =
    SykmeldingSendEventDTO(
        OffsetDateTime.now(ZoneOffset.UTC),
        ArbeidsgiverStatusDTO(orgnummer = "orgnummer", juridiskOrgnummer = null, orgNavn = "navn"),
        listOf(
            SporsmalOgSvarDTO("", no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "ARBEIDSTAKER"),
            SporsmalOgSvarDTO("", no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.NY_NARMESTE_LEDER, SvartypeDTO.JA_NEI, "NEI")
        )
    )
