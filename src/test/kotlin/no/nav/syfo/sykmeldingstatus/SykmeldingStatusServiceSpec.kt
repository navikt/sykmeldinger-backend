package no.nav.syfo.sykmeldingstatus

import io.ktor.server.testing.*
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.sykmeldingstatus.TestHelper.Companion.februar
import no.nav.syfo.sykmeldingstatus.TestHelper.Companion.januar
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v2.Arbeidssituasjon.ANNET
import no.nav.syfo.sykmeldingstatus.api.v2.Arbeidssituasjon.ARBEIDSTAKER
import no.nav.syfo.sykmeldingstatus.api.v2.Arbeidssituasjon.FRILANSER
import no.nav.syfo.sykmeldingstatus.api.v2.JaEllerNei
import no.nav.syfo.sykmeldingstatus.api.v2.SporsmalSvar
import no.nav.syfo.sykmeldingstatus.api.v2.SykmeldingFormResponse
import no.nav.syfo.sykmeldingstatus.db.SykmeldingStatusDb
import no.nav.syfo.sykmeldingstatus.exception.InvalidSykmeldingStatusException
import no.nav.syfo.sykmeldingstatus.exception.SykmeldingStatusNotFoundException
import no.nav.syfo.sykmeldingstatus.kafka.model.ShortNameKafkaDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SykmeldingStatusServiceSpec {
    val sykmeldingId = "id"
    val fnr = "fnr"

    val sykmeldingStatusKafkaProducer = mockkClass(SykmeldingStatusKafkaProducer::class)

    val arbeidsgiverService = mockkClass(ArbeidsgiverService::class)
    val sykmeldingStatusDb = mockkClass(SykmeldingStatusDb::class)
    val sykmeldingStatusService =
        SykmeldingStatusService(
            sykmeldingStatusKafkaProducer,
            arbeidsgiverService,
            sykmeldingStatusDb,
        )

    @BeforeEach
    fun init() {
        clearAllMocks()
        coEvery { sykmeldingStatusDb.insertStatus(any(), any(), any()) } just Runs
        coEvery { sykmeldingStatusKafkaProducer.send(any(), any()) } just Runs
        coEvery {
            sykmeldingStatusDb.getLatestStatus(
                any(),
                any(),
            )
        } throws SykmeldingStatusNotFoundException("not found")
        coEvery { sykmeldingStatusDb.getSykmeldingWithStatus(any()) } returns emptyList()
    }

    @Nested
    @DisplayName("Hent nyeste status")
    inner class HentNyesteStatus {
        @Test
        fun `Skal hente sendt status fra db`() = testApplication {
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

        @Test
        fun `Ikke tilgang til sykmeldingstatus`() = testApplication {
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

    @Nested
    @DisplayName("Test av BEKREFT for sluttbruker")
    inner class TestAvBekreftForSluttbruker {
        @Test
        fun `Happy-case`() = testApplication {
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

            coEvery { sykmeldingStatusDb.getSykmeldingWithStatus(any()) } returns
                listOf(
                    opprettSykmelding(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusDays(1),
                    ),
                )

            sykmeldingStatusService.createStatus(
                opprettBekreftetSykmeldingUserEvent(),
                sykmeldingId,
                fnr,
            )
            coVerify(exactly = 1) { sykmeldingStatusDb.insertStatus(any(), any(), any()) }
        }

        @Test
        fun `Oppdaterer ikke status hvis bruker ikke har tilgang til sykmelding`() =
            testApplication {
                coEvery {
                    sykmeldingStatusDb.getLatestStatus(
                        any(),
                        any(),
                    )
                } throws SykmeldingStatusNotFoundException("Ingen tilgang")

                assertFailsWith<SykmeldingStatusNotFoundException> {
                    sykmeldingStatusService.createStatus(
                        opprettBekreftetSykmeldingUserEvent(),
                        sykmeldingId,
                        fnr,
                    )
                }

                coVerify { sykmeldingStatusDb.getLatestStatus(any(), any()) }
                coVerify(exactly = 0) { sykmeldingStatusDb.insertStatus(any(), any(), any()) }
            }
    }

    @Nested
    @DisplayName("Test bekrefting av avvist sykmelding")
    inner class TestBekreftingAvAvvistSykmelding {
        @Test
        fun `Får bekrefte avvist sykmelding med status APEN`() = testApplication {
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

        @Test
        fun `Får ikke bekrefte avvist sykmelding med status BEKREFTET`() = testApplication {
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

        @Test
        fun `Får ikke bekrefte sykmelding som ikke er avvist`() = testApplication {
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

    @Nested
    @DisplayName("Test user event")
    inner class TestUserEvent {
        @Test
        fun `Test SEND user event`() = testApplication {
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
                sykmeldingStatusDb.getSykmeldingWithStatus(
                    any(),
                )
            } returns
                listOf(
                    opprettSykmelding(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusDays(1),
                    ),
                )
            coEvery { arbeidsgiverService.getArbeidsgivere(any(), any()) } returns
                listOf(
                    Arbeidsgiverinfo(
                        orgnummer = "123456789",
                        juridiskOrgnummer = "",
                        navn = "",
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
                    arbeidsledig = null,
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
                    fisker = null,
                )

            sykmeldingStatusService.createStatus(
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

        @Test
        fun `test SENDT user event - Siste status er sendt`() = testApplication {
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
                    arbeidsledig = null,
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
                    fisker = null,
                )

            assertFailsWith(InvalidSykmeldingStatusException::class) {
                runBlocking {
                    sykmeldingStatusService.createStatus(
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

        @Test
        fun `Test SEND user event - finner ikke riktig arbeidsgiver`() = testApplication {
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
                    arbeidsledig = null,
                    harBruktEgenmelding = null,
                    egenmeldingsperioder = null,
                    harForsikring = null,
                    harBruktEgenmeldingsdager = null,
                    egenmeldingsdager = null,
                    fisker = null,
                )

            assertFailsWith(InvalidSykmeldingStatusException::class) {
                runBlocking {
                    sykmeldingStatusService.createStatus(
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

        @Test
        fun `Test BEKREFT user event`() = testApplication {
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
                    arbeidsledig = null,
                    arbeidsgiverOrgnummer = null,
                    riktigNarmesteLeder = null,
                    harBruktEgenmelding = null,
                    egenmeldingsperioder = null,
                    harForsikring = null,
                    harBruktEgenmeldingsdager = null,
                    egenmeldingsdager = null,
                    fisker = null,
                )

            sykmeldingStatusService.createStatus(
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

        @Test
        fun `Setter nyNarmesteLeder-spørsmal til NEI dersom Arbeidsgforholder er inaktivt`() =
            testApplication {
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
                        arbeidsledig = null,
                        riktigNarmesteLeder = null,
                        harBruktEgenmelding = null,
                        egenmeldingsperioder = null,
                        harForsikring = null,
                        harBruktEgenmeldingsdager = null,
                        egenmeldingsdager = null,
                        fisker = null,
                    )

                val expected = slot<SykmeldingStatusKafkaEventDTO>()

                sykmeldingStatusService.createStatus(
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

    @Nested
    @DisplayName("Test SENDT status")
    inner class TestSendtStatus {
        @Test
        fun `Skal kunne sende sykmelding med status APEN`() = testApplication {
            checkStatusOk(
                newStatus = StatusEventDTO.SENDT,
                oldStatus = StatusEventDTO.APEN,
            )
        }

        @Test
        fun `Skal ikke kunne SENDE en allerede SENDT Sykmelding`() = testApplication {
            checkStatusFails(
                newStatus = StatusEventDTO.SENDT,
                oldStatus = StatusEventDTO.SENDT,
            )
        }

        @Test
        fun `Skal kunne SENDE en BEKREFTET sykmelding`() = testApplication {
            checkStatusOk(
                newStatus = StatusEventDTO.SENDT,
                oldStatus = StatusEventDTO.BEKREFTET,
            )
        }

        @Test
        fun `skal ikke kunne SENDE en UTGÅTT sykmelding`() = testApplication {
            checkStatusFails(
                newStatus = StatusEventDTO.SENDT,
                oldStatus = StatusEventDTO.UTGATT,
            )
        }

        @Test
        fun `SKal kunne SENDE en AVBRUTT sykmelding`() = testApplication {
            checkStatusOk(
                newStatus = StatusEventDTO.SENDT,
                oldStatus = StatusEventDTO.AVBRUTT,
            )
        }
    }

    @Nested
    @DisplayName("Test BEKREFT status")
    inner class TestBekreftStatus {
        @Test
        fun `Bruker skal få BEKREFTET sykmelding med status APEN`() = testApplication {
            checkStatusOk(StatusEventDTO.BEKREFTET, StatusEventDTO.APEN)
        }

        @Test
        fun `Bruker skal få BEKREFTET en sykmelding med status BEKREFTET`() = testApplication {
            checkStatusOk(StatusEventDTO.BEKREFTET, StatusEventDTO.BEKREFTET)
        }

        @Test
        fun `Bruker skal få bekrefte sin egen sykmelding med status AVBRUTT`() = testApplication {
            checkStatusOk(
                newStatus = StatusEventDTO.BEKREFTET,
                oldStatus = StatusEventDTO.AVBRUTT,
            )
        }

        @Test
        fun `Skal ikke kunne BEKREFTE når siste status er SENDT`() = testApplication {
            checkStatusFails(
                newStatus = StatusEventDTO.BEKREFTET,
                oldStatus = StatusEventDTO.SENDT,
            )
        }

        @Test
        fun `Skal ikke kunne bekrefte når siste status er UTGATT`() = testApplication {
            checkStatusFails(
                newStatus = StatusEventDTO.BEKREFTET,
                oldStatus = StatusEventDTO.UTGATT,
            )
        }
    }

    @Nested
    @DisplayName("Test APEN status")
    inner class TestApenStatus {
        @Test
        fun `Bruker skal kunne APNE en sykmelding med statsu BEKREFTET`() = testApplication {
            checkStatusOk(StatusEventDTO.APEN, StatusEventDTO.BEKREFTET)
        }

        @Test
        fun `Bruker skal kunne APNE en sykmeldimg med Status APEN`() = testApplication {
            checkStatusOk(StatusEventDTO.APEN, StatusEventDTO.APEN)
        }

        @Test
        fun `Skal kunne endre status til APEN fra AVBRUTT`() = testApplication {
            checkStatusOk(StatusEventDTO.APEN, StatusEventDTO.AVBRUTT)
        }

        @Test
        fun `Skal ikke kunne endre status til APEN fra UTGATT`() = testApplication {
            checkStatusFails(StatusEventDTO.APEN, StatusEventDTO.UTGATT)
        }

        @Test
        fun `Skal ikke kunne endre status til APEN fra SENDT`() = testApplication {
            checkStatusFails(StatusEventDTO.APEN, StatusEventDTO.SENDT)
        }
    }

    @Nested
    @DisplayName("Test AVBRUTT status")
    inner class TestAvbruttStatus {
        @Test
        fun `Skal ikke kunne endre status til AVBRUTT om sykmeldingen er sendt`() =
            testApplication {
                checkStatusFails(StatusEventDTO.AVBRUTT, StatusEventDTO.SENDT)
            }

        @Test
        fun `Skal kunne avbryte en APEN sykmelding`() = testApplication {
            checkStatusOk(StatusEventDTO.AVBRUTT, StatusEventDTO.APEN)
        }

        @Test
        fun `Skal kunne avbryte en BEKREFTET sykmelding`() = testApplication {
            checkStatusOk(StatusEventDTO.AVBRUTT, StatusEventDTO.BEKREFTET)
        }

        @Test
        fun `Skal kunne avbryte en allerede AVBRUTT sykmelding`() = testApplication {
            checkStatusOk(StatusEventDTO.AVBRUTT, StatusEventDTO.AVBRUTT)
        }

        @Test
        fun `Skal kunne avbryte en UTGATT sykmelding`() = testApplication {
            checkStatusOk(StatusEventDTO.AVBRUTT, StatusEventDTO.UTGATT)
        }
    }

    @Nested
    @DisplayName("Test statusendring for avviste sykmeldinger")
    inner class TestStatusendringForAvvisteSykmeldinger {
        @Test
        fun `Skal kunne bekrefte en APEN avvist sykmelding`() = testApplication {
            checkStatusOk(
                StatusEventDTO.BEKREFTET,
                StatusEventDTO.APEN,
                erAvvist = true,
            )
        }

        @Test
        fun `Skal ikke kunne gjenåpne en bekreftet avvist sykmelding`() = testApplication {
            checkStatusFails(
                StatusEventDTO.APEN,
                StatusEventDTO.BEKREFTET,
                erAvvist = true,
            )
        }

        @Test
        fun `Skal ikke kunne sende en avvist sykmelding`() = testApplication {
            checkStatusFails(StatusEventDTO.SENDT, StatusEventDTO.APEN, erAvvist = true)
        }

        @Test
        fun `Skal ikke kunne avbryte en avvist sykmelding`() = testApplication {
            checkStatusFails(
                StatusEventDTO.AVBRUTT,
                StatusEventDTO.APEN,
                erAvvist = true,
            )
        }
    }

    @Nested
    @DisplayName("Test statusendring for egenmeldinger")
    inner class TestStatusendringForEgenmeldinger {
        @Test
        fun `Skal kunne bekrefte en APEN egenmelding`() = testApplication {
            checkStatusOk(
                StatusEventDTO.BEKREFTET,
                StatusEventDTO.APEN,
                erEgenmeldt = true,
            )
        }

        @Test
        fun `Skal ikke kunne gjenåpne en bekreftet egenmelding`() = testApplication {
            checkStatusFails(
                StatusEventDTO.APEN,
                StatusEventDTO.BEKREFTET,
                erEgenmeldt = true,
            )
        }

        @Test
        fun `Skal ikke kunne sende en egenmelding`() = testApplication {
            checkStatusFails(
                StatusEventDTO.SENDT,
                StatusEventDTO.APEN,
                erEgenmeldt = true,
            )
        }

        @Test
        fun `Skal kunne avbryte en egenmelding`() = testApplication {
            checkStatusOk(
                StatusEventDTO.AVBRUTT,
                StatusEventDTO.APEN,
                erEgenmeldt = true,
            )
        }

        @Test
        fun `Skal ikke kunne gjenåpne en avbrutt egenmelding`() = testApplication {
            checkStatusFails(
                StatusEventDTO.APEN,
                StatusEventDTO.AVBRUTT,
                erEgenmeldt = true,
            )
        }
    }

    @Nested
    @DisplayName("Bruker går fra arbeidstaker til arbeidsledig")
    inner class BrukerGarFraArbeidstakerTilArbeidsledig {
        @Test
        fun `Happy-case`() = testApplication {
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
                    sykmeldingId = sykmeldingId,
                )
            coEvery { sykmeldingStatusDb.getSykmeldingWithStatus(any()) } returns
                listOf(
                    tidligereSykmelding,
                    nySykmelding,
                )

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
            val tidligereArbeidsgivere =
                sykmeldingStatusService.finnTidligereArbeidsgivere(
                    fnr,
                    sykmeldingId,
                )
            assertNotNull(tidligereArbeidsgivere)
            assertEquals(1, tidligereArbeidsgivere.size)
        }

        @Test
        fun `ingen relevante`() = testApplication {
            val nySykmelding =
                opprettSykmelding(
                    fom = 16.januar(2023),
                    tom = 31.januar(2023),
                    status = "APEN",
                    sykmeldingId = sykmeldingId,
                )
            coEvery { sykmeldingStatusDb.getSykmeldingWithStatus(any()) } returns
                listOf(
                    nySykmelding,
                )

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
            val tidligereArbeidsgivere =
                sykmeldingStatusService.finnTidligereArbeidsgivere(
                    fnr,
                    sykmeldingId,
                )
            assertNotNull(tidligereArbeidsgivere)
            assertEquals(0, tidligereArbeidsgivere.size)
        }

        @Test
        fun `bekreftet kant til kant med bekreftet`() = testApplication {
            val tidligereSykmelding =
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 15.januar(2023),
                    status = "BEKREFTET",
                    tidligereArbeidsgiver =
                        TidligereArbeidsgiverDTO(
                            orgnummer = "orgnummer",
                            orgNavn = "navn",
                            sykmeldingsId = "1"
                        )
                )
            val nySykmelding =
                opprettSykmelding(
                    fom = 16.januar(2023),
                    tom = 31.januar(2023),
                    status = "APEN",
                    sykmeldingId = sykmeldingId,
                )
            coEvery { sykmeldingStatusDb.getSykmeldingWithStatus(any()) } returns
                listOf(
                    tidligereSykmelding,
                    nySykmelding,
                )

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
            val tidligereArbeidsgivere =
                sykmeldingStatusService.finnTidligereArbeidsgivere(
                    fnr,
                    sykmeldingId,
                )
            assertNotNull(tidligereArbeidsgivere)
            assertEquals(1, tidligereArbeidsgivere.size)
        }

        @Test
        fun `ikke kant i kant sykmelding`() = testApplication {
            val tidligereSykmelding = opprettSykmelding(fom = 1.januar(2023), tom = 15.januar(2023))
            val nySykmelding =
                opprettSykmelding(
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    status = "APEN",
                    sykmeldingId = sykmeldingId,
                )

            coEvery { sykmeldingStatusDb.getSykmeldingWithStatus(any()) } returns
                listOf(
                    tidligereSykmelding,
                    nySykmelding,
                )
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
            val tidligereArbeidsgivere =
                sykmeldingStatusService.finnTidligereArbeidsgivere(
                    fnr,
                    sykmeldingId,
                )
            assertTrue(tidligereArbeidsgivere.isEmpty())
        }

        @Test
        fun `kant til kant fredag og mandag`() = testApplication {
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
                    sykmeldingId = sykmeldingId,
                )

            coEvery { sykmeldingStatusDb.getSykmeldingWithStatus(any()) } returns
                listOf(tidligereSykmelding, nySykmelding)

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
            val tidligereArbeidsgivere =
                sykmeldingStatusService.finnTidligereArbeidsgivere(
                    fnr,
                    sykmeldingId,
                )
            assertNotNull(tidligereArbeidsgivere)
            assertEquals(1, tidligereArbeidsgivere.size)
        }

        @Test
        fun `Ikke kant til kant torsdag og mandag`() = testApplication {
            val tidligereSykmelding =
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 5.januar(2023),
                    status = "SENDT",
                )
            val nySykmelding =
                opprettSykmelding(
                    fom = 9.januar(2023),
                    tom = 31.januar(2023),
                    status = "APEN",
                    sykmeldingId = sykmeldingId,
                )

            coEvery { sykmeldingStatusDb.getSykmeldingWithStatus(any()) } returns
                listOf(tidligereSykmelding, nySykmelding)

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

            val tidligereArbeidsgivere =
                sykmeldingStatusService.finnTidligereArbeidsgivere(
                    fnr,
                    sykmeldingId,
                )
            assertNotNull(tidligereArbeidsgivere)
            assertEquals(0, tidligereArbeidsgivere.size)
        }

        @Test
        fun `arbeidstaker til arbeidsledig flere potensielle kandidater`() = testApplication {
            val ag1 =
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 15.januar(2023),
                    orgnummer = "ag1",
                    status = "SENDT",
                )
            val ag2 =
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 15.januar(2023),
                    orgnummer = "ag2",
                    status = "SENDT",
                )
            val nySykmelding =
                opprettSykmelding(
                    fom = 16.januar(2023),
                    tom = 31.januar(2023),
                    status = "APEN",
                    sykmeldingId = sykmeldingId,
                )

            coEvery { sykmeldingStatusDb.getSykmeldingWithStatus(any()) } returns
                listOf(
                    ag1,
                    ag2,
                    nySykmelding,
                )

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
            val tidligereArbeidsgivere =
                sykmeldingStatusService.finnTidligereArbeidsgivere(
                    fnr,
                    sykmeldingId,
                )
            assertNotNull(tidligereArbeidsgivere)
            assertEquals(2, tidligereArbeidsgivere.size)
        }

        @Test
        fun `arbeidstaker til frilanser`() = testApplication {
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
                    sykmeldingId = sykmeldingId,
                )

            coEvery { sykmeldingStatusDb.getSykmeldingWithStatus(any()) } returns
                listOf(
                    tidligereSykmelding,
                    nySykmelding,
                )

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
            sykmeldingStatusService.createStatus(
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
            coVerify(exactly = 0) { sykmeldingStatusDb.getSykmeldingWithStatus(any()) }
        }

        @Test
        fun `en dag etter direkte overlappende sykmelding`() = testApplication {
            val tidligereSykmelding =
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 31.januar(2023),
                    status = "BEKREFTET",
                    tidligereArbeidsgiver =
                        TidligereArbeidsgiverDTO(
                            "orgNavn",
                            orgnummer = "orgnummer",
                            sykmeldingsId = "1",
                        ),
                )
            val nySykmelding =
                opprettSykmelding(
                    fom = 2.januar(2023),
                    tom = 31.januar(2023),
                    status = "APEN",
                    sykmeldingId = sykmeldingId,
                )
            coEvery { sykmeldingStatusDb.getSykmeldingWithStatus(any()) } returns
                listOf(
                    tidligereSykmelding,
                    nySykmelding,
                )

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
            val tidligereArbeidsgivere =
                sykmeldingStatusService.finnTidligereArbeidsgivere(
                    fnr,
                    sykmeldingId,
                )
            assertNotNull(tidligereArbeidsgivere)
            assertEquals(1, tidligereArbeidsgivere.size)
        }

        @Test
        fun `indirekte overlappende sykmelding`() = testApplication {
            val tidligereSykmelding =
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 31.januar(2023),
                    status = "BEKREFTET",
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
                    sykmeldingId = sykmeldingId,
                )
            coEvery { sykmeldingStatusDb.getSykmeldingWithStatus(any()) } returns
                listOf(
                    tidligereSykmelding,
                    nySykmelding,
                )

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
            val tidligereArbeidsgivere =
                sykmeldingStatusService.finnTidligereArbeidsgivere(
                    fnr,
                    sykmeldingId,
                )
            assertNotNull(tidligereArbeidsgivere)
            assertEquals(1, tidligereArbeidsgivere.size)
        }

        @Test
        fun `overlappende sykmelding inni en annen sykmelding`() = testApplication {
            val tidligereSykmelding =
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 31.januar(2023),
                    status = "SENDT",
                    orgnummer = "123",
                )
            val nySykmelding =
                opprettSykmelding(
                    fom = 5.januar(2023),
                    tom = 20.januar(2023),
                    status = "APEN",
                    sykmeldingId = sykmeldingId,
                )
            coEvery { sykmeldingStatusDb.getSykmeldingWithStatus(any()) } returns
                listOf(
                    tidligereSykmelding,
                    nySykmelding,
                )

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
            val tidligereArbeidsgivere =
                sykmeldingStatusService.finnTidligereArbeidsgivere(
                    fnr,
                    sykmeldingId,
                )
            assertNotNull(tidligereArbeidsgivere)
            assertEquals(1, tidligereArbeidsgivere.size)
        }

        @Test
        fun `Sykmelding frem i tid skal ikke overlappe`() = testApplication {
            val tidligereSykmelding =
                opprettSykmelding(
                    fom = 28.januar(2023),
                    tom = 31.januar(2023),
                    status = "SENDT",
                    orgnummer = "123",
                )
            val nySykmelding =
                opprettSykmelding(
                    fom = 5.januar(2023),
                    tom = 20.januar(2023),
                    status = "APEN",
                    sykmeldingId = sykmeldingId,
                )
            coEvery { sykmeldingStatusDb.getSykmeldingWithStatus(any()) } returns
                listOf(
                    tidligereSykmelding,
                    nySykmelding,
                )

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
            val tidligereArbeidsgivere =
                sykmeldingStatusService.finnTidligereArbeidsgivere(
                    fnr,
                    sykmeldingId,
                )
            assertNotNull(tidligereArbeidsgivere)
            assertEquals(0, tidligereArbeidsgivere.size)
        }

        @Test
        fun `Ny sykmelding starter før tidligere`() = testApplication {
            val tidligereSykmelding =
                opprettSykmelding(
                    fom = 28.januar(2023),
                    tom = 31.januar(2023),
                    status = "SENDT",
                    orgnummer = "123",
                )
            val nySykmelding =
                opprettSykmelding(
                    fom = 27.januar(2023),
                    tom = 30.januar(2023),
                    status = "APEN",
                    sykmeldingId = sykmeldingId,
                )
            coEvery { sykmeldingStatusDb.getSykmeldingWithStatus(any()) } returns
                listOf(
                    tidligereSykmelding,
                    nySykmelding,
                )

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
            val tidligereArbeidsgivere =
                sykmeldingStatusService.finnTidligereArbeidsgivere(
                    fnr,
                    sykmeldingId,
                )
            assertEquals(emptyList(), tidligereArbeidsgivere)
        }

        @Test
        fun `Ny sykmelding starter etter`() = testApplication {
            val tidligereSykmelding =
                opprettSykmelding(
                    fom = 28.januar(2023),
                    tom = 31.januar(2023),
                    status = "BEKREFTET",
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
                    sykmeldingId = sykmeldingId,
                )
            coEvery { sykmeldingStatusDb.getSykmeldingWithStatus(any()) } returns
                listOf(
                    tidligereSykmelding,
                    nySykmelding,
                )

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
            val tidligereArbeidsgivere =
                sykmeldingStatusService.finnTidligereArbeidsgivere(
                    fnr,
                    sykmeldingId,
                )
            assertNotNull(tidligereArbeidsgivere)
            assertEquals(0, tidligereArbeidsgivere.size)
        }

        @Test
        fun `Ny Sykmelding starter før og slutter etter`() = testApplication {
            val tidligereSykmelding =
                opprettSykmelding(
                    fom = 28.januar(2023),
                    tom = 31.januar(2023),
                    status = "SENDT",
                    orgnummer = "1",
                )
            val nySykmelding =
                opprettSykmelding(
                    fom = 5.januar(2023),
                    tom = 3.februar(2023),
                    status = "APEN",
                    sykmeldingId = sykmeldingId,
                )
            coEvery { sykmeldingStatusDb.getSykmeldingWithStatus(any()) } returns
                listOf(
                    tidligereSykmelding,
                    nySykmelding,
                )

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
            val tidligereArbeidsgivere =
                sykmeldingStatusService.finnTidligereArbeidsgivere(
                    fnr,
                    sykmeldingId,
                )
            assertNotNull(tidligereArbeidsgivere)
            assertEquals(0, tidligereArbeidsgivere.size)
        }
    }

    @Nested
    @DisplayName("Bruker går fra arbeidstaker til annet")
    inner class BrukerGarFraArbeidstakerTilAnnet {
        @Test
        fun `skal ikke ha tidligere arbeidsgiver ved status annet`() = testApplication {
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
                    sykmeldingId = sykmeldingId,
                )
            coEvery { sykmeldingStatusDb.getSykmeldingWithStatus(any()) } returns
                listOf(
                    tidligereSykmelding,
                    nySykmelding,
                )

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
            sykmeldingStatusService.createStatus(
                opprettBekreftetSykmeldingUserEvent(arbeidssituasjon = ANNET),
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

        @Test
        fun `skal ikke ha tidligere arbeidsgiver ved andre statuser`() = testApplication {
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
                    sykmeldingId = sykmeldingId,
                )
            coEvery { sykmeldingStatusDb.getSykmeldingWithStatus(any()) } returns
                listOf(
                    tidligereSykmelding,
                    nySykmelding,
                )

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
            sykmeldingStatusService.createStatus(
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

    private fun checkStatusFails(
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
                            sykmeldingStatusService.createStatus(
                                opprettSendtSykmeldingUserEvent(),
                                sykmeldingId,
                                fnr,
                            )
                        StatusEventDTO.BEKREFTET ->
                            sykmeldingStatusService.createStatus(
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

    private fun checkStatusOk(
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
                        aktivtArbeidsforhold = true,
                        naermesteLeder = null,
                    ),
                )
            when (newStatus) {
                StatusEventDTO.SENDT ->
                    sykmeldingStatusService.createStatus(
                        opprettSendtSykmeldingUserEvent(),
                        sykmeldingId,
                        fnr,
                    )
                StatusEventDTO.BEKREFTET ->
                    sykmeldingStatusService.createStatus(
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
                else -> throw IllegalStateException("Ikke implementert $newStatus i testene")
            }

            coVerify(exactly = 1) { sykmeldingStatusDb.insertStatus(any(), any(), any()) }
        }
    }
}
