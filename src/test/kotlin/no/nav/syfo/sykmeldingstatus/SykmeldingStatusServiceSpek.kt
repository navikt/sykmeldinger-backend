package no.nav.syfo.sykmeldingstatus

import io.kotest.core.spec.style.FunSpec
import io.mockk.MockKMatcherScope
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockkClass
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v2.ArbeidssituasjonDTO
import no.nav.syfo.sykmeldingstatus.api.v2.Egenmeldingsperiode
import no.nav.syfo.sykmeldingstatus.api.v2.JaEllerNei
import no.nav.syfo.sykmeldingstatus.api.v2.SporsmalSvar
import no.nav.syfo.sykmeldingstatus.api.v2.SykmeldingUserEvent
import no.nav.syfo.sykmeldingstatus.db.SykmeldingStatusDb
import no.nav.syfo.sykmeldingstatus.exception.InvalidSykmeldingStatusException
import no.nav.syfo.sykmeldingstatus.exception.SykmeldingStatusNotFoundException
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertFailsWith

class SykmeldingStatusServiceSpek : FunSpec({
    val sykmeldingId = "id"
    val fnr = "fnr"
    val token = "token"
    val sykmeldingStatusKafkaProducer = mockkClass(SykmeldingStatusKafkaProducer::class)

    val arbeidsgiverService = mockkClass(ArbeidsgiverService::class)
    val sykmeldingStatusDb = mockkClass(SykmeldingStatusDb::class)
    val sykmeldingStatusService = SykmeldingStatusService(sykmeldingStatusKafkaProducer, arbeidsgiverService, sykmeldingStatusDb)

    fun checkStatusFails(newStatus: StatusEventDTO, oldStatus: StatusEventDTO, erAvvist: Boolean = false, erEgenmeldt: Boolean = false) {
        runBlocking {

            coEvery { sykmeldingStatusDb.getLatestStatus(any(), any()) } returns getSykmeldingStatus(
                oldStatus,
                erAvvist = erAvvist,
                erEgenmeldt = erEgenmeldt
            )
            coEvery { arbeidsgiverService.getArbeidsgivere(any(), any()) } returns listOf(
                Arbeidsgiverinfo(
                    orgnummer = "orgnummer",
                    juridiskOrgnummer = "",
                    navn = "",
                    stillingsprosent = "",
                    stilling = "",
                    aktivtArbeidsforhold = true,
                    naermesteLeder = null
                )
            )
            val expextedErrorMessage =
                "Kan ikke endre status fra $oldStatus til $newStatus for sykmeldingID $sykmeldingId"
            val error = assertFailsWith<InvalidSykmeldingStatusException> {
                when (newStatus) {
                    StatusEventDTO.SENDT -> sykmeldingStatusService.registrerUserEvent(
                        opprettSendtSykmeldingUserEvent(),
                        sykmeldingId,
                        fnr,
                        token
                    )
                    StatusEventDTO.BEKREFTET -> sykmeldingStatusService.registrerUserEvent(
                        opprettBekreftetSykmeldingUserEvent(),
                        sykmeldingId,
                        fnr,
                        token
                    )
                    else -> sykmeldingStatusService.registrerStatus(
                        getSykmeldingStatus(newStatus),
                        sykmeldingId,
                        "user",
                        fnr
                    )
                }
            }
            error.message shouldBeEqualTo expextedErrorMessage
        }
    }
    fun checkStatusOk(newStatus: StatusEventDTO, oldStatus: StatusEventDTO, erAvvist: Boolean = false, erEgenmeldt: Boolean = false) {
        runBlocking {
            coEvery { sykmeldingStatusDb.getLatestStatus(any(), any()) } returns getSykmeldingStatus(oldStatus, erAvvist = erAvvist, erEgenmeldt = erEgenmeldt)
            coEvery { arbeidsgiverService.getArbeidsgivere(any(), any()) } returns listOf(
                Arbeidsgiverinfo(
                    orgnummer = "orgnummer",
                    juridiskOrgnummer = "",
                    navn = "",
                    stillingsprosent = "",
                    stilling = "",
                    aktivtArbeidsforhold = true,
                    naermesteLeder = null
                )
            )
            when (newStatus) {
                StatusEventDTO.SENDT -> sykmeldingStatusService.registrerUserEvent(
                    opprettSendtSykmeldingUserEvent(),
                    sykmeldingId,
                    fnr,
                    token
                )
                StatusEventDTO.BEKREFTET -> sykmeldingStatusService.registrerUserEvent(opprettBekreftetSykmeldingUserEvent(), sykmeldingId, fnr, token)
                else -> sykmeldingStatusService.registrerStatus(getSykmeldingStatus(newStatus), sykmeldingId, "user", fnr)
            }

            coVerify(exactly = 1) { sykmeldingStatusKafkaProducer.send(any(), any(), any()) }
            coVerify(exactly = 1) { sykmeldingStatusDb.insertStatus(any()) }
        }
    }

    beforeTest {
        clearAllMocks()
        coEvery { sykmeldingStatusDb.insertStatus(any()) } just Runs
        coEvery { sykmeldingStatusKafkaProducer.send(any(), any(), any()) } just Runs
        coEvery { sykmeldingStatusDb.getLatestStatus(any(), any()) } throws SykmeldingStatusNotFoundException("not found")
    }

    context("Hent nyeste status") {
        test("Skal hente sendt status fra db") {
            val status = getSykmeldingStatus(
                StatusEventDTO.SENDT,
                OffsetDateTime.now(ZoneOffset.UTC),
                erAvvist = true,
                erEgenmeldt = false
            )
            coEvery { sykmeldingStatusDb.getLatestStatus(any(), any()) } returns status
            val sisteStatusEventDTO = sykmeldingStatusService.hentSisteStatusOgSjekkTilgang(sykmeldingId, fnr)
            sisteStatusEventDTO shouldBeEqualTo SykmeldingStatusEventDTO(
                StatusEventDTO.SENDT,
                status.timestamp,
                erAvvist = true,
                erEgenmeldt = false
            )
        }

        test("Ikke tilgang til sykmeldingstatus") {
            coEvery { sykmeldingStatusDb.getLatestStatus(any(), any()) } throws SykmeldingStatusNotFoundException("Fant ikke sykmeldingstatus for sykmelding id $sykmeldingId")
            val exception = assertFailsWith<SykmeldingStatusNotFoundException> {
                sykmeldingStatusService.hentSisteStatusOgSjekkTilgang(sykmeldingId, token)
            }
            exception.message shouldBeEqualTo "Fant ikke sykmeldingstatus for sykmelding id $sykmeldingId"
        }
    }

    context("Test av BEKREFT for sluttbruker") {
        test("Happy-case") {
            coEvery { sykmeldingStatusDb.getLatestStatus(any(), any()) } returns SykmeldingStatusEventDTO(
                statusEvent = StatusEventDTO.APEN,
                timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                erAvvist = true
            )
            sykmeldingStatusService.registrerUserEvent(opprettBekreftetSykmeldingUserEvent(), sykmeldingId, fnr, token)
            coVerify(exactly = 1) { sykmeldingStatusDb.insertStatus(any()) }
            coVerify { sykmeldingStatusKafkaProducer.send(any(), any(), any()) }
        }
        test("Oppdaterer ikke status hvis bruker ikke har tilgang til sykmelding") {
            coEvery { sykmeldingStatusDb.getLatestStatus(any(), any()) } throws SykmeldingStatusNotFoundException("Ingen tilgang")

            assertFailsWith<SykmeldingStatusNotFoundException> {
                sykmeldingStatusService.registrerUserEvent(opprettBekreftetSykmeldingUserEvent(), sykmeldingId, fnr, token)
            }

            coVerify { sykmeldingStatusDb.getLatestStatus(any(), any()) }
            coVerify(exactly = 0) { sykmeldingStatusDb.insertStatus(any()) }
            coVerify(exactly = 0) { sykmeldingStatusKafkaProducer.send(any(), any(), any()) }
        }
    }

    context("Test bekrefting av avvist sykmelding") {
        test("Får bekrefte avvist sykmelding med status APEN") {
            coEvery { sykmeldingStatusDb.getLatestStatus(any(), any()) } returns SykmeldingStatusEventDTO(
                statusEvent = StatusEventDTO.APEN,
                timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                erAvvist = true
            )

            sykmeldingStatusService.registrerBekreftetAvvist(sykmeldingId, "user", fnr)

            coVerify(exactly = 1) { sykmeldingStatusKafkaProducer.send(matchStatusWithEmptySporsmals("BEKREFTET"), "user", "fnr") }
            coVerify(exactly = 1) { sykmeldingStatusDb.getLatestStatus(any(), any()) }
            coVerify(exactly = 1) { sykmeldingStatusDb.insertStatus(any()) }
        }

        test("Får ikke bekrefte avvist sykmelding med status BEKREFTET") {
            coEvery { sykmeldingStatusDb.getLatestStatus(any(), any()) } returns SykmeldingStatusEventDTO(
                statusEvent = StatusEventDTO.BEKREFTET,
                timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                erAvvist = true
            )

            assertFailsWith<InvalidSykmeldingStatusException> {
                sykmeldingStatusService.registrerBekreftetAvvist(sykmeldingId, "user", fnr)
            }

            coVerify(exactly = 0) { sykmeldingStatusKafkaProducer.send(matchStatusWithEmptySporsmals("BEKREFTET"), "user", "fnr") }
            coVerify(exactly = 1) { sykmeldingStatusDb.getLatestStatus(any(), any()) }
            coVerify(exactly = 0) { sykmeldingStatusDb.insertStatus(any()) }
        }

        test("Får ikke bekrefte sykmelding som ikke er avvist") {
            coEvery { sykmeldingStatusDb.getLatestStatus(any(), any()) } returns SykmeldingStatusEventDTO(
                statusEvent = StatusEventDTO.BEKREFTET,
                timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                erAvvist = false
            )

            assertFailsWith<InvalidSykmeldingStatusException> {
                sykmeldingStatusService.registrerBekreftetAvvist(sykmeldingId, "user", fnr)
            }

            coVerify(exactly = 0) { sykmeldingStatusKafkaProducer.send(matchStatusWithEmptySporsmals("BEKREFTET"), "user", "fnr") }
            coVerify(exactly = 1) { sykmeldingStatusDb.getLatestStatus(any(), any()) }
            coVerify(exactly = 0) { sykmeldingStatusDb.insertStatus(any()) }
        }
    }

    context("Test user event") {
        test("Test SEND user event") {
            coEvery { sykmeldingStatusDb.getLatestStatus(any(), any()) } returns SykmeldingStatusEventDTO(
                statusEvent = StatusEventDTO.APEN,
                timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                erAvvist = false
            )
            coEvery { arbeidsgiverService.getArbeidsgivere(any(), any()) } returns listOf(
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

            sykmeldingStatusService.registrerUserEvent(sykmeldingUserEvent, "test", "fnr", "token")

            coVerify(exactly = 1) { arbeidsgiverService.getArbeidsgivere(any(), any()) }
            coVerify(exactly = 1) { sykmeldingStatusKafkaProducer.send(statusEquals("SENDT"), "user", "fnr") }
            coVerify(exactly = 1) { sykmeldingStatusDb.getLatestStatus(any(), any()) }
            coVerify(exactly = 1) { sykmeldingStatusDb.insertStatus(any()) }
        }
        test("test SENDT user event - Siste status er sendt") {
            coEvery { sykmeldingStatusDb.getLatestStatus(any(), any()) } returns SykmeldingStatusEventDTO(
                statusEvent = StatusEventDTO.SENDT,
                timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                erAvvist = false
            )
            coEvery { arbeidsgiverService.getArbeidsgivere(any(), any()) } returns listOf(
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

            coVerify(exactly = 0) { arbeidsgiverService.getArbeidsgivere(any(), any()) }
            coVerify(exactly = 0) { sykmeldingStatusKafkaProducer.send(statusEquals("SENDT"), "user", "fnr") }
            coVerify(exactly = 1) { sykmeldingStatusDb.getLatestStatus(any(), any()) }
            coVerify(exactly = 0) { sykmeldingStatusDb.insertStatus(any()) }
        }
        test("Test SEND user event - finner ikke riktig arbeidsgiver") {
            coEvery { sykmeldingStatusDb.getLatestStatus(any(), any()) } returns SykmeldingStatusEventDTO(
                statusEvent = StatusEventDTO.APEN,
                timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                erAvvist = false
            )
            coEvery { arbeidsgiverService.getArbeidsgivere(any(), any()) } returns listOf(
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

            coVerify(exactly = 1) { arbeidsgiverService.getArbeidsgivere(any(), any()) }
            coVerify(exactly = 0) { sykmeldingStatusKafkaProducer.send(statusEquals("SENDT"), "user", "fnr") }
            coVerify(exactly = 1) { sykmeldingStatusDb.getLatestStatus(any(), any()) }
            coVerify(exactly = 0) { sykmeldingStatusDb.insertStatus(any()) }
        }

        test("Test BEKREFT user event") {
            coEvery { sykmeldingStatusDb.getLatestStatus(any(), any()) } returns SykmeldingStatusEventDTO(
                statusEvent = StatusEventDTO.APEN,
                timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                erAvvist = false
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
                    svar = ArbeidssituasjonDTO.FRILANSER,
                ),
                arbeidsgiverOrgnummer = null,
                riktigNarmesteLeder = null,
                harBruktEgenmelding = null,
                egenmeldingsperioder = null,
                harForsikring = null,
            )

            sykmeldingStatusService.registrerUserEvent(sykmeldingUserEvent, "test", "fnr", "token")

            coVerify(exactly = 0) { arbeidsgiverService.getArbeidsgivere(any(), any()) }
            coVerify(exactly = 1) { sykmeldingStatusKafkaProducer.send(statusEquals("BEKREFTET"), "user", "fnr") }
            coVerify(exactly = 1) { sykmeldingStatusDb.getLatestStatus(any(), any()) }
            coVerify(exactly = 1) { sykmeldingStatusDb.insertStatus(any()) }
        }

        test("Setter nyNarmesteLeder-spørsmal til NEI dersom Arbeidsgforholder er inaktivt") {
            coEvery { sykmeldingStatusDb.getLatestStatus(any(), any()) } returns SykmeldingStatusEventDTO(
                statusEvent = StatusEventDTO.APEN,
                timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                erAvvist = false
            )
            coEvery { arbeidsgiverService.getArbeidsgivere(any(), any()) } returns listOf(
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

            sykmeldingStatusService.registrerUserEvent(sykmeldingUserEvent, "test", "fnr", "token")

            coVerify(exactly = 1) { arbeidsgiverService.getArbeidsgivere(any(), any()) }
            coVerify(exactly = 1) { sykmeldingStatusKafkaProducer.send(capture(expected), "user", "fnr") }
            coVerify(exactly = 1) { sykmeldingStatusDb.getLatestStatus(any(), any()) }
            coVerify(exactly = 1) { sykmeldingStatusDb.insertStatus(any()) }

            val nlSvar = expected.captured.sporsmals?.filter { it.shortName == ShortNameDTO.NY_NARMESTE_LEDER }

            nlSvar?.size shouldBeEqualTo 1
            nlSvar?.first()?.svar shouldBeEqualTo "NEI"
        }
    }

    context("Test SENDT status") {
        test("Skal kunne sende sykmelding med status APEN") {
            checkStatusOk(newStatus = StatusEventDTO.SENDT, oldStatus = StatusEventDTO.APEN)
        }
        test("Skal ikke kunne SENDE en allerede SENDT Sykmelding") {
            checkStatusFails(newStatus = StatusEventDTO.SENDT, oldStatus = StatusEventDTO.SENDT)
        }
        test("Skal kunne SENDE en BEKREFTET sykmelding") {
            checkStatusOk(newStatus = StatusEventDTO.SENDT, oldStatus = StatusEventDTO.BEKREFTET)
        }
        test("skal ikke kunne SENDE en UTGÅTT sykmelding") {
            checkStatusFails(newStatus = StatusEventDTO.SENDT, oldStatus = StatusEventDTO.UTGATT)
        }
        test("SKal kunne SENDE en AVBRUTT sykmelding") {
            checkStatusOk(newStatus = StatusEventDTO.SENDT, oldStatus = StatusEventDTO.AVBRUTT)
        }
    }

    context("Test BEKREFT status") {
        test("Bruker skal få BEKREFTET sykmelding med status APEN") {
            checkStatusOk(StatusEventDTO.BEKREFTET, StatusEventDTO.APEN)
        }
        test("Bruker skal få BEKREFTET en sykmelding med status BEKREFTET") {
            checkStatusOk(StatusEventDTO.BEKREFTET, StatusEventDTO.BEKREFTET)
        }

        test("Bruker skal få bekrefte sin egen sykmelding med status AVBRUTT") {
            checkStatusOk(newStatus = StatusEventDTO.BEKREFTET, oldStatus = StatusEventDTO.AVBRUTT)
        }

        test("Skal ikke kunne BEKREFTE når siste status er SENDT") {
            checkStatusFails(newStatus = StatusEventDTO.BEKREFTET, oldStatus = StatusEventDTO.SENDT)
        }

        test("Skal ikke kunne bekrefte når siste status er UTGATT") {
            checkStatusFails(newStatus = StatusEventDTO.BEKREFTET, oldStatus = StatusEventDTO.UTGATT)
        }
    }

    context("Test APEN status") {
        test("Bruker skal kunne APNE en sykmelding med statsu BEKREFTET") {
            checkStatusOk(StatusEventDTO.APEN, StatusEventDTO.BEKREFTET)
        }
        test("Bruker skal kunne APNE en sykmeldimg med Status APEN") {
            checkStatusOk(StatusEventDTO.APEN, StatusEventDTO.APEN)
        }
        test("Skal kunne endre status til APEN fra AVBRUTT") {
            checkStatusOk(StatusEventDTO.APEN, StatusEventDTO.AVBRUTT)
        }
        test("Skal ikke kunne endre status til APEN fra UTGATT") {
            checkStatusFails(StatusEventDTO.APEN, StatusEventDTO.UTGATT)
        }
        test("Skal ikke kunne endre status til APEN fra SENDT") {
            checkStatusFails(StatusEventDTO.APEN, StatusEventDTO.SENDT)
        }
    }

    context("Test AVBRUTT status") {
        test("Skal ikke kunne endre status til AVBRUTT om sykmeldingen er sendt") {
            checkStatusFails(StatusEventDTO.AVBRUTT, StatusEventDTO.SENDT)
        }
        test("Skal kunne avbryte en APEN sykmelding") {
            checkStatusOk(StatusEventDTO.AVBRUTT, StatusEventDTO.APEN)
        }
        test("Skal kunne avbryte en BEKREFTET sykmelding") {
            checkStatusOk(StatusEventDTO.AVBRUTT, StatusEventDTO.BEKREFTET)
        }
        test("Skal kunne avbryte en allerede AVBRUTT sykmelding") {
            checkStatusOk(StatusEventDTO.AVBRUTT, StatusEventDTO.AVBRUTT)
        }
        test("Skal kunne avbryte en UTGATT sykmelding") {
            checkStatusOk(StatusEventDTO.AVBRUTT, StatusEventDTO.UTGATT)
        }
    }

    context("Test statusendring for avviste sykmeldinger") {
        test("Skal kunne bekrefte en APEN avvist sykmelding") {
            checkStatusOk(StatusEventDTO.BEKREFTET, StatusEventDTO.APEN, erAvvist = true)
        }
        test("Skal ikke kunne gjenåpne en bekreftet avvist sykmelding") {
            checkStatusFails(StatusEventDTO.APEN, StatusEventDTO.BEKREFTET, erAvvist = true)
        }
        test("Skal ikke kunne sende en avvist sykmelding") {
            checkStatusFails(StatusEventDTO.SENDT, StatusEventDTO.APEN, erAvvist = true)
        }
        test("Skal ikke kunne avbryte en avvist sykmelding") {
            checkStatusFails(StatusEventDTO.AVBRUTT, StatusEventDTO.APEN, erAvvist = true)
        }
    }

    context("Test statusendring for egenmeldinger") {
        test("Skal kunne bekrefte en APEN egenmelding") {
            checkStatusOk(StatusEventDTO.BEKREFTET, StatusEventDTO.APEN, erEgenmeldt = true)
        }
        test("Skal ikke kunne gjenåpne en bekreftet egenmelding") {
            checkStatusFails(StatusEventDTO.APEN, StatusEventDTO.BEKREFTET, erEgenmeldt = true)
        }
        test("Skal ikke kunne sende en egenmelding") {
            checkStatusFails(StatusEventDTO.SENDT, StatusEventDTO.APEN, erEgenmeldt = true)
        }
        test("Skal kunne avbryte en egenmelding") {
            checkStatusOk(StatusEventDTO.AVBRUTT, StatusEventDTO.APEN, erEgenmeldt = true)
        }
        test("Skal ikke kunne gjenåpne en avbrutt egenmelding") {
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

fun opprettSendtSykmeldingUserEvent(): SykmeldingUserEvent =
    SykmeldingUserEvent(
        erOpplysningeneRiktige = SporsmalSvar(
            sporsmaltekst = "",
            svartekster = "",
            svar = JaEllerNei.JA
        ),
        uriktigeOpplysninger = null,
        arbeidssituasjon = SporsmalSvar(
            sporsmaltekst = "",
            svartekster = "",
            svar = ArbeidssituasjonDTO.ARBEIDSTAKER
        ),
        arbeidsgiverOrgnummer = SporsmalSvar(
            sporsmaltekst = "",
            svartekster = "",
            svar = "orgnummer"
        ),
        riktigNarmesteLeder = SporsmalSvar(
            sporsmaltekst = "",
            svartekster = "",
            svar = JaEllerNei.JA
        ),
        harBruktEgenmelding = null,
        egenmeldingsperioder = null,
        harForsikring = null
    )

fun opprettBekreftetSykmeldingUserEvent(): SykmeldingUserEvent =
    SykmeldingUserEvent(
        erOpplysningeneRiktige = SporsmalSvar(
            sporsmaltekst = "",
            svartekster = "",
            svar = JaEllerNei.JA
        ),
        uriktigeOpplysninger = null,
        arbeidssituasjon = SporsmalSvar(
            sporsmaltekst = "",
            svartekster = "",
            svar = ArbeidssituasjonDTO.FRILANSER
        ),
        arbeidsgiverOrgnummer = null,
        riktigNarmesteLeder = null,
        harBruktEgenmelding = SporsmalSvar(
            sporsmaltekst = "",
            svartekster = "",
            svar = JaEllerNei.JA
        ),
        egenmeldingsperioder = SporsmalSvar(
            sporsmaltekst = "",
            svartekster = "",
            svar = listOf(
                Egenmeldingsperiode(
                    fom = LocalDate.now().minusWeeks(1),
                    tom = LocalDate.now(),
                )
            )
        ),
        harForsikring = SporsmalSvar(
            sporsmaltekst = "",
            svartekster = "",
            svar = JaEllerNei.JA
        )
    )
