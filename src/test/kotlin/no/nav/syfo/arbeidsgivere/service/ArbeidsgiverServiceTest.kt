package no.nav.syfo.arbeidsgivere.service

import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkClass
import java.time.LocalDate
import no.nav.syfo.arbeidsgivere.db.ArbeidsforholdDb
import no.nav.syfo.arbeidsgivere.model.Arbeidsforhold
import no.nav.syfo.arbeidsgivere.narmesteleder.db.NarmestelederDb
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ArbeidsgiverServiceTest {
    val arbeidsforholdDb = mockkClass(ArbeidsforholdDb::class)
    val narmestelederDb = mockkClass(NarmestelederDb::class)

    val arbeidsgiverService =
        ArbeidsgiverService(
            narmestelederDb = narmestelederDb,
            arbeidsforholdDb = arbeidsforholdDb,
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
}
