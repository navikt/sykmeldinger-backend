package no.nav.syfo.sykmeldingstatus

import io.kotest.core.spec.style.FunSpec
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockkClass
import io.mockk.slot
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.sykmeldingstatus.TestHelper.Companion.februar
import no.nav.syfo.sykmeldingstatus.TestHelper.Companion.januar
import no.nav.syfo.sykmeldingstatus.TestHelper.Companion.mars
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v2.Arbeidssituasjon.ANNET
import no.nav.syfo.sykmeldingstatus.api.v2.Arbeidssituasjon.ARBEIDSLEDIG
import no.nav.syfo.sykmeldingstatus.api.v2.Arbeidssituasjon.ARBEIDSTAKER
import no.nav.syfo.sykmeldingstatus.api.v2.Arbeidssituasjon.FRILANSER
import no.nav.syfo.sykmeldingstatus.api.v2.EndreEgenmeldingsdagerEvent
import no.nav.syfo.sykmeldingstatus.api.v2.JaEllerNei
import no.nav.syfo.sykmeldingstatus.api.v2.SporsmalSvar
import no.nav.syfo.sykmeldingstatus.api.v2.SykmeldingFormResponse
import no.nav.syfo.sykmeldingstatus.db.SykmeldingStatusDb
import no.nav.syfo.sykmeldingstatus.exception.InvalidSykmeldingStatusException
import no.nav.syfo.sykmeldingstatus.exception.SykmeldingStatusNotFoundException
import no.nav.syfo.sykmeldingstatus.kafka.model.ShortNameKafkaDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.SporsmalOgSvarKafkaDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.SvartypeKafkaDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.TidligereArbeidsgiverKafkaDTO
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import org.amshove.kluent.shouldBeEqualTo

class SykmeldingStatusServiceSpec :
    FunSpec(
        {
            val sykmeldingId = "id"
            val fnr = "fnr"

            val sykmeldingStatusKafkaProducer = mockkClass(SykmeldingStatusKafkaProducer::class)

            val arbeidsgiverService = mockkClass(ArbeidsgiverService::class)
            val sykmeldingStatusDb = mockkClass(SykmeldingStatusDb::class)
            val sykmeldingService = mockkClass(SykmeldingService::class)
            val sykmeldingStatusService =
                SykmeldingStatusService(
                    sykmeldingStatusKafkaProducer,
                    arbeidsgiverService,
                    sykmeldingStatusDb,
                    sykmeldingService,
                )

            fun checkStatusFails(
                newStatus: StatusEventDTO,
                oldStatus: StatusEventDTO,
                erAvvist: Boolean = false,
                erEgenmeldt: Boolean = false,
            ) {
                runBlocking {
                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        getSykmeldingStatus(
                            oldStatus,
                            erAvvist = erAvvist,
                            erEgenmeldt = erEgenmeldt,
                        )
                    coEvery { arbeidsgiverService.getArbeidsgivere(any(), any()) } returns
                        listOf(
                            Arbeidsgiverinfo(
                                orgnummer = "orgnummer",
                                juridiskOrgnummer = "",
                                navn = "",
                                stillingsprosent = "",
                                stilling = "",
                                aktivtArbeidsforhold = true,
                                naermesteLeder = null,
                            ),
                        )
                    val expextedErrorMessage =
                        "Kan ikke endre status fra $oldStatus til $newStatus for sykmeldingID $sykmeldingId"
                    val error =
                        assertFailsWith<InvalidSykmeldingStatusException> {
                            when (newStatus) {
                                StatusEventDTO.SENDT ->
                                    sykmeldingStatusService.createSendtStatus(
                                        opprettSendtSykmeldingUserEvent(),
                                        sykmeldingId,
                                        fnr,
                                    )
                                StatusEventDTO.BEKREFTET ->
                                    sykmeldingStatusService.createSendtStatus(
                                        opprettBekreftetSykmeldingUserEvent(),
                                        sykmeldingId,
                                        fnr,
                                    )
                                StatusEventDTO.APEN ->
                                    sykmeldingStatusService.createGjenapneStatus(
                                        sykmeldingId,
                                        fnr,
                                    )
                                StatusEventDTO.AVBRUTT ->
                                    sykmeldingStatusService.createAvbruttStatus(
                                        sykmeldingId,
                                        fnr,
                                    )
                                else ->
                                    throw IllegalStateException(
                                        "Ikke implementert $newStatus i testene",
                                    )
                            }
                        }
                    error.message shouldBeEqualTo expextedErrorMessage
                }
            }

            fun checkStatusOk(
                newStatus: StatusEventDTO,
                oldStatus: StatusEventDTO,
                erAvvist: Boolean = false,
                erEgenmeldt: Boolean = false,
            ) {
                runBlocking {
                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        getSykmeldingStatus(
                            oldStatus,
                            erAvvist = erAvvist,
                            erEgenmeldt = erEgenmeldt,
                        )
                    coEvery { arbeidsgiverService.getArbeidsgivere(any(), any()) } returns
                        listOf(
                            Arbeidsgiverinfo(
                                orgnummer = "orgnummer",
                                juridiskOrgnummer = "",
                                navn = "",
                                stillingsprosent = "",
                                stilling = "",
                                aktivtArbeidsforhold = true,
                                naermesteLeder = null,
                            ),
                        )
                    when (newStatus) {
                        StatusEventDTO.SENDT ->
                            sykmeldingStatusService.createSendtStatus(
                                opprettSendtSykmeldingUserEvent(),
                                sykmeldingId,
                                fnr,
                            )
                        StatusEventDTO.BEKREFTET ->
                            sykmeldingStatusService.createSendtStatus(
                                opprettBekreftetSykmeldingUserEvent(),
                                sykmeldingId,
                                fnr,
                            )
                        StatusEventDTO.APEN ->
                            sykmeldingStatusService.createGjenapneStatus(
                                sykmeldingId,
                                fnr,
                            )
                        StatusEventDTO.AVBRUTT ->
                            sykmeldingStatusService.createAvbruttStatus(
                                sykmeldingId,
                                fnr,
                            )
                        else ->
                            throw IllegalStateException("Ikke implementert $newStatus i testene")
                    }

                    coVerify(exactly = 1) { sykmeldingStatusDb.insertStatus(any(), any(), any()) }
                }
            }

            beforeTest {
                clearAllMocks()
                coEvery { sykmeldingStatusDb.insertStatus(any(), any(), any()) } just Runs
                coEvery { sykmeldingStatusKafkaProducer.send(any(), any()) } just Runs
                coEvery {
                    sykmeldingStatusDb.getLatestStatus(
                        any(),
                        any(),
                    )
                } throws SykmeldingStatusNotFoundException("not found")
                coEvery { sykmeldingService.getSykmelding(any(), any()) } returns null
                coEvery { sykmeldingService.getSykmeldinger(any()) } returns emptyList()
            }

            context("Hent nyeste status") {
                test("Skal hente sendt status fra db") {
                    val status =
                        getSykmeldingStatus(
                            StatusEventDTO.SENDT,
                            OffsetDateTime.now(ZoneOffset.UTC),
                            erAvvist = true,
                            erEgenmeldt = false,
                        )
                    coEvery { sykmeldingStatusDb.getLatestStatus(any(), any()) } returns status
                    val sisteStatusEventDTO =
                        sykmeldingStatusService.hentSisteStatusOgSjekkTilgang(
                            sykmeldingId,
                            fnr,
                        )
                    sisteStatusEventDTO shouldBeEqualTo
                        SykmeldingStatusEventDTO(
                            StatusEventDTO.SENDT,
                            status.timestamp,
                            erAvvist = true,
                            erEgenmeldt = false,
                        )
                }

                test("Ikke tilgang til sykmeldingstatus") {
                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } throws
                        SykmeldingStatusNotFoundException(
                            "Fant ikke sykmeldingstatus for sykmelding id $sykmeldingId",
                        )
                    val exception =
                        assertFailsWith<SykmeldingStatusNotFoundException> {
                            sykmeldingStatusService.hentSisteStatusOgSjekkTilgang(
                                sykmeldingId,
                                fnr,
                            )
                        }
                    exception.message shouldBeEqualTo
                        "Fant ikke sykmeldingstatus for sykmelding id $sykmeldingId"
                }
            }

            context("Test av BEKREFT for sluttbruker") {
                test("Happy-case") {
                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )

                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns getSykmeldingDTO()

                    sykmeldingStatusService.createSendtStatus(
                        opprettBekreftetSykmeldingUserEvent(),
                        sykmeldingId,
                        fnr,
                    )
                    coVerify(exactly = 1) { sykmeldingStatusDb.insertStatus(any(), any(), any()) }
                }
                test("Oppdaterer ikke status hvis bruker ikke har tilgang til sykmelding") {
                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } throws SykmeldingStatusNotFoundException("Ingen tilgang")

                    assertFailsWith<SykmeldingStatusNotFoundException> {
                        sykmeldingStatusService.createSendtStatus(
                            opprettBekreftetSykmeldingUserEvent(),
                            sykmeldingId,
                            fnr,
                        )
                    }

                    coVerify { sykmeldingStatusDb.getLatestStatus(any(), any()) }
                    coVerify(exactly = 0) { sykmeldingStatusDb.insertStatus(any(), any(), any()) }
                }
            }

            context("Test bekrefting av avvist sykmelding") {
                test("Får bekrefte avvist sykmelding med status APEN") {
                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )

                    sykmeldingStatusService.createBekreftetAvvistStatus(
                        sykmeldingId,
                        fnr,
                    )

                    coVerify(exactly = 1) { sykmeldingStatusDb.getLatestStatus(any(), any()) }
                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            matchStatusWithEmptySporsmals("BEKREFTET"),
                            any(),
                            any(),
                        )
                    }
                }

                test("Får ikke bekrefte avvist sykmelding med status BEKREFTET") {
                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.BEKREFTET,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )

                    assertFailsWith<InvalidSykmeldingStatusException> {
                        sykmeldingStatusService.createBekreftetAvvistStatus(
                            sykmeldingId,
                            fnr,
                        )
                    }

                    coVerify(exactly = 1) { sykmeldingStatusDb.getLatestStatus(any(), any()) }
                    coVerify(exactly = 0) { sykmeldingStatusDb.insertStatus(any(), any(), any()) }
                }

                test("Får ikke bekrefte sykmelding som ikke er avvist") {
                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.BEKREFTET,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = false,
                        )

                    assertFailsWith<InvalidSykmeldingStatusException> {
                        sykmeldingStatusService.createBekreftetAvvistStatus(
                            sykmeldingId,
                            fnr,
                        )
                    }

                    coVerify(exactly = 1) { sykmeldingStatusDb.getLatestStatus(any(), any()) }
                    coVerify(exactly = 0) { sykmeldingStatusDb.insertStatus(any(), any(), any()) }
                }
            }

            context("Test user event") {
                test("Test SEND user event") {
                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = false,
                        )
                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns getSykmeldingDTO()
                    coEvery { arbeidsgiverService.getArbeidsgivere(any(), any()) } returns
                        listOf(
                            Arbeidsgiverinfo(
                                orgnummer = "123456789",
                                juridiskOrgnummer = "",
                                navn = "",
                                stillingsprosent = "",
                                stilling = "",
                                aktivtArbeidsforhold = true,
                                naermesteLeder = null,
                            ),
                        )
                    val sykmeldingFormResponse =
                        SykmeldingFormResponse(
                            erOpplysningeneRiktige =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = JaEllerNei.JA,
                                ),
                            uriktigeOpplysninger = null,
                            arbeidssituasjon =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = ARBEIDSTAKER,
                                ),
                            arbeidsgiverOrgnummer =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = "123456789",
                                ),
                            riktigNarmesteLeder =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = JaEllerNei.NEI,
                                ),
                            harBruktEgenmelding = null,
                            egenmeldingsperioder = null,
                            harForsikring = null,
                            harBruktEgenmeldingsdager = null,
                            egenmeldingsdager = null,
                        )

                    sykmeldingStatusService.createSendtStatus(
                        sykmeldingFormResponse,
                        "test",
                        "fnr",
                    )

                    coVerify(exactly = 1) { arbeidsgiverService.getArbeidsgivere(any(), any()) }
                    coVerify(exactly = 1) { sykmeldingStatusDb.getLatestStatus(any(), any()) }
                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(statusEquals("SENDT"), any(), any())
                    }
                }
                test("test SENDT user event - Siste status er sendt") {
                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.SENDT,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = false,
                        )
                    coEvery { arbeidsgiverService.getArbeidsgivere(any(), any()) } returns
                        listOf(
                            Arbeidsgiverinfo(
                                orgnummer = "123456789",
                                juridiskOrgnummer = "",
                                navn = "",
                                stillingsprosent = "",
                                stilling = "",
                                aktivtArbeidsforhold = true,
                                naermesteLeder = null,
                            ),
                        )
                    val sykmeldingFormResponse =
                        SykmeldingFormResponse(
                            erOpplysningeneRiktige =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = JaEllerNei.JA,
                                ),
                            uriktigeOpplysninger = null,
                            arbeidssituasjon =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = ARBEIDSTAKER,
                                ),
                            arbeidsgiverOrgnummer =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = "feilOrnummer",
                                ),
                            riktigNarmesteLeder =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = JaEllerNei.NEI,
                                ),
                            harBruktEgenmelding = null,
                            egenmeldingsperioder = null,
                            harForsikring = null,
                            harBruktEgenmeldingsdager = null,
                            egenmeldingsdager = null,
                        )

                    assertFailsWith(InvalidSykmeldingStatusException::class) {
                        runBlocking {
                            sykmeldingStatusService.createSendtStatus(
                                sykmeldingFormResponse,
                                "test",
                                "fnr",
                            )
                        }
                    }

                    coVerify(exactly = 0) { arbeidsgiverService.getArbeidsgivere(any(), any()) }
                    coVerify(exactly = 1) { sykmeldingStatusDb.getLatestStatus(any(), any()) }
                    coVerify(exactly = 0) {
                        sykmeldingStatusDb.insertStatus(statusEquals("SENDT"), any(), any())
                    }
                }
                test("Test SEND user event - finner ikke riktig arbeidsgiver") {
                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = false,
                        )
                    coEvery { arbeidsgiverService.getArbeidsgivere(any(), any()) } returns
                        listOf(
                            Arbeidsgiverinfo(
                                orgnummer = "123456789",
                                juridiskOrgnummer = "",
                                navn = "",
                                stillingsprosent = "",
                                stilling = "",
                                aktivtArbeidsforhold = true,
                                naermesteLeder = null,
                            ),
                        )
                    val sykmeldingFormResponse =
                        SykmeldingFormResponse(
                            erOpplysningeneRiktige =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = JaEllerNei.JA,
                                ),
                            uriktigeOpplysninger = null,
                            arbeidssituasjon =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = ARBEIDSTAKER,
                                ),
                            arbeidsgiverOrgnummer =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = "feilOrnummer",
                                ),
                            riktigNarmesteLeder =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = JaEllerNei.NEI,
                                ),
                            harBruktEgenmelding = null,
                            egenmeldingsperioder = null,
                            harForsikring = null,
                            harBruktEgenmeldingsdager = null,
                            egenmeldingsdager = null,
                        )

                    assertFailsWith(InvalidSykmeldingStatusException::class) {
                        runBlocking {
                            sykmeldingStatusService.createSendtStatus(
                                sykmeldingFormResponse,
                                "test",
                                "fnr",
                            )
                        }
                    }

                    coVerify(exactly = 1) { arbeidsgiverService.getArbeidsgivere(any(), any()) }
                    coVerify(exactly = 1) { sykmeldingStatusDb.getLatestStatus(any(), any()) }
                    coVerify(exactly = 0) { sykmeldingStatusDb.insertStatus(any(), any(), any()) }
                }

                test("Test BEKREFT user event") {
                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = false,
                        )
                    val sykmeldingFormResponse =
                        SykmeldingFormResponse(
                            erOpplysningeneRiktige =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = JaEllerNei.JA,
                                ),
                            uriktigeOpplysninger = null,
                            arbeidssituasjon =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = FRILANSER,
                                ),
                            arbeidsgiverOrgnummer = null,
                            riktigNarmesteLeder = null,
                            harBruktEgenmelding = null,
                            egenmeldingsperioder = null,
                            harForsikring = null,
                            harBruktEgenmeldingsdager = null,
                            egenmeldingsdager = null,
                        )

                    sykmeldingStatusService.createSendtStatus(
                        sykmeldingFormResponse,
                        "test",
                        "fnr",
                    )

                    coVerify(exactly = 0) { arbeidsgiverService.getArbeidsgivere(any(), any()) }
                    coVerify(exactly = 1) { sykmeldingStatusDb.getLatestStatus(any(), any()) }
                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(statusEquals("BEKREFTET"), any(), any())
                    }
                }

                test(
                    "Setter nyNarmesteLeder-spørsmal til NEI dersom Arbeidsgforholder er inaktivt",
                ) {
                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = false,
                        )
                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns getSykmeldingDTO()
                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns getSykmeldingDTO()
                    coEvery { arbeidsgiverService.getArbeidsgivere(any(), any()) } returns
                        listOf(
                            Arbeidsgiverinfo(
                                orgnummer = "123456789",
                                juridiskOrgnummer = "",
                                navn = "",
                                stillingsprosent = "",
                                stilling = "",
                                aktivtArbeidsforhold = false,
                                naermesteLeder = null,
                            ),
                        )

                    val sykmeldingFormResponse =
                        SykmeldingFormResponse(
                            erOpplysningeneRiktige =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = JaEllerNei.JA,
                                ),
                            uriktigeOpplysninger = null,
                            arbeidssituasjon =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = ARBEIDSTAKER,
                                ),
                            arbeidsgiverOrgnummer =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = "123456789",
                                ),
                            riktigNarmesteLeder = null,
                            harBruktEgenmelding = null,
                            egenmeldingsperioder = null,
                            harForsikring = null,
                            harBruktEgenmeldingsdager = null,
                            egenmeldingsdager = null,
                        )

                    val expected = slot<SykmeldingStatusKafkaEventDTO>()

                    sykmeldingStatusService.createSendtStatus(
                        sykmeldingFormResponse,
                        "test",
                        "fnr",
                    )

                    coVerify(exactly = 1) { arbeidsgiverService.getArbeidsgivere(any(), any()) }
                    coVerify(exactly = 1) { sykmeldingStatusDb.getLatestStatus(any(), any()) }
                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(capture(expected), any(), any())
                    }

                    val nlSvar =
                        expected.captured.sporsmals?.filter {
                            it.shortName == ShortNameKafkaDTO.NY_NARMESTE_LEDER
                        }

                    nlSvar?.size shouldBeEqualTo 1
                    nlSvar?.first()?.svar shouldBeEqualTo "NEI"
                }
            }

            context("Endre egenmeldingsdager") {
                test("Oppdatere sporsmal med nye egenmeldingsdager") {
                    coEvery {
                        sykmeldingStatusDb.getSykmeldingStatus(
                            sykmeldingId = "sykmelding-id",
                            fnr = "22222222",
                        )
                    } returns
                        (SykmeldingStatusKafkaEventDTO(
                            sporsmals =
                                listOf(
                                    SporsmalOgSvarKafkaDTO(
                                        svartype = SvartypeKafkaDTO.DAGER,
                                        shortName = ShortNameKafkaDTO.EGENMELDINGSDAGER,
                                        svar = "",
                                        tekst = "tom string",
                                    ),
                                    SporsmalOgSvarKafkaDTO(
                                        svartype = SvartypeKafkaDTO.ARBEIDSSITUASJON,
                                        shortName = ShortNameKafkaDTO.ARBEIDSSITUASJON,
                                        svar = "8765432",
                                        tekst = "",
                                    ),
                                ),
                            sykmeldingId = "sykmelding-id",
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                            statusEvent = StatusEventDTO.SENDT.toString(),
                        ) to null)

                    sykmeldingStatusService.endreEgenmeldingsdager(
                        sykmeldingId = "sykmelding-id",
                        egenmeldingsdagerEvent =
                            EndreEgenmeldingsdagerEvent(
                                dager =
                                    listOf(
                                        LocalDate.parse("2021-02-01"),
                                        LocalDate.parse("2021-02-02"),
                                    ),
                                tekst = "Egenmeldingsdager spørsmål",
                            ),
                        fnr = "22222222",
                    )

                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match {
                                val last = it.sporsmals?.last()
                                val first = it.sporsmals?.first()
                                // Verify value has been updated
                                last?.svar == "[\"2021-02-01\",\"2021-02-02\"]" &&
                                    last.shortName == ShortNameKafkaDTO.EGENMELDINGSDAGER &&
                                    // Verify that existing remains untouched
                                    first?.shortName == ShortNameKafkaDTO.ARBEIDSSITUASJON &&
                                    first.svar == "8765432" &&
                                    it.erSvarOppdatering == true
                            },
                            any(),
                            any(),
                        )
                    }
                }

                test("Fjern spørsmål om egenmeldingsdager") {
                    coEvery {
                        sykmeldingStatusDb.getSykmeldingStatus(
                            sykmeldingId = "sykmelding-id",
                            fnr = "22222222",
                        )
                    } returns
                        (SykmeldingStatusKafkaEventDTO(
                            sporsmals =
                                listOf(
                                    SporsmalOgSvarKafkaDTO(
                                        svartype = SvartypeKafkaDTO.DAGER,
                                        shortName = ShortNameKafkaDTO.EGENMELDINGSDAGER,
                                        svar = "",
                                        tekst = "tom string",
                                    ),
                                    SporsmalOgSvarKafkaDTO(
                                        svartype = SvartypeKafkaDTO.ARBEIDSSITUASJON,
                                        shortName = ShortNameKafkaDTO.ARBEIDSSITUASJON,
                                        svar = "8765432",
                                        tekst = "",
                                    ),
                                ),
                            sykmeldingId = "sykmelding-id",
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                            statusEvent = StatusEventDTO.SENDT.toString(),
                        ) to null)

                    sykmeldingStatusService.endreEgenmeldingsdager(
                        sykmeldingId = "sykmelding-id",
                        egenmeldingsdagerEvent =
                            EndreEgenmeldingsdagerEvent(
                                dager = listOf(),
                                tekst = "Egenmeldingsdager spørsmål",
                            ),
                        fnr = "22222222",
                    )

                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match {
                                it.sporsmals?.size == 1 &&
                                    it.sporsmals?.first()?.svartype ==
                                        SvartypeKafkaDTO.ARBEIDSSITUASJON
                            },
                            any(),
                            any(),
                        )
                    }
                }

                test("Legg til egenmeldingsdager") {
                    coEvery {
                        sykmeldingStatusDb.getSykmeldingStatus(
                            sykmeldingId = "sykmelding-id",
                            fnr = "22222222",
                        )
                    } returns
                        (SykmeldingStatusKafkaEventDTO(
                            sporsmals =
                                listOf(
                                    SporsmalOgSvarKafkaDTO(
                                        svartype = SvartypeKafkaDTO.ARBEIDSSITUASJON,
                                        shortName = ShortNameKafkaDTO.ARBEIDSSITUASJON,
                                        svar = "8765432",
                                        tekst = "",
                                    ),
                                ),
                            sykmeldingId = "sykmelding-id",
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                            statusEvent = StatusEventDTO.SENDT.toString(),
                        ) to null)

                    sykmeldingStatusService.endreEgenmeldingsdager(
                        sykmeldingId = "sykmelding-id",
                        egenmeldingsdagerEvent =
                            EndreEgenmeldingsdagerEvent(
                                dager =
                                    listOf(
                                        LocalDate.parse("2021-02-01"),
                                        LocalDate.parse("2021-02-02"),
                                    ),
                                tekst = "Egenmeldingsdager spørsmål",
                            ),
                        fnr = "22222222",
                    )

                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match {
                                val last = it.sporsmals?.last()
                                val first = it.sporsmals?.first()
                                // Verify value has been updated
                                last?.svar == "[\"2021-02-01\",\"2021-02-02\"]" &&
                                    last.shortName == ShortNameKafkaDTO.EGENMELDINGSDAGER &&
                                    // Verify that existing remains untouched
                                    first?.shortName == ShortNameKafkaDTO.ARBEIDSSITUASJON &&
                                    first.svar == "8765432" &&
                                    it.erSvarOppdatering == true
                            },
                            any(),
                            any(),
                        )
                    }
                }
            }

            context("Test SENDT status") {
                test("Skal kunne sende sykmelding med status APEN") {
                    checkStatusOk(
                        newStatus = StatusEventDTO.SENDT,
                        oldStatus = StatusEventDTO.APEN,
                    )
                }
                test("Skal ikke kunne SENDE en allerede SENDT Sykmelding") {
                    checkStatusFails(
                        newStatus = StatusEventDTO.SENDT,
                        oldStatus = StatusEventDTO.SENDT,
                    )
                }
                test("Skal kunne SENDE en BEKREFTET sykmelding") {
                    checkStatusOk(
                        newStatus = StatusEventDTO.SENDT,
                        oldStatus = StatusEventDTO.BEKREFTET,
                    )
                }
                test("skal ikke kunne SENDE en UTGÅTT sykmelding") {
                    checkStatusFails(
                        newStatus = StatusEventDTO.SENDT,
                        oldStatus = StatusEventDTO.UTGATT,
                    )
                }
                test("SKal kunne SENDE en AVBRUTT sykmelding") {
                    checkStatusOk(
                        newStatus = StatusEventDTO.SENDT,
                        oldStatus = StatusEventDTO.AVBRUTT,
                    )
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
                    checkStatusOk(
                        newStatus = StatusEventDTO.BEKREFTET,
                        oldStatus = StatusEventDTO.AVBRUTT,
                    )
                }

                test("Skal ikke kunne BEKREFTE når siste status er SENDT") {
                    checkStatusFails(
                        newStatus = StatusEventDTO.BEKREFTET,
                        oldStatus = StatusEventDTO.SENDT,
                    )
                }

                test("Skal ikke kunne bekrefte når siste status er UTGATT") {
                    checkStatusFails(
                        newStatus = StatusEventDTO.BEKREFTET,
                        oldStatus = StatusEventDTO.UTGATT,
                    )
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
                    checkStatusOk(
                        StatusEventDTO.BEKREFTET,
                        StatusEventDTO.APEN,
                        erAvvist = true,
                    )
                }
                test("Skal ikke kunne gjenåpne en bekreftet avvist sykmelding") {
                    checkStatusFails(
                        StatusEventDTO.APEN,
                        StatusEventDTO.BEKREFTET,
                        erAvvist = true,
                    )
                }
                test("Skal ikke kunne sende en avvist sykmelding") {
                    checkStatusFails(StatusEventDTO.SENDT, StatusEventDTO.APEN, erAvvist = true)
                }
                test("Skal ikke kunne avbryte en avvist sykmelding") {
                    checkStatusFails(
                        StatusEventDTO.AVBRUTT,
                        StatusEventDTO.APEN,
                        erAvvist = true,
                    )
                }
            }

            context("Test statusendring for egenmeldinger") {
                test("Skal kunne bekrefte en APEN egenmelding") {
                    checkStatusOk(
                        StatusEventDTO.BEKREFTET,
                        StatusEventDTO.APEN,
                        erEgenmeldt = true,
                    )
                }
                test("Skal ikke kunne gjenåpne en bekreftet egenmelding") {
                    checkStatusFails(
                        StatusEventDTO.APEN,
                        StatusEventDTO.BEKREFTET,
                        erEgenmeldt = true,
                    )
                }
                test("Skal ikke kunne sende en egenmelding") {
                    checkStatusFails(
                        StatusEventDTO.SENDT,
                        StatusEventDTO.APEN,
                        erEgenmeldt = true,
                    )
                }
                test("Skal kunne avbryte en egenmelding") {
                    checkStatusOk(
                        StatusEventDTO.AVBRUTT,
                        StatusEventDTO.APEN,
                        erEgenmeldt = true,
                    )
                }
                test("Skal ikke kunne gjenåpne en avbrutt egenmelding") {
                    checkStatusFails(
                        StatusEventDTO.APEN,
                        StatusEventDTO.AVBRUTT,
                        erEgenmeldt = true,
                    )
                }
            }
            context("Bruker går fra arbeidstaker til arbeidsledig") {
                test("Happy-case") {
                    val tidligereSykmelding =
                        opprettSykmelding(
                            fom = 1.januar(2023),
                            tom = 15.januar(2023),
                            orgnummer = "orgnummer",
                            status = "SENDT",
                        )
                    val nySykmelding =
                        opprettSykmelding(
                            fom = 16.januar(2023),
                            tom = 31.januar(2023),
                            status = "APEN",
                        )
                    coEvery { sykmeldingService.getSykmeldinger(any()) } returns
                        listOf(
                            tidligereSykmelding,
                            nySykmelding,
                        )

                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns nySykmelding

                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )
                    sykmeldingStatusService.createSendtStatus(
                        opprettBekreftetSykmeldingUserEvent(arbeidssituasjon = ARBEIDSLEDIG),
                        sykmeldingId,
                        fnr,
                    )
                    val tidligereArbeidsgiver =
                        TidligereArbeidsgiverKafkaDTO("orgNavn", "orgnummer", "1")
                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match { it.tidligereArbeidsgiver == tidligereArbeidsgiver },
                            any(),
                            any(),
                        )
                    }
                }
                test("bekreftet kant til kant med bekreftet") {
                    val tidligereSykmelding =
                        opprettSykmelding(
                            fom = 1.januar(2023),
                            tom = 15.januar(2023),
                            status = "BEKREFTET",
                        )
                    val nySykmelding =
                        opprettSykmelding(
                            fom = 16.januar(2023),
                            tom = 31.januar(2023),
                            status = "APEN",
                        )
                    coEvery { sykmeldingService.getSykmeldinger(any()) } returns
                        listOf(
                            tidligereSykmelding,
                            nySykmelding,
                        )

                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns nySykmelding

                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )
                    sykmeldingStatusService.createSendtStatus(
                        opprettBekreftetSykmeldingUserEvent(arbeidssituasjon = ARBEIDSLEDIG),
                        sykmeldingId,
                        fnr,
                    )
                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match { it.tidligereArbeidsgiver == null },
                            any(),
                            any(),
                        )
                    }
                }
                test("ikke kant i kant sykmelding") {
                    val tidligereSykmelding =
                        opprettSykmelding(fom = 1.januar(2023), tom = 15.januar(2023))
                    val nySykmelding =
                        opprettSykmelding(
                            fom = 17.januar(2023),
                            tom = 31.januar(2023),
                            status = "APEN",
                        )

                    coEvery { sykmeldingService.getSykmeldinger(any()) } returns
                        listOf(
                            tidligereSykmelding,
                            nySykmelding,
                        )
                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns nySykmelding

                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )
                    sykmeldingStatusService.createSendtStatus(
                        opprettBekreftetSykmeldingUserEvent(arbeidssituasjon = ARBEIDSLEDIG),
                        sykmeldingId,
                        fnr,
                    )

                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match { it.tidligereArbeidsgiver == null },
                            any(),
                            any(),
                        )
                    }
                }

                test("kant til kant men flere arbeidsgivere") {
                    val tidligereSykmeldingArbeidsgiver1 =
                        opprettSykmelding(
                            fom = 1.januar(2023),
                            tom = 15.januar(2023),
                            "orgnummer1",
                            status = "SENDT",
                        )
                    val tidligereSykmeldingArbeidsgiver2 =
                        opprettSykmelding(
                            fom = 1.januar(2023),
                            tom = 15.januar(2023),
                            "orgnummer2",
                            status = "SENDT",
                        )
                    val nySykmelding =
                        opprettSykmelding(
                            fom = 16.januar(2023),
                            tom = 31.januar(2023),
                            status = "APEN",
                        )

                    coEvery { sykmeldingService.getSykmeldinger(any()) } returns
                        listOf(
                            tidligereSykmeldingArbeidsgiver1,
                            tidligereSykmeldingArbeidsgiver2,
                        )

                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns nySykmelding

                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )
                    sykmeldingStatusService.createSendtStatus(
                        opprettBekreftetSykmeldingUserEvent(),
                        sykmeldingId,
                        fnr,
                    )

                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match { it.tidligereArbeidsgiver == null },
                            any(),
                            any(),
                        )
                    }
                }

                test("kant til kant fredag og mandag") {
                    val tidligereSykmelding =
                        opprettSykmelding(
                            fom = 1.januar(2023),
                            tom = 6.januar(2023),
                            orgnummer = "orgnummer",
                            status = "SENDT",
                        )
                    val nySykmelding =
                        opprettSykmelding(
                            fom = 9.januar(2023),
                            tom = 31.januar(2023),
                            status = "APEN",
                        )

                    coEvery { sykmeldingService.getSykmeldinger(any()) } returns
                        listOf(
                            tidligereSykmelding,
                        )

                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns nySykmelding

                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )
                    sykmeldingStatusService.createSendtStatus(
                        opprettBekreftetSykmeldingUserEvent(arbeidssituasjon = ARBEIDSLEDIG),
                        sykmeldingId,
                        fnr,
                    )

                    val tidligereArbeidsgiver =
                        TidligereArbeidsgiverKafkaDTO("orgNavn", "orgnummer", "1")
                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match { it.tidligereArbeidsgiver == tidligereArbeidsgiver },
                            any(),
                            any(),
                        )
                    }
                }

                test("Ikke kant til kant torsdag og mandag") {
                    val tidligereSykmelding =
                        opprettSykmelding(
                            fom = 1.januar(2023),
                            tom = 5.januar(2023),
                            status = "SENDT",
                        )
                    val nySykmelding =
                        opprettSykmelding(
                            fom = 1.januar(2023),
                            tom = 5.januar(2023),
                            status = "APEN",
                        )

                    coEvery { sykmeldingService.getSykmeldinger(any()) } returns
                        listOf(
                            tidligereSykmelding,
                        )

                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns nySykmelding

                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )
                    sykmeldingStatusService.createSendtStatus(
                        opprettBekreftetSykmeldingUserEvent(),
                        sykmeldingId,
                        fnr,
                    )

                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match { it.tidligereArbeidsgiver == null },
                            any(),
                            any(),
                        )
                    }
                }

                test("arbeidstaker til arbeidsledig") {
                    val tidligereSykmelding =
                        opprettSykmelding(
                            fom = 1.januar(2023),
                            tom = 15.januar(2023),
                            orgnummer = "orgnummer",
                            status = "SENDT",
                        )
                    val nySykmelding =
                        opprettSykmelding(
                            fom = 16.januar(2023),
                            tom = 31.januar(2023),
                            status = "APEN",
                        )

                    coEvery { sykmeldingService.getSykmeldinger(any()) } returns
                        listOf(
                            tidligereSykmelding,
                            nySykmelding,
                        )

                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns nySykmelding

                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )
                    sykmeldingStatusService.createSendtStatus(
                        opprettBekreftetSykmeldingUserEvent(arbeidssituasjon = ARBEIDSLEDIG),
                        sykmeldingId,
                        fnr,
                    )

                    val tidligereArbeidsgiver =
                        TidligereArbeidsgiverKafkaDTO("orgNavn", "orgnummer", "1")
                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match { it.tidligereArbeidsgiver == tidligereArbeidsgiver },
                            any(),
                            any(),
                        )
                    }
                }

                test("arbeidstaker til frilanser") {
                    val tidligereSykmelding =
                        opprettSykmelding(
                            fom = 1.januar(2023),
                            tom = 15.januar(2023),
                            status = "SENDT",
                        )
                    val nySykmelding =
                        opprettSykmelding(
                            fom = 16.januar(2023),
                            tom = 31.januar(2023),
                            status = "APEN",
                        )

                    coEvery { sykmeldingService.getSykmeldinger(any()) } returns
                        listOf(
                            tidligereSykmelding,
                            nySykmelding,
                        )

                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns nySykmelding

                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )
                    sykmeldingStatusService.createSendtStatus(
                        opprettBekreftetSykmeldingUserEvent(arbeidssituasjon = FRILANSER),
                        sykmeldingId,
                        fnr,
                    )

                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match { it.tidligereArbeidsgiver == null },
                            any(),
                            any(),
                        )
                    }
                    coVerify(exactly = 0) { sykmeldingService.getSykmeldinger(any()) }
                }

                test("En bekreftet sykmelding kant til kant med en bekreftet sykmelding") {
                    val arbeidstakerSykmelding =
                        opprettSykmelding(
                            fom = 1.januar(2023),
                            tom = 15.januar(2023),
                            status = "SENDT",
                            orgnummer = "orgnummer",
                        )
                    val arbeidsledigSykmelding1 =
                        opprettSykmelding(
                            fom = 16.januar(2023),
                            tom = 20.januar(2023),
                            status = "BEKREFTET",
                            tidligereArbeidsgiver =
                                TidligereArbeidsgiverDTO(
                                    "orgNavn",
                                    orgnummer = "orgnummer",
                                    sykmeldingsId = "1",
                                ),
                        )
                    val arbeidsledigSykmelding2 =
                        opprettSykmelding(
                            fom = 21.januar(2023),
                            tom = 31.januar(2023),
                            status = "APEN",
                        )
                    coEvery { sykmeldingService.getSykmeldinger(any()) } returns
                        listOf(
                            arbeidstakerSykmelding,
                            arbeidsledigSykmelding1,
                            arbeidsledigSykmelding2,
                        )

                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns arbeidsledigSykmelding2

                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )
                    sykmeldingStatusService.createSendtStatus(
                        opprettBekreftetSykmeldingUserEvent(arbeidssituasjon = ARBEIDSLEDIG),
                        sykmeldingId,
                        fnr,
                    )

                    val tidligereArbeidsgiver =
                        TidligereArbeidsgiverKafkaDTO("orgNavn", "orgnummer", "1")
                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match { it.tidligereArbeidsgiver == tidligereArbeidsgiver },
                            any(),
                            any(),
                        )
                    }
                }

                test("direkte overlappende sykmelding") {
                    val tidligereSykmelding =
                        opprettSykmelding(
                            fom = 1.januar(2023),
                            tom = 31.januar(2023),
                            status = "SENDT",
                            tidligereArbeidsgiver =
                                TidligereArbeidsgiverDTO(
                                    "orgNavn",
                                    orgnummer = "orgnummer",
                                    sykmeldingsId = "1",
                                ),
                        )
                    val nySykmelding =
                        opprettSykmelding(
                            fom = 1.januar(2023),
                            tom = 31.januar(2023),
                            status = "APEN",
                        )
                    coEvery { sykmeldingService.getSykmeldinger(any()) } returns
                        listOf(
                            tidligereSykmelding,
                            nySykmelding,
                        )

                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns nySykmelding

                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )
                    sykmeldingStatusService.createSendtStatus(
                        opprettBekreftetSykmeldingUserEvent(arbeidssituasjon = ARBEIDSLEDIG),
                        sykmeldingId,
                        fnr,
                    )

                    val tidligereArbeidsgiver =
                        TidligereArbeidsgiverKafkaDTO("orgNavn", "orgnummer", "1")
                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match { it.tidligereArbeidsgiver == tidligereArbeidsgiver },
                            any(),
                            any(),
                        )
                    }
                }

                test("indirekte overlappende sykmelding") {
                    val tidligereSykmelding =
                        opprettSykmelding(
                            fom = 1.januar(2023),
                            tom = 31.januar(2023),
                            status = "SENDT",
                            tidligereArbeidsgiver =
                                TidligereArbeidsgiverDTO(
                                    "orgNavn",
                                    orgnummer = "orgnummer",
                                    sykmeldingsId = "1",
                                ),
                        )
                    val nySykmelding =
                        opprettSykmelding(
                            fom = 15.januar(2023),
                            tom = 15.februar(2023),
                            status = "APEN",
                        )
                    coEvery { sykmeldingService.getSykmeldinger(any()) } returns
                        listOf(
                            tidligereSykmelding,
                            nySykmelding,
                        )

                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns nySykmelding

                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )
                    sykmeldingStatusService.createSendtStatus(
                        opprettBekreftetSykmeldingUserEvent(arbeidssituasjon = ARBEIDSLEDIG),
                        sykmeldingId,
                        fnr,
                    )

                    val tidligereArbeidsgiver =
                        TidligereArbeidsgiverKafkaDTO("orgNavn", "orgnummer", "1")
                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match { it.tidligereArbeidsgiver == tidligereArbeidsgiver },
                            any(),
                            any(),
                        )
                    }
                }

                test("overlappende sykmelding inni en annen sykmelding") {
                    val tidligereSykmelding =
                        opprettSykmelding(
                            fom = 1.januar(2023),
                            tom = 31.januar(2023),
                            status = "SENDT",
                            tidligereArbeidsgiver =
                                TidligereArbeidsgiverDTO(
                                    "orgNavn",
                                    orgnummer = "orgnummer",
                                    sykmeldingsId = "1",
                                ),
                        )
                    val nySykmelding =
                        opprettSykmelding(
                            fom = 5.januar(2023),
                            tom = 20.januar(2023),
                            status = "APEN",
                        )
                    coEvery { sykmeldingService.getSykmeldinger(any()) } returns
                        listOf(
                            tidligereSykmelding,
                            nySykmelding,
                        )

                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns nySykmelding

                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )
                    sykmeldingStatusService.createSendtStatus(
                        opprettBekreftetSykmeldingUserEvent(arbeidssituasjon = ARBEIDSLEDIG),
                        sykmeldingId,
                        fnr,
                    )

                    val tidligereArbeidsgiver =
                        TidligereArbeidsgiverKafkaDTO("orgNavn", "orgnummer", "1")
                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match { it.tidligereArbeidsgiver == tidligereArbeidsgiver },
                            any(),
                            any(),
                        )
                    }
                }

                test("Sykmelding frem i tid skal ikke overlappe") {
                    val tidligereSykmelding =
                        opprettSykmelding(
                            fom = 28.januar(2023),
                            tom = 31.januar(2023),
                            status = "SENDT",
                            tidligereArbeidsgiver =
                                TidligereArbeidsgiverDTO(
                                    "orgNavn",
                                    orgnummer = "orgnummer",
                                    sykmeldingsId = "1",
                                ),
                        )
                    val nySykmelding =
                        opprettSykmelding(
                            fom = 5.januar(2023),
                            tom = 20.januar(2023),
                            status = "APEN",
                        )
                    coEvery { sykmeldingService.getSykmeldinger(any()) } returns
                        listOf(
                            tidligereSykmelding,
                            nySykmelding,
                        )

                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns nySykmelding

                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )
                    sykmeldingStatusService.createSendtStatus(
                        opprettBekreftetSykmeldingUserEvent(arbeidssituasjon = ARBEIDSLEDIG),
                        sykmeldingId,
                        fnr,
                    )

                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match { it.tidligereArbeidsgiver == null },
                            any(),
                            any(),
                        )
                    }
                }
                test("Ny sykmelding starter før tidligere") {
                    val tidligereSykmelding =
                        opprettSykmelding(
                            fom = 28.januar(2023),
                            tom = 31.januar(2023),
                            status = "SENDT",
                            tidligereArbeidsgiver =
                                TidligereArbeidsgiverDTO(
                                    "orgNavn",
                                    orgnummer = "orgnummer",
                                    sykmeldingsId = "1",
                                ),
                        )
                    val nySykmelding =
                        opprettSykmelding(
                            fom = 27.januar(2023),
                            tom = 30.januar(2023),
                            status = "APEN",
                        )
                    coEvery { sykmeldingService.getSykmeldinger(any()) } returns
                        listOf(
                            tidligereSykmelding,
                            nySykmelding,
                        )

                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns nySykmelding

                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )
                    sykmeldingStatusService.createSendtStatus(
                        opprettBekreftetSykmeldingUserEvent(arbeidssituasjon = ARBEIDSLEDIG),
                        sykmeldingId,
                        fnr,
                    )

                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match { it.tidligereArbeidsgiver == null },
                            any(),
                            any(),
                        )
                    }
                }
                test("Ny sykmelding starter etter") {
                    val tidligereSykmelding =
                        opprettSykmelding(
                            fom = 28.januar(2023),
                            tom = 31.januar(2023),
                            status = "SENDT",
                            tidligereArbeidsgiver =
                                TidligereArbeidsgiverDTO(
                                    "orgNavn",
                                    orgnummer = "orgnummer",
                                    sykmeldingsId = "1",
                                ),
                        )
                    val nySykmelding =
                        opprettSykmelding(
                            fom = 2.februar(2023),
                            tom = 5.februar(2023),
                            status = "APEN",
                        )
                    coEvery { sykmeldingService.getSykmeldinger(any()) } returns
                        listOf(
                            tidligereSykmelding,
                            nySykmelding,
                        )

                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns nySykmelding

                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )
                    sykmeldingStatusService.createSendtStatus(
                        opprettBekreftetSykmeldingUserEvent(arbeidssituasjon = ARBEIDSLEDIG),
                        sykmeldingId,
                        fnr,
                    )

                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match { it.tidligereArbeidsgiver == null },
                            any(),
                            any(),
                        )
                    }
                }
                test("Ny Sykmelding starter før og slutter etter") {
                    val tidligereSykmelding =
                        opprettSykmelding(
                            fom = 28.januar(2023),
                            tom = 31.januar(2023),
                            status = "SENDT",
                            tidligereArbeidsgiver =
                                TidligereArbeidsgiverDTO(
                                    "orgNavn",
                                    orgnummer = "orgnummer",
                                    sykmeldingsId = "1",
                                ),
                        )
                    val nySykmelding =
                        opprettSykmelding(
                            fom = 5.januar(2023),
                            tom = 3.februar(2023),
                            status = "APEN",
                        )
                    coEvery { sykmeldingService.getSykmeldinger(any()) } returns
                        listOf(
                            tidligereSykmelding,
                            nySykmelding,
                        )

                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns nySykmelding

                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )
                    sykmeldingStatusService.createSendtStatus(
                        opprettBekreftetSykmeldingUserEvent(arbeidssituasjon = ARBEIDSLEDIG),
                        sykmeldingId,
                        fnr,
                    )

                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match { it.tidligereArbeidsgiver == null },
                            any(),
                            any(),
                        )
                    }
                }
            }
            context("Bruker går fra arbeidstaker til annet") {
                test("skal ha tidligere arbeidsgiver") {
                    val tidligereSykmelding =
                        opprettSykmelding(
                            fom = 1.januar(2023),
                            tom = 15.januar(2023),
                            orgnummer = "orgnummer",
                            status = "SENDT",
                        )
                    val nySykmelding =
                        opprettSykmelding(
                            fom = 16.januar(2023),
                            tom = 31.januar(2023),
                            status = "APEN",
                        )
                    coEvery { sykmeldingService.getSykmeldinger(any()) } returns
                        listOf(
                            tidligereSykmelding,
                            nySykmelding,
                        )

                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns nySykmelding

                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )
                    sykmeldingStatusService.createSendtStatus(
                        opprettBekreftetSykmeldingUserEvent(arbeidssituasjon = ANNET),
                        sykmeldingId,
                        fnr,
                    )
                    val tidligereArbeidsgiver =
                        TidligereArbeidsgiverKafkaDTO("orgNavn", "orgnummer", "1")
                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match { it.tidligereArbeidsgiver == tidligereArbeidsgiver },
                            any(),
                            any(),
                        )
                    }
                }
                test("skal ikke ha tidligere arbeidsgiver ved andre statuser") {
                    val tidligereSykmelding =
                        opprettSykmelding(
                            fom = 1.januar(2023),
                            tom = 15.januar(2023),
                            orgnummer = "org",
                            status = "SENDT",
                        )
                    val nySykmelding =
                        opprettSykmelding(
                            fom = 16.januar(2023),
                            tom = 31.januar(2023),
                            status = "APEN",
                        )
                    coEvery { sykmeldingService.getSykmeldinger(any()) } returns
                        listOf(
                            tidligereSykmelding,
                            nySykmelding,
                        )

                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns nySykmelding

                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )
                    sykmeldingStatusService.createSendtStatus(
                        opprettBekreftetSykmeldingUserEvent(arbeidssituasjon = FRILANSER),
                        sykmeldingId,
                        fnr,
                    )

                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match { it.tidligereArbeidsgiver == null },
                            any(),
                            any(),
                        )
                    }
                }
            }
            context("Forlengelse av bekreftet sykmelding") {
                test("En bekreftet sykmelding kant til kant med en bekreftet sykmelding") {
                    val arbeidstakerSykmelding =
                        opprettSykmelding(
                            fom = 1.januar(2023),
                            tom = 31.januar(2023),
                            status = "SENDT",
                            orgnummer = "orgnummer",
                        )
                    val arbeidsledigSykmelding1 =
                        opprettSykmelding(
                            fom = 1.februar(2023),
                            tom = 28.februar(2023),
                            status = "BEKREFTET",
                            tidligereArbeidsgiver =
                                TidligereArbeidsgiverDTO(
                                    "orgNavn",
                                    orgnummer = "orgnummer",
                                    sykmeldingsId = "1",
                                ),
                        )
                    val arbeidsledigSykmelding2 =
                        opprettSykmelding(
                            fom = 1.mars(2023),
                            tom = 31.mars(2023),
                            status = "APEN",
                        )
                    coEvery { sykmeldingService.getSykmeldinger(any()) } returns
                        listOf(
                            arbeidstakerSykmelding,
                            arbeidsledigSykmelding1,
                            arbeidsledigSykmelding2,
                        )

                    coEvery {
                        sykmeldingService.getSykmelding(
                            any(),
                            any(),
                        )
                    } returns arbeidsledigSykmelding2

                    coEvery {
                        sykmeldingStatusDb.getLatestStatus(
                            any(),
                            any(),
                        )
                    } returns
                        SykmeldingStatusEventDTO(
                            statusEvent = StatusEventDTO.APEN,
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                            erAvvist = true,
                        )
                    sykmeldingStatusService.createSendtStatus(
                        opprettBekreftetSykmeldingUserEvent(arbeidssituasjon = ARBEIDSLEDIG),
                        sykmeldingId,
                        fnr,
                    )

                    val tidligereArbeidsgiver =
                        TidligereArbeidsgiverKafkaDTO("orgNavn", "orgnummer", "1")
                    coVerify(exactly = 1) {
                        sykmeldingStatusDb.insertStatus(
                            match { it.tidligereArbeidsgiver == tidligereArbeidsgiver },
                            any(),
                            any(),
                        )
                    }
                }
            }
        },
    )
