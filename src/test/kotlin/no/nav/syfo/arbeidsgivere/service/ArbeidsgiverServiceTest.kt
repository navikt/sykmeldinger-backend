package no.nav.syfo.arbeidsgivere.service

import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkClass
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import no.nav.syfo.arbeidsforhold.ArbeidsforholdService
import no.nav.syfo.arbeidsgivere.db.ArbeidsforholdDb
import no.nav.syfo.arbeidsgivere.model.Arbeidsforhold
import no.nav.syfo.arbeidsgivere.model.ArbeidsforholdType
import no.nav.syfo.arbeidsgivere.narmesteleder.db.NarmestelederDb
import no.nav.syfo.sykmeldingstatus.TestHelper.Companion.februar
import no.nav.syfo.sykmeldingstatus.TestHelper.Companion.januar
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ArbeidsgiverServiceTest {
    val arbeidsforholdDb = mockkClass(ArbeidsforholdDb::class)
    val narmestelederDb = mockkClass(NarmestelederDb::class)
    val arbeidsforholdService = mockkClass(ArbeidsforholdService::class)
    val arbeidsgiverService =
        ArbeidsgiverService(
            narmestelederDb = narmestelederDb,
            arbeidsforholdDb = arbeidsforholdDb,
            arbeidsfhorholdService = arbeidsforholdService
        )

    @BeforeEach
    fun init() {
        clearMocks(
            narmestelederDb,
            arbeidsforholdDb,
        )
        coEvery { narmestelederDb.getNarmesteleder(any()) } returns getNarmesteledere()
    }

    @Test
    fun `arbeidsgiverService returnerer liste med arbeidsforhold`() = testApplication {
        coEvery { arbeidsforholdDb.getArbeidsforhold(any()) } returns getArbeidsgiverforhold()

        val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901")
        arbeidsgiverinformasjon.size shouldBeEqualTo 1
        arbeidsgiverinformasjon[0].navn shouldBeEqualTo "Navn 1"
        arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
        arbeidsgiverinformasjon[0].naermesteLeder?.navn shouldBeEqualTo "Leder Ledersen"
        arbeidsgiverinformasjon[0].naermesteLeder?.orgnummer shouldBeEqualTo "123456789"
        arbeidsgiverinformasjon[0].naermesteLeder?.organisasjonsnavn shouldBeEqualTo "Navn 1"
    }

    @Test
    fun `arbeidsgiverService returnerer tom liste hvis bruker ikke har arbeidsforhold`() =
        testApplication {
            coEvery { arbeidsforholdDb.getArbeidsforhold(any()) } returns emptyList()

            val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901")
            arbeidsgiverinformasjon.size shouldBeEqualTo 0

            coVerify(exactly = 0) { narmestelederDb.getNarmesteleder(any()) }
        }

    @Test
    fun `Viser arbeidsforhold som ikke aktivt hvis tom er satt for ansettelsesperiode før dagens dato`() =
        testApplication {
            coEvery { arbeidsforholdDb.getArbeidsforhold(any()) } returns
                getArbeidsgiverforhold(
                    fom = LocalDate.of(2019, 1, 1),
                    tom = LocalDate.now().minusDays(1),
                )

            val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901")
            arbeidsgiverinformasjon.size shouldBeEqualTo 1
            arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo false
        }

    @Test
    fun `Viser arbeidsforhold som ikke aktivt hvis fom er satt for ansettelsesperiode etter dagens dato`() =
        testApplication {
            coEvery { arbeidsforholdDb.getArbeidsforhold(any()) } returns
                getArbeidsgiverforhold(
                    fom = LocalDate.now().plusDays(1),
                    tom = LocalDate.now().plusDays(10),
                )

            val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901")
            arbeidsgiverinformasjon.size shouldBeEqualTo 1
            arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo false
        }

    @Test
    fun `Viser arbeidsforhold som aktivt hvis tom-dato er i fremtiden`() = testApplication {
        coEvery { arbeidsforholdDb.getArbeidsforhold(any()) } returns
            getArbeidsgiverforhold(
                fom = LocalDate.now().minusDays(1),
                tom = LocalDate.now().plusDays(1),
            )

        val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901")
        arbeidsgiverinformasjon.size shouldBeEqualTo 1
        arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
    }

    @Test
    fun `arbeidsgiverService filtrerer bort duplikate arbeidsforhold for samme orgnummer`() =
        testApplication {
            coEvery { arbeidsforholdDb.getArbeidsforhold(any()) } returns
                listOf(
                    Arbeidsforhold(
                        id = 1,
                        orgNavn = "Organisasjon",
                        orgnummer = "123456789",
                        fnr = "12345678901",
                        juridiskOrgnummer = "987654321",
                        fom = LocalDate.of(2020, 6, 1),
                        tom = null,
                    ),
                    Arbeidsforhold(
                        id = 2,
                        orgnummer = "123456789",
                        orgNavn = "Organisasjon",
                        juridiskOrgnummer = "987654321",
                        fnr = "12345678901",
                        fom = LocalDate.of(2020, 6, 1),
                        tom = null,
                    ),
                    Arbeidsforhold(
                        id = 3,
                        orgNavn = "Organisasjon",
                        orgnummer = "234567891",
                        juridiskOrgnummer = "987654321",
                        fnr = "12345678901",
                        fom = LocalDate.of(2020, 6, 1),
                        tom = null,
                    ),
                )

            val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901")
            arbeidsgiverinformasjon.size shouldBeEqualTo 2
            arbeidsgiverinformasjon[0].navn shouldBeEqualTo "Organisasjon"
            arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
            arbeidsgiverinformasjon[0].naermesteLeder?.navn shouldBeEqualTo "Leder Ledersen"
            arbeidsgiverinformasjon[0].naermesteLeder?.orgnummer shouldBeEqualTo "123456789"
            arbeidsgiverinformasjon[0].naermesteLeder?.organisasjonsnavn shouldBeEqualTo
                "Organisasjon"
        }

    @Test
    fun `arbeidsgiverService velger det aktive arbeidsforholdet ved duplikate arbeidsforhold for samme orgnummer`() =
        testApplication {
            coEvery { arbeidsforholdDb.getArbeidsforhold(any()) } returns
                listOf(
                    Arbeidsforhold(
                        id = 1,
                        fnr = "12345678901",
                        orgnummer = "123456789",
                        juridiskOrgnummer = "987654321",
                        orgNavn = "Navn 1",
                        fom = LocalDate.of(2020, 5, 1),
                        tom = LocalDate.of(2020, 6, 1),
                    ),
                    Arbeidsforhold(
                        id = 2,
                        fnr = "12345678901",
                        orgnummer = "123456789",
                        juridiskOrgnummer = "987654321",
                        orgNavn = "Navn 1",
                        fom = LocalDate.of(2020, 1, 1),
                        tom = null,
                    ),
                )

            val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901")
            arbeidsgiverinformasjon.size shouldBeEqualTo 1
            arbeidsgiverinformasjon[0].navn shouldBeEqualTo "Navn 1"
            arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
            arbeidsgiverinformasjon[0].naermesteLeder?.navn shouldBeEqualTo "Leder Ledersen"
            arbeidsgiverinformasjon[0].naermesteLeder?.orgnummer shouldBeEqualTo "123456789"
            arbeidsgiverinformasjon[0].naermesteLeder?.organisasjonsnavn shouldBeEqualTo "Navn 1"
        }

    @Test
    fun `arbeidsgiverService velger det aktive arbeidsforholdet ved duplikate arbeidsforhold der alle har satt tom-dato for samme orgnummer`() =
        testApplication {
            coEvery { arbeidsforholdDb.getArbeidsforhold(any()) } returns
                listOf(
                    Arbeidsforhold(
                        id = 1,
                        fnr = "12345678901",
                        orgnummer = "123456789",
                        juridiskOrgnummer = "987654321",
                        orgNavn = "Navn 1",
                        fom = LocalDate.of(2021, 3, 12),
                        tom = LocalDate.of(2021, 4, 22),
                    ),
                    Arbeidsforhold(
                        id = 2,
                        fnr = "12345678901",
                        orgnummer = "123456789",
                        juridiskOrgnummer = "987654321",
                        orgNavn = "Navn 1",
                        fom = LocalDate.of(2021, 4, 23),
                        tom = LocalDate.of(2021, 9, 1),
                    ),
                )

            val arbeidsgiverinformasjon =
                arbeidsgiverService.getArbeidsgivere(
                    "12345678901",
                    date = LocalDate.of(2021, 8, 22),
                )
            arbeidsgiverinformasjon.size shouldBeEqualTo 1
            arbeidsgiverinformasjon[0].navn shouldBeEqualTo "Navn 1"
            arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
            arbeidsgiverinformasjon[0].naermesteLeder?.navn shouldBeEqualTo "Leder Ledersen"
            arbeidsgiverinformasjon[0].naermesteLeder?.orgnummer shouldBeEqualTo "123456789"
            arbeidsgiverinformasjon[0].naermesteLeder?.organisasjonsnavn shouldBeEqualTo "Navn 1"
        }

    @Test
    fun `Henter arbeidsgivere kun innenfor sykmeldingsperiode - happy case`() = testApplication {
        coEvery { arbeidsforholdDb.getArbeidsforhold(any()) } returns
            getArbeidsgiverforhold(
                fom = 1.januar(2020),
                tom = null,
            )

        val arbeidsgiverinformasjon =
            arbeidsgiverService.getArbeidsgivereWithinSykmeldingPeriode(
                1.januar(2023),
                31.januar(2023),
                "1",
            )
        arbeidsgiverinformasjon.size shouldBeEqualTo 1
        arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
    }

    @Test
    fun `Henter ikke arbeidsforhold som er frilansere`() {
        runBlocking {
            coEvery { arbeidsforholdDb.getArbeidsforhold(any()) } returns
                getArbeidsgiverforhold(
                    fom = 1.januar(2020),
                    tom = null,
                    type = ArbeidsforholdType.FRILANSER_OPPDRAGSTAKER_HONORAR_PERSONER_MM,
                )

            val arbeidsgiverinformasjon =
                arbeidsgiverService.getArbeidsgivereWithinSykmeldingPeriode(
                    1.januar(2023),
                    31.januar(2023),
                    "1",
                )
            arbeidsgiverinformasjon.size shouldBeEqualTo 0
        }
    }

    @Test
    fun `Henter alle arbeidsforhold som ikke er frilansere`() {
        runBlocking {
            coEvery { arbeidsforholdDb.getArbeidsforhold(any()) } returns
                getArbeidsgiverforhold(
                    fom = 1.januar(2020),
                    tom = null,
                    type = ArbeidsforholdType.ORDINAERT_ARBEIDSFORHOLD,
                ) +
                    getArbeidsgiverforhold(
                        fom = 1.januar(2020),
                        tom = null,
                        type = ArbeidsforholdType.MARITIMT_ARBEIDSFORHOLD,
                    ) +
                    getArbeidsgiverforhold(
                        fom = 1.januar(2020),
                        tom = null,
                        type =
                            ArbeidsforholdType
                                .PENSJON_OG_ANDRE_TYPER_YTELSER_UTEN_ANSETTELSESFORHOLD,
                    ) +
                    getArbeidsgiverforhold(
                        fom = 1.januar(2020),
                        tom = null,
                        type = ArbeidsforholdType.FORENKLET_OPPGJOERSORDNING,
                    ) +
                    getArbeidsgiverforhold(
                        fom = 1.januar(2020),
                        tom = null,
                        type = ArbeidsforholdType.FRILANSER_OPPDRAGSTAKER_HONORAR_PERSONER_MM,
                    ) +
                    getArbeidsgiverforhold(
                        fom = 1.januar(2020),
                        tom = null,
                        type = null,
                    )

            val arbeidsgiverinformasjon =
                arbeidsgiverService.getArbeidsgivereWithinSykmeldingPeriode(
                    1.januar(2023),
                    31.januar(2023),
                    "1",
                )
            arbeidsgiverinformasjon.size shouldBeEqualTo 5
        }
    }

    @Test
    fun `To sykmeldinger med to forskjellige arbeidsforhold returnerer riktig arbeidsforhold`() =
        testApplication {
            coEvery { arbeidsforholdDb.getArbeidsforhold(any()) } returns
                listOf(
                    Arbeidsforhold(
                        id = 1,
                        fnr = "12345678901",
                        orgnummer = ORGNR_1,
                        juridiskOrgnummer = ORGNR_1,
                        orgNavn = "Navn 2",
                        fom = 1.januar(2020),
                        tom = 30.januar(2023),
                    ),
                    Arbeidsforhold(
                        id = 2,
                        fnr = "12345678901",
                        orgnummer = ORGNR_2,
                        juridiskOrgnummer = ORGNR_2,
                        orgNavn = "Navn 1",
                        fom = 31.januar(2023),
                        tom = null,
                    ),
                )

            val arbeidsgiverinformasjonSm1 =
                arbeidsgiverService.getArbeidsgivereWithinSykmeldingPeriode(
                    1.januar(2023),
                    30.januar(2023),
                    "1",
                )
            arbeidsgiverinformasjonSm1.size shouldBeEqualTo 1
            arbeidsgiverinformasjonSm1[0].aktivtArbeidsforhold shouldBeEqualTo false
            arbeidsgiverinformasjonSm1.first().orgnummer shouldBeEqualTo ORGNR_1

            val arbeidsgiverinformasjonSm2 =
                arbeidsgiverService.getArbeidsgivereWithinSykmeldingPeriode(
                    31.januar(2023),
                    15.februar(2023),
                    "1",
                )

            arbeidsgiverinformasjonSm2.size shouldBeEqualTo 1
            arbeidsgiverinformasjonSm2[0].aktivtArbeidsforhold shouldBeEqualTo true
            arbeidsgiverinformasjonSm2.first().orgnummer shouldBeEqualTo ORGNR_2
        }

    @Test
    fun `sluttdato på arbeidsforhold er null startdato er før sykmeldingTom`() = testApplication {
        coEvery { arbeidsforholdDb.getArbeidsforhold(any()) } returns
            getArbeidsgiverforhold(
                fom = 1.januar(2023),
                tom = null,
            )

        val arbeidsgiverinformasjon =
            arbeidsgiverService.getArbeidsgivereWithinSykmeldingPeriode(
                31.januar(2023),
                15.februar(2023),
                "1",
            )

        arbeidsgiverinformasjon.size shouldBeEqualTo 1
        arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
    }

    @Test
    fun `sluttdato på arbeidsforhold er null, startdato er lik sykmeldingTom`() = testApplication {
        coEvery { arbeidsforholdDb.getArbeidsforhold(any()) } returns
            getArbeidsgiverforhold(
                fom = 1.januar(2023),
                tom = null,
            )

        val arbeidsgiverinformasjon =
            arbeidsgiverService.getArbeidsgivereWithinSykmeldingPeriode(
                1.januar(2023),
                31.januar(2023),
                "1",
            )

        arbeidsgiverinformasjon.size shouldBeEqualTo 1
        arbeidsgiverinformasjon.single().aktivtArbeidsforhold shouldBeEqualTo true
    }

    @Test
    fun `sluttdato på arbeidsforhold er null, startdato er etter tom - ingen aktive arbeidsforhold innenfor sm periode`() =
        testApplication {
            coEvery { arbeidsforholdDb.getArbeidsforhold(any()) } returns
                getArbeidsgiverforhold(
                    fom = 1.februar(2023),
                    tom = null,
                )

            val arbeidsgiverinformasjon =
                arbeidsgiverService.getArbeidsgivereWithinSykmeldingPeriode(
                    1.januar(2023),
                    31.januar(2023),
                    "1",
                )

            arbeidsgiverinformasjon.size shouldBeEqualTo 0
        }

    @Test
    fun `sluttdato på arbeidsforhold er etter fom, startdato er før tom`() = testApplication {
        coEvery { arbeidsforholdDb.getArbeidsforhold(any()) } returns
            getArbeidsgiverforhold(
                fom = 1.januar(2020),
                tom = 31.januar(2023),
            )

        val arbeidsgiverinformasjon =
            arbeidsgiverService.getArbeidsgivereWithinSykmeldingPeriode(
                15.januar(2023),
                31.januar(2023),
                "1",
            )

        arbeidsgiverinformasjon.size shouldBeEqualTo 1
    }

    @Test
    fun `sluttdato på arbeidsforhold er null, startdato er lik tom`() = testApplication {
        coEvery { arbeidsforholdDb.getArbeidsforhold(any()) } returns
            getArbeidsgiverforhold(
                fom = 31.januar(2020),
                tom = null,
            )
        val arbeidsgiverinformasjon =
            arbeidsgiverService.getArbeidsgivereWithinSykmeldingPeriode(
                15.januar(2023),
                31.januar(2023),
                "1",
            )
        arbeidsgiverinformasjon.size shouldBeEqualTo 1
    }

    @Test
    fun `sluttdato på arbeidsforhold er lik fom, startdato er før tom`() = testApplication {
        coEvery { arbeidsforholdDb.getArbeidsforhold(any()) } returns
            getArbeidsgiverforhold(
                fom = 1.januar(2020),
                tom = 31.januar(2023),
            )

        val arbeidsgiverinformasjon =
            arbeidsgiverService.getArbeidsgivereWithinSykmeldingPeriode(
                31.januar(2023),
                15.februar(2023),
                "1",
            )

        arbeidsgiverinformasjon.size shouldBeEqualTo 1
    }
}
