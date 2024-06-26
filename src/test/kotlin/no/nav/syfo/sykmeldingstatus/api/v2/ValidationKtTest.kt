package no.nav.syfo.sykmeldingstatus.api.v2

import java.time.LocalDate
import kotlin.test.assertFailsWith
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.sykmeldingstatus.kafka.model.ShortNameKafkaDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.SporsmalOgSvarKafkaDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.SvartypeKafkaDTO
import no.nav.syfo.sykmeldingstatus.kafka.toSporsmalSvarListe
import no.nav.syfo.utils.objectMapper
import org.amshove.kluent.invoking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotThrow
import org.amshove.kluent.shouldThrow
import org.amshove.kluent.withMessage
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ValidationKtTest {
    @Test
    fun `Skal kaste exception hvis opplysningene ikke stemmer, men uriktige opplysninger er tom`() {
        val sykmeldingFormResponse =
            SykmeldingFormResponse(
                erOpplysningeneRiktige =
                    SporsmalSvar(
                        sporsmaltekst = "",
                        svar = JaEllerNei.NEI,
                    ),
                uriktigeOpplysninger = null,
                arbeidssituasjon =
                    SporsmalSvar(
                        sporsmaltekst = "",
                        svar = Arbeidssituasjon.ANNET,
                    ),
                arbeidsgiverOrgnummer = null,
                arbeidsledig = null,
                riktigNarmesteLeder = null,
                harBruktEgenmelding = null,
                egenmeldingsperioder = null,
                harForsikring = null,
                harBruktEgenmeldingsdager = null,
                egenmeldingsdager = null,
                fisker = null,
            )

        assertFailsWith<ValidationException> { sykmeldingFormResponse.validate() }
    }

    @Nested
    @DisplayName("Arbeidssituasjon - Fisker")
    inner class Fisker {
        @Test
        fun `Skal kaste exception dersom arbeidssituasjon er fisker men ingen fisker-svar`() {
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
                            svar = Arbeidssituasjon.FISKER,
                        ),
                    arbeidsgiverOrgnummer = null,
                    arbeidsledig = null,
                    riktigNarmesteLeder = null,
                    harBruktEgenmelding = null,
                    egenmeldingsperioder = null,
                    harForsikring = null,
                    harBruktEgenmeldingsdager = null,
                    egenmeldingsdager = null,
                    fisker = null,
                )

            invoking { sykmeldingFormResponse.validate() } shouldThrow
                ValidationException::class withMessage
                "Fisker-seksjon må være definert når arbeidssituasjon er fisker"
        }

        @Test
        fun `Skal kaste exception dersom fisker er på Hyre og mangler arbeidsgiver-nummer`() {
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
                            svar = Arbeidssituasjon.FISKER,
                        ),
                    arbeidsgiverOrgnummer = null,
                    arbeidsledig = null,
                    riktigNarmesteLeder = null,
                    harBruktEgenmelding = null,
                    egenmeldingsperioder = null,
                    harForsikring = null,
                    harBruktEgenmeldingsdager = null,
                    egenmeldingsdager = null,
                    fisker =
                        FiskerSvar(
                            blad =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = Blad.A,
                                ),
                            lottOgHyre =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = LottOgHyre.HYRE,
                                ),
                        ),
                )

            invoking { sykmeldingFormResponse.validate() } shouldThrow
                ValidationException::class withMessage
                "Arbeidsgiver må være valgt når arbeidssituasjon er fisker på hyre"
        }

        @Test
        fun `Skal gå fint for en vanlig Hyre fisker på Blad A`() {
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
                            svar = Arbeidssituasjon.FISKER,
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
                    fisker =
                        FiskerSvar(
                            blad =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = Blad.A,
                                ),
                            lottOgHyre =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = LottOgHyre.HYRE,
                                ),
                        ),
                )

            invoking { sykmeldingFormResponse.validate() } shouldNotThrow ValidationException::class
        }

        @Test
        fun `Skal gå fint for en vanlig Lott fisker på Blad A`() {
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
                            svar = Arbeidssituasjon.FISKER,
                        ),
                    arbeidsgiverOrgnummer =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = "123456789",
                        ),
                    arbeidsledig = null,
                    riktigNarmesteLeder = null,
                    harBruktEgenmelding =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.NEI,
                        ),
                    egenmeldingsperioder = null,
                    harForsikring =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA,
                        ),
                    harBruktEgenmeldingsdager = null,
                    egenmeldingsdager = null,
                    fisker =
                        FiskerSvar(
                            blad =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = Blad.A,
                                ),
                            lottOgHyre =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = LottOgHyre.LOTT,
                                ),
                        ),
                )

            invoking { sykmeldingFormResponse.validate() } shouldNotThrow ValidationException::class
        }

        @Test
        fun `Skal gå bra dersom fisker er på Lott og Blad B og mangler forsikringspørsmål`() {
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
                            svar = Arbeidssituasjon.FISKER,
                        ),
                    arbeidsgiverOrgnummer =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = "123456789",
                        ),
                    arbeidsledig = null,
                    riktigNarmesteLeder = null,
                    harBruktEgenmelding =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.NEI,
                        ),
                    egenmeldingsperioder = null,
                    harForsikring = null,
                    harBruktEgenmeldingsdager = null,
                    egenmeldingsdager = null,
                    fisker =
                        FiskerSvar(
                            blad =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = Blad.B,
                                ),
                            lottOgHyre =
                                SporsmalSvar(
                                    sporsmaltekst = "",
                                    svar = LottOgHyre.LOTT,
                                ),
                        ),
                )

            invoking { sykmeldingFormResponse.validate() } shouldNotThrow ValidationException::class
        }
    }

    @Nested
    @DisplayName("Arbeidssituasjon: Arbeidsgiver")
    inner class Arbeidsgiver {
        @Test
        fun `Skal kaste exception hvis arbeidssituasjon == ARBEIDSGIVER, men arbeidsgiverOrgnummer og nyNarmesteLeder mangler`() {
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
                            svar = Arbeidssituasjon.ARBEIDSTAKER,
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

            assertFailsWith<ValidationException> { sykmeldingFormResponse.validate() }
        }

        @Test
        fun `Skal kaste exception hvis arbeidssituasjon != ARBEIDSGIVER, men arbeidsgiverOrgnummer og nyNarmesteLeder er satt`() {
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
                            svar = Arbeidssituasjon.NAERINGSDRIVENDE,
                        ),
                    arbeidsgiverOrgnummer =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = "13456789",
                        ),
                    arbeidsledig = null,
                    riktigNarmesteLeder =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA,
                        ),
                    harBruktEgenmelding = null,
                    egenmeldingsperioder = null,
                    harForsikring = null,
                    harBruktEgenmeldingsdager = null,
                    egenmeldingsdager = null,
                    fisker = null,
                )

            assertFailsWith<ValidationException> { sykmeldingFormResponse.validate() }
        }

        @Test
        fun `Skal kaste exception hvis harBruktEgenmelding == JA, men egenmeldingsperioder og harForsikring mangler`() {
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
                            svar = Arbeidssituasjon.ARBEIDSTAKER,
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

            assertFailsWith<ValidationException> { sykmeldingFormResponse.validate() }
        }

        @Test
        fun `Skal kaste exception hvis harBruktEgenmeldingsdager == JA, men egenmeldingsdager mangler`() {
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
                            svar = Arbeidssituasjon.ARBEIDSTAKER,
                        ),
                    arbeidsgiverOrgnummer =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = "543263",
                        ),
                    arbeidsledig = null,
                    riktigNarmesteLeder = null,
                    harBruktEgenmelding = null,
                    egenmeldingsperioder = null,
                    harForsikring = null,
                    harBruktEgenmeldingsdager =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA,
                        ),
                    egenmeldingsdager = null,
                    fisker = null,
                )
            invoking { sykmeldingFormResponse.validate() } shouldThrow
                ValidationException::class withMessage
                "Spørsmål om egenmeldimngsdager må minst ha 1 dag, når harBruktEgenmeldingsdager er JA"
        }

        @Test
        fun `Skal kaste exception hvis harBruktEgenmeldingsdager == JA, men egenmeldingsdager er en tom liste`() {
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
                            svar = Arbeidssituasjon.ARBEIDSTAKER,
                        ),
                    arbeidsgiverOrgnummer =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = "543263",
                        ),
                    arbeidsledig = null,
                    riktigNarmesteLeder = null,
                    harBruktEgenmelding = null,
                    egenmeldingsperioder = null,
                    harForsikring = null,
                    harBruktEgenmeldingsdager =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA,
                        ),
                    egenmeldingsdager =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = emptyList(),
                        ),
                    fisker = null,
                )
            invoking { sykmeldingFormResponse.validate() } shouldThrow
                ValidationException::class withMessage
                "Spørsmål om egenmeldimngsdager må minst ha 1 dag, når harBruktEgenmeldingsdager er JA"
        }

        @Test
        fun `Skal IKKE kaste exception hvis harBruktEgenmeldingsdager == JA, men egenmeldingsdager har 1 element`() {
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
                            svar = Arbeidssituasjon.ARBEIDSTAKER,
                        ),
                    arbeidsgiverOrgnummer =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = "543263",
                        ),
                    arbeidsledig = null,
                    riktigNarmesteLeder = null,
                    harBruktEgenmelding = null,
                    egenmeldingsperioder = null,
                    harForsikring = null,
                    harBruktEgenmeldingsdager =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA,
                        ),
                    egenmeldingsdager =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = listOf(LocalDate.now()),
                        ),
                    fisker = null,
                )
            invoking { sykmeldingFormResponse.validate() } shouldNotThrow ValidationException::class
        }
    }

    @Nested
    @DisplayName("Arbeidssituasjon: Frilanser")
    inner class Frilanser {
        @Test
        fun `Skal kaste exception hvis harBruktEgenmelding == JA, men egenmeldingsperioder og harForsikring mangler`() {
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
                            svar = Arbeidssituasjon.FRILANSER,
                        ),
                    arbeidsgiverOrgnummer = null,
                    arbeidsledig = null,
                    riktigNarmesteLeder = null,
                    harBruktEgenmelding =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA,
                        ),
                    egenmeldingsperioder = null,
                    harForsikring = null,
                    harBruktEgenmeldingsdager = null,
                    egenmeldingsdager = null,
                    fisker = null,
                )

            assertFailsWith<ValidationException> { sykmeldingFormResponse.validate() }
        }
    }

    @Nested
    @DisplayName("Arbeidssituasjon: Selvstendig")
    inner class Selvstendig {
        @Test
        fun `Skal kaste exception hvis harBruktEgenmelding == JA, men egenmeldingsperioder og harForsikring mangler`() {
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
                            svar = Arbeidssituasjon.NAERINGSDRIVENDE,
                        ),
                    arbeidsgiverOrgnummer = null,
                    arbeidsledig = null,
                    riktigNarmesteLeder = null,
                    harBruktEgenmelding =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA,
                        ),
                    egenmeldingsperioder = null,
                    harForsikring = null,
                    harBruktEgenmeldingsdager = null,
                    egenmeldingsdager = null,
                    fisker = null,
                )

            assertFailsWith<ValidationException> { sykmeldingFormResponse.validate() }
        }
    }

    @Nested
    @DisplayName("Arbeidssituasjon: Arbeidsledig")
    inner class Arbeidsledig {
        @Test
        fun `Skal kaste exception hvis egenmeldingsperioder er satt`() {
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
                            svar = Arbeidssituasjon.ARBEIDSLEDIG,
                        ),
                    arbeidsgiverOrgnummer = null,
                    arbeidsledig = null,
                    riktigNarmesteLeder = null,
                    harBruktEgenmelding = null,
                    egenmeldingsperioder =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = listOf(),
                        ),
                    harForsikring = null,
                    harBruktEgenmeldingsdager = null,
                    egenmeldingsdager = null,
                    fisker = null,
                )

            assertFailsWith<ValidationException> { sykmeldingFormResponse.validate() }
        }

        @Test
        fun `Skal kaste exception hvis harForsikring er satt`() {
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
                            svar = Arbeidssituasjon.ARBEIDSLEDIG,
                        ),
                    arbeidsgiverOrgnummer = null,
                    arbeidsledig = null,
                    riktigNarmesteLeder = null,
                    harBruktEgenmelding = null,
                    egenmeldingsperioder = null,
                    harForsikring =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA,
                        ),
                    harBruktEgenmeldingsdager = null,
                    egenmeldingsdager = null,
                    fisker = null,
                )

            assertFailsWith<ValidationException> { sykmeldingFormResponse.validate() }
        }
    }

    @Nested
    @DisplayName("Arbeidssituasjon: Annet")
    inner class Annet {
        @Test
        fun `Skal kaste exception hvis egenmeldingsperioder er satt`() {
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
                            svar = Arbeidssituasjon.ANNET,
                        ),
                    arbeidsgiverOrgnummer = null,
                    arbeidsledig = null,
                    riktigNarmesteLeder = null,
                    harBruktEgenmelding = null,
                    egenmeldingsperioder =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = listOf(),
                        ),
                    harForsikring = null,
                    harBruktEgenmeldingsdager = null,
                    egenmeldingsdager = null,
                    fisker = null,
                )

            assertFailsWith<ValidationException> { sykmeldingFormResponse.validate() }
        }

        @Test
        fun `Skal kaste exception hvis harForsikring er satt`() {
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
                            svar = Arbeidssituasjon.ANNET,
                        ),
                    arbeidsgiverOrgnummer = null,
                    arbeidsledig = null,
                    riktigNarmesteLeder = null,
                    harBruktEgenmelding = null,
                    egenmeldingsperioder = null,
                    harForsikring =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA,
                        ),
                    harBruktEgenmeldingsdager = null,
                    egenmeldingsdager = null,
                    fisker = null,
                )

            assertFailsWith<ValidationException> { sykmeldingFormResponse.validate() }
        }
    }

    @Nested
    @DisplayName("SporsmalOgSvar builders")
    inner class SporsmalOgSvar {

        @Test
        fun `Skal lage SporsmalOgSvarDTO for arbeidssituasjon`() {
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
                            svar = Arbeidssituasjon.FRILANSER,
                        ),
                    arbeidsgiverOrgnummer = null,
                    arbeidsledig = null,
                    riktigNarmesteLeder = null,
                    harBruktEgenmelding = null,
                    egenmeldingsperioder = null,
                    harForsikring = null,
                    harBruktEgenmeldingsdager = null,
                    egenmeldingsdager = null,
                    fisker = null,
                )

            val sporsmalOgSvarListe =
                sykmeldingFormResponse.toSporsmalSvarListe(sykmeldingId = "id")

            val expected =
                listOf(
                    SporsmalOgSvarKafkaDTO(
                        "",
                        ShortNameKafkaDTO.ARBEIDSSITUASJON,
                        SvartypeKafkaDTO.ARBEIDSSITUASJON,
                        svar = Arbeidssituasjon.FRILANSER.name,
                    ),
                )

            sporsmalOgSvarListe shouldBeEqualTo expected
        }

        @Test
        fun `Skal lage SporsmalOgSvarDTO for riktigNarmesteLeder med aktiv arbeidsgiver`() {
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
                            svar = Arbeidssituasjon.ARBEIDSTAKER,
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
                            svar = JaEllerNei.JA,
                        ),
                    harBruktEgenmelding = null,
                    egenmeldingsperioder = null,
                    harForsikring = null,
                    harBruktEgenmeldingsdager = null,
                    egenmeldingsdager = null,
                    fisker = null,
                )

            val arbeidsgiver =
                Arbeidsgiverinfo(
                    orgnummer = "132456789",
                    juridiskOrgnummer = "",
                    navn = "",
                    aktivtArbeidsforhold = true,
                    naermesteLeder = null,
                )

            val sporsmalOgSvarListe = sykmeldingFormResponse.toSporsmalSvarListe(arbeidsgiver, "id")

            val expected =
                listOf(
                    SporsmalOgSvarKafkaDTO(
                        "",
                        ShortNameKafkaDTO.ARBEIDSSITUASJON,
                        SvartypeKafkaDTO.ARBEIDSSITUASJON,
                        svar = Arbeidssituasjon.ARBEIDSTAKER.name,
                    ),
                    SporsmalOgSvarKafkaDTO(
                        "",
                        ShortNameKafkaDTO.NY_NARMESTE_LEDER,
                        SvartypeKafkaDTO.JA_NEI,
                        svar = JaEllerNei.NEI.name,
                    ),
                )

            sporsmalOgSvarListe shouldBeEqualTo expected
        }

        @Test
        fun `Skal lage SporsmalOgSvarDTO for riktigNarmesteLeder med inaktiv arbeidsgiver`() {
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
                            svar = Arbeidssituasjon.ARBEIDSTAKER,
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
                            svar = JaEllerNei.JA,
                        ),
                    harBruktEgenmelding = null,
                    egenmeldingsperioder = null,
                    harForsikring = null,
                    harBruktEgenmeldingsdager = null,
                    egenmeldingsdager = null,
                    fisker = null,
                )

            val arbeidsgiver =
                Arbeidsgiverinfo(
                    orgnummer = "132456789",
                    juridiskOrgnummer = "",
                    navn = "",
                    aktivtArbeidsforhold = false,
                    naermesteLeder = null,
                )

            val sporsmalOgSvarListe = sykmeldingFormResponse.toSporsmalSvarListe(arbeidsgiver, "id")

            val expected =
                listOf(
                    SporsmalOgSvarKafkaDTO(
                        "",
                        ShortNameKafkaDTO.ARBEIDSSITUASJON,
                        SvartypeKafkaDTO.ARBEIDSSITUASJON,
                        svar = Arbeidssituasjon.ARBEIDSTAKER.name,
                    ),
                    SporsmalOgSvarKafkaDTO(
                        "Skal finne ny nærmeste leder",
                        ShortNameKafkaDTO.NY_NARMESTE_LEDER,
                        SvartypeKafkaDTO.JA_NEI,
                        svar = JaEllerNei.NEI.name,
                    ),
                )

            sporsmalOgSvarListe shouldBeEqualTo expected
        }

        @Test
        fun `Skal lage SporsmalOgSvarDTO for fravarSporsmal`() {
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
                            svar = Arbeidssituasjon.NAERINGSDRIVENDE,
                        ),
                    arbeidsgiverOrgnummer = null,
                    riktigNarmesteLeder = null,
                    harBruktEgenmelding =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.NEI,
                        ),
                    arbeidsledig = null,
                    egenmeldingsperioder = null,
                    harForsikring =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA,
                        ),
                    harBruktEgenmeldingsdager = null,
                    egenmeldingsdager = null,
                    fisker = null,
                )

            val sporsmalOgSvarListe =
                sykmeldingFormResponse.toSporsmalSvarListe(sykmeldingId = "id")

            val expected =
                listOf(
                    SporsmalOgSvarKafkaDTO(
                        "",
                        ShortNameKafkaDTO.ARBEIDSSITUASJON,
                        SvartypeKafkaDTO.ARBEIDSSITUASJON,
                        svar = Arbeidssituasjon.NAERINGSDRIVENDE.name,
                    ),
                    SporsmalOgSvarKafkaDTO(
                        "",
                        ShortNameKafkaDTO.FRAVAER,
                        SvartypeKafkaDTO.JA_NEI,
                        svar = JaEllerNei.NEI.name,
                    ),
                    SporsmalOgSvarKafkaDTO(
                        "",
                        ShortNameKafkaDTO.FORSIKRING,
                        SvartypeKafkaDTO.JA_NEI,
                        svar = JaEllerNei.JA.name,
                    ),
                )

            sporsmalOgSvarListe shouldBeEqualTo expected
        }

        @Test
        fun `Skal lage SporsmalOgSvarDTO for egenmeldingsperioder`() {
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
                            svar = Arbeidssituasjon.FRILANSER,
                        ),
                    arbeidsgiverOrgnummer = null,
                    arbeidsledig = null,
                    riktigNarmesteLeder = null,
                    harBruktEgenmelding =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA,
                        ),
                    egenmeldingsperioder =
                        SporsmalSvar(
                            sporsmaltekst = "",
                            svar =
                                listOf(
                                    Egenmeldingsperiode(
                                        fom = LocalDate.now(),
                                        tom = LocalDate.now(),
                                    ),
                                ),
                        ),
                    harForsikring = null,
                    harBruktEgenmeldingsdager = null,
                    egenmeldingsdager = null,
                    fisker = null,
                )

            val sporsmalOgSvarListe =
                sykmeldingFormResponse.toSporsmalSvarListe(sykmeldingId = "id")

            val expected =
                listOf(
                    SporsmalOgSvarKafkaDTO(
                        "",
                        ShortNameKafkaDTO.ARBEIDSSITUASJON,
                        SvartypeKafkaDTO.ARBEIDSSITUASJON,
                        svar = Arbeidssituasjon.FRILANSER.name,
                    ),
                    SporsmalOgSvarKafkaDTO(
                        "",
                        ShortNameKafkaDTO.FRAVAER,
                        SvartypeKafkaDTO.JA_NEI,
                        svar = JaEllerNei.JA.name,
                    ),
                    SporsmalOgSvarKafkaDTO(
                        "",
                        ShortNameKafkaDTO.PERIODE,
                        SvartypeKafkaDTO.PERIODER,
                        svar =
                            objectMapper.writeValueAsString(
                                listOf(
                                    Egenmeldingsperiode(
                                        fom = LocalDate.now(),
                                        tom = LocalDate.now(),
                                    ),
                                ),
                            ),
                    ),
                )

            sporsmalOgSvarListe shouldBeEqualTo expected
        }
    }
}
