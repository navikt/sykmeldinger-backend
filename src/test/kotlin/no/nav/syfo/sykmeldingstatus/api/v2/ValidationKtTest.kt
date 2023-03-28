package no.nav.syfo.sykmeldingstatus.api.v2

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmeldingstatus.kafka.toSporsmalSvarListe
import org.amshove.kluent.invoking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotThrow
import org.amshove.kluent.shouldThrow
import org.amshove.kluent.withMessage
import java.time.LocalDate
import kotlin.test.assertFailsWith

class ValidationKtTest : FunSpec({
    context("Validation") {
        context("erOpplysnigeneRiktige") {
            test("Skal kaste exception hvis opplysningene ikke stemmer, men uriktige opplysninger er tom") {
                val sykmeldingUserEvent = SykmeldingUserEvent(
                    erOpplysningeneRiktige = SporsmalSvar(
                        sporsmaltekst = "",
                        svar = JaEllerNei.NEI
                    ),
                    uriktigeOpplysninger = null,
                    arbeidssituasjon = SporsmalSvar(
                        sporsmaltekst = "",
                        svar = ArbeidssituasjonDTO.ANNET
                    ),
                    arbeidsgiverOrgnummer = null,
                    riktigNarmesteLeder = null,
                    harBruktEgenmelding = null,
                    egenmeldingsperioder = null,
                    harForsikring = null,
                    harBruktEgenmeldingsdager = null,
                    egenmeldingsdager = null
                )

                assertFailsWith<ValidationException> {
                    sykmeldingUserEvent.validate()
                }
            }
        }

        context("arbeidssituasjon") {
            context("arbeidsgiver") {
                test("Skal kaste exception hvis arbeidssituasjon == ARBEIDSGIVER, men arbeidsgiverOrgnummer og nyNarmesteLeder mangler") {
                    val sykmeldingUserEvent = SykmeldingUserEvent(

                        erOpplysningeneRiktige = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA
                        ),
                        uriktigeOpplysninger = null,
                        arbeidssituasjon = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = ArbeidssituasjonDTO.ARBEIDSTAKER
                        ),
                        arbeidsgiverOrgnummer = null,
                        riktigNarmesteLeder = null,
                        harBruktEgenmelding = null,
                        egenmeldingsperioder = null,
                        harForsikring = null,
                        harBruktEgenmeldingsdager = null,
                        egenmeldingsdager = null
                    )

                    assertFailsWith<ValidationException> {
                        sykmeldingUserEvent.validate()
                    }
                }

                test("Skal kaste exception hvis arbeidssituasjon != ARBEIDSGIVER, men arbeidsgiverOrgnummer og nyNarmesteLeder er satt") {
                    val sykmeldingUserEvent = SykmeldingUserEvent(
                        erOpplysningeneRiktige = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA
                        ),
                        uriktigeOpplysninger = null,
                        arbeidssituasjon = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = ArbeidssituasjonDTO.ANNET
                        ),
                        arbeidsgiverOrgnummer = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = "13456789"
                        ),
                        riktigNarmesteLeder = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA
                        ),
                        harBruktEgenmelding = null,
                        egenmeldingsperioder = null,
                        harForsikring = null,
                        harBruktEgenmeldingsdager = null,
                        egenmeldingsdager = null
                    )

                    assertFailsWith<ValidationException> {
                        sykmeldingUserEvent.validate()
                    }
                }

                test("Skal kaste exception hvis harBruktEgenmelding == JA, men egenmeldingsperioder og harForsikring mangler") {
                    val sykmeldingUserEvent = SykmeldingUserEvent(
                        erOpplysningeneRiktige = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA
                        ),
                        uriktigeOpplysninger = null,
                        arbeidssituasjon = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = ArbeidssituasjonDTO.ARBEIDSTAKER
                        ),
                        arbeidsgiverOrgnummer = null,
                        riktigNarmesteLeder = null,
                        harBruktEgenmelding = null,
                        egenmeldingsperioder = null,
                        harForsikring = null,
                        harBruktEgenmeldingsdager = null,
                        egenmeldingsdager = null
                    )

                    assertFailsWith<ValidationException> {
                        sykmeldingUserEvent.validate()
                    }
                }

                test("Skal kaste exception hvis harBruktEgenmeldingsdager == JA, men egenmeldingsdager mangler") {
                    val sykmeldingUserEvent = SykmeldingUserEvent(
                        erOpplysningeneRiktige = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA
                        ),
                        uriktigeOpplysninger = null,
                        arbeidssituasjon = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = ArbeidssituasjonDTO.ARBEIDSTAKER
                        ),
                        arbeidsgiverOrgnummer = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = "543263"
                        ),
                        riktigNarmesteLeder = null,
                        harBruktEgenmelding = null,
                        egenmeldingsperioder = null,
                        harForsikring = null,
                        harBruktEgenmeldingsdager = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA
                        ),
                        egenmeldingsdager = null
                    )
                    invoking { sykmeldingUserEvent.validate() } shouldThrow ValidationException::class withMessage "Spørsmål om egenmeldimngsdager må minst ha 1 dag, når harBruktEgenmeldingsdager er JA"
                }

                test("Skal kaste exception hvis harBruktEgenmeldingsdager == JA, men egenmeldingsdager er en tom liste") {
                    val sykmeldingUserEvent = SykmeldingUserEvent(
                        erOpplysningeneRiktige = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA
                        ),
                        uriktigeOpplysninger = null,
                        arbeidssituasjon = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = ArbeidssituasjonDTO.ARBEIDSTAKER
                        ),
                        arbeidsgiverOrgnummer = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = "543263"
                        ),
                        riktigNarmesteLeder = null,
                        harBruktEgenmelding = null,
                        egenmeldingsperioder = null,
                        harForsikring = null,
                        harBruktEgenmeldingsdager = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA
                        ),
                        egenmeldingsdager = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = emptyList()
                        )
                    )
                    invoking { sykmeldingUserEvent.validate() } shouldThrow ValidationException::class withMessage "Spørsmål om egenmeldimngsdager må minst ha 1 dag, når harBruktEgenmeldingsdager er JA"
                }

                test("Skal IKKE kaste exception hvis harBruktEgenmeldingsdager == JA, men egenmeldingsdager har 1 element") {
                    val sykmeldingUserEvent = SykmeldingUserEvent(
                        erOpplysningeneRiktige = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA
                        ),
                        uriktigeOpplysninger = null,
                        arbeidssituasjon = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = ArbeidssituasjonDTO.ARBEIDSTAKER
                        ),
                        arbeidsgiverOrgnummer = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = "543263"
                        ),
                        riktigNarmesteLeder = null,
                        harBruktEgenmelding = null,
                        egenmeldingsperioder = null,
                        harForsikring = null,
                        harBruktEgenmeldingsdager = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA
                        ),
                        egenmeldingsdager = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = listOf(LocalDate.now())
                        )
                    )
                    invoking { sykmeldingUserEvent.validate() } shouldNotThrow ValidationException::class
                }
            }

            context("frilanser") {
                test("Skal kaste exception hvis harBruktEgenmelding == JA, men egenmeldingsperioder og harForsikring mangler") {
                    val sykmeldingUserEvent = SykmeldingUserEvent(
                        erOpplysningeneRiktige = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA
                        ),
                        uriktigeOpplysninger = null,
                        arbeidssituasjon = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = ArbeidssituasjonDTO.FRILANSER
                        ),
                        arbeidsgiverOrgnummer = null,
                        riktigNarmesteLeder = null,
                        harBruktEgenmelding = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA
                        ),
                        egenmeldingsperioder = null,
                        harForsikring = null,
                        harBruktEgenmeldingsdager = null,
                        egenmeldingsdager = null
                    )

                    assertFailsWith<ValidationException> {
                        sykmeldingUserEvent.validate()
                    }
                }
            }

            context("selvstendig naringsdrivende") {
                test("Skal kaste exception hvis harBruktEgenmelding == JA, men egenmeldingsperioder og harForsikring mangler") {
                    val sykmeldingUserEvent = SykmeldingUserEvent(
                        erOpplysningeneRiktige = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA
                        ),
                        uriktigeOpplysninger = null,
                        arbeidssituasjon = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = ArbeidssituasjonDTO.NAERINGSDRIVENDE
                        ),
                        arbeidsgiverOrgnummer = null,
                        riktigNarmesteLeder = null,
                        harBruktEgenmelding = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA
                        ),
                        egenmeldingsperioder = null,
                        harForsikring = null,
                        harBruktEgenmeldingsdager = null,
                        egenmeldingsdager = null
                    )

                    assertFailsWith<ValidationException> {
                        sykmeldingUserEvent.validate()
                    }
                }
            }

            context("arbeidsledig") {
                test("Skal kaste exception hvis egenmeldingsperioder er satt") {
                    val sykmeldingUserEvent = SykmeldingUserEvent(
                        erOpplysningeneRiktige = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA
                        ),
                        uriktigeOpplysninger = null,
                        arbeidssituasjon = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = ArbeidssituasjonDTO.ARBEIDSLEDIG
                        ),
                        arbeidsgiverOrgnummer = null,
                        riktigNarmesteLeder = null,
                        harBruktEgenmelding = null,
                        egenmeldingsperioder = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = listOf()
                        ),
                        harForsikring = null,
                        harBruktEgenmeldingsdager = null,
                        egenmeldingsdager = null
                    )

                    assertFailsWith<ValidationException> {
                        sykmeldingUserEvent.validate()
                    }
                }

                test("Skal kaste exception hvis harForsikring er satt") {
                    val sykmeldingUserEvent = SykmeldingUserEvent(
                        erOpplysningeneRiktige = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA
                        ),
                        uriktigeOpplysninger = null,
                        arbeidssituasjon = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = ArbeidssituasjonDTO.ARBEIDSLEDIG
                        ),
                        arbeidsgiverOrgnummer = null,
                        riktigNarmesteLeder = null,
                        harBruktEgenmelding = null,
                        egenmeldingsperioder = null,
                        harForsikring = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA
                        ),
                        harBruktEgenmeldingsdager = null,
                        egenmeldingsdager = null
                    )

                    assertFailsWith<ValidationException> {
                        sykmeldingUserEvent.validate()
                    }
                }
            }

            context("annet") {
                test("Skal kaste exception hvis egenmeldingsperioder er satt") {
                    val sykmeldingUserEvent = SykmeldingUserEvent(
                        erOpplysningeneRiktige = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA
                        ),
                        uriktigeOpplysninger = null,
                        arbeidssituasjon = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = ArbeidssituasjonDTO.ANNET
                        ),
                        arbeidsgiverOrgnummer = null,
                        riktigNarmesteLeder = null,
                        harBruktEgenmelding = null,
                        egenmeldingsperioder = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = listOf()
                        ),
                        harForsikring = null,
                        harBruktEgenmeldingsdager = null,
                        egenmeldingsdager = null
                    )

                    assertFailsWith<ValidationException> {
                        sykmeldingUserEvent.validate()
                    }
                }

                test("Skal kaste exception hvis harForsikring er satt") {
                    val sykmeldingUserEvent = SykmeldingUserEvent(
                        erOpplysningeneRiktige = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA
                        ),
                        uriktigeOpplysninger = null,
                        arbeidssituasjon = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = ArbeidssituasjonDTO.ANNET
                        ),
                        arbeidsgiverOrgnummer = null,
                        riktigNarmesteLeder = null,
                        harBruktEgenmelding = null,
                        egenmeldingsperioder = null,
                        harForsikring = SporsmalSvar(
                            sporsmaltekst = "",
                            svar = JaEllerNei.JA
                        ),
                        harBruktEgenmeldingsdager = null,
                        egenmeldingsdager = null
                    )

                    assertFailsWith<ValidationException> {
                        sykmeldingUserEvent.validate()
                    }
                }
            }
        }
    }

    context("SporsmalOgSvar builders") {
        test("Skal lage SporsmalOgSvarDTO for arbeidssituasjon") {
            val sykmeldingUserEvent = SykmeldingUserEvent(
                erOpplysningeneRiktige = SporsmalSvar(
                    sporsmaltekst = "",
                    svar = JaEllerNei.JA
                ),
                uriktigeOpplysninger = null,
                arbeidssituasjon = SporsmalSvar(
                    sporsmaltekst = "",
                    svar = ArbeidssituasjonDTO.FRILANSER
                ),
                arbeidsgiverOrgnummer = null,
                riktigNarmesteLeder = null,
                harBruktEgenmelding = null,
                egenmeldingsperioder = null,
                harForsikring = null,
                harBruktEgenmeldingsdager = null,
                egenmeldingsdager = null
            )

            val sporsmalOgSvarListe = sykmeldingUserEvent.toSporsmalSvarListe(sykmeldingId = "id")

            val expected = listOf(
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.ARBEIDSSITUASJON,
                    SvartypeDTO.ARBEIDSSITUASJON,
                    svar = ArbeidssituasjonDTO.FRILANSER.name
                )
            )

            sporsmalOgSvarListe shouldBeEqualTo expected
        }

        test("Skal lage SporsmalOgSvarDTO for riktigNarmesteLeder med aktiv arbeidsgiver") {
            val sykmeldingUserEvent = SykmeldingUserEvent(
                erOpplysningeneRiktige = SporsmalSvar(
                    sporsmaltekst = "",
                    svar = JaEllerNei.JA
                ),
                uriktigeOpplysninger = null,
                arbeidssituasjon = SporsmalSvar(
                    sporsmaltekst = "",
                    svar = ArbeidssituasjonDTO.ARBEIDSTAKER
                ),
                arbeidsgiverOrgnummer = SporsmalSvar(
                    sporsmaltekst = "",
                    svar = "123456789"
                ),
                riktigNarmesteLeder = SporsmalSvar(
                    sporsmaltekst = "",
                    svar = JaEllerNei.JA
                ),
                harBruktEgenmelding = null,
                egenmeldingsperioder = null,
                harForsikring = null,
                harBruktEgenmeldingsdager = null,
                egenmeldingsdager = null
            )

            val arbeidsgiver = Arbeidsgiverinfo(
                orgnummer = "132456789",
                juridiskOrgnummer = "",
                stillingsprosent = "",
                stilling = "",
                navn = "",
                aktivtArbeidsforhold = true,
                naermesteLeder = null
            )

            val sporsmalOgSvarListe = sykmeldingUserEvent.toSporsmalSvarListe(arbeidsgiver, "id")

            val expected = listOf(
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.ARBEIDSSITUASJON,
                    SvartypeDTO.ARBEIDSSITUASJON,
                    svar = ArbeidssituasjonDTO.ARBEIDSTAKER.name
                ),
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.NY_NARMESTE_LEDER,
                    SvartypeDTO.JA_NEI,
                    svar = JaEllerNei.NEI.name
                )
            )

            sporsmalOgSvarListe shouldBeEqualTo expected
        }

        test("Skal lage SporsmalOgSvarDTO for riktigNarmesteLeder med inaktiv arbeidsgiver") {
            val sykmeldingUserEvent = SykmeldingUserEvent(
                erOpplysningeneRiktige = SporsmalSvar(
                    sporsmaltekst = "",
                    svar = JaEllerNei.JA
                ),
                uriktigeOpplysninger = null,
                arbeidssituasjon = SporsmalSvar(
                    sporsmaltekst = "",
                    svar = ArbeidssituasjonDTO.ARBEIDSTAKER
                ),
                arbeidsgiverOrgnummer = SporsmalSvar(
                    sporsmaltekst = "",
                    svar = "123456789"
                ),
                riktigNarmesteLeder = SporsmalSvar(
                    sporsmaltekst = "",
                    svar = JaEllerNei.JA
                ),
                harBruktEgenmelding = null,
                egenmeldingsperioder = null,
                harForsikring = null,
                harBruktEgenmeldingsdager = null,
                egenmeldingsdager = null
            )

            val arbeidsgiver = Arbeidsgiverinfo(
                orgnummer = "132456789",
                juridiskOrgnummer = "",
                stillingsprosent = "",
                stilling = "",
                navn = "",
                aktivtArbeidsforhold = false,
                naermesteLeder = null
            )

            val sporsmalOgSvarListe = sykmeldingUserEvent.toSporsmalSvarListe(arbeidsgiver, "id")

            val expected = listOf(
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.ARBEIDSSITUASJON,
                    SvartypeDTO.ARBEIDSSITUASJON,
                    svar = ArbeidssituasjonDTO.ARBEIDSTAKER.name
                ),
                SporsmalOgSvarDTO(
                    "Skal finne ny nærmeste leder",
                    ShortNameDTO.NY_NARMESTE_LEDER,
                    SvartypeDTO.JA_NEI,
                    svar = JaEllerNei.NEI.name
                )
            )

            sporsmalOgSvarListe shouldBeEqualTo expected
        }

        test("Skal lage SporsmalOgSvarDTO for fravarSporsmal") {
            val sykmeldingUserEvent = SykmeldingUserEvent(
                erOpplysningeneRiktige = SporsmalSvar(
                    sporsmaltekst = "",
                    svar = JaEllerNei.JA
                ),
                uriktigeOpplysninger = null,
                arbeidssituasjon = SporsmalSvar(
                    sporsmaltekst = "",
                    svar = ArbeidssituasjonDTO.NAERINGSDRIVENDE
                ),
                arbeidsgiverOrgnummer = null,
                riktigNarmesteLeder = null,
                harBruktEgenmelding = SporsmalSvar(
                    sporsmaltekst = "",
                    svar = JaEllerNei.NEI
                ),
                egenmeldingsperioder = null,
                harForsikring = SporsmalSvar(
                    sporsmaltekst = "",
                    svar = JaEllerNei.JA
                ),
                harBruktEgenmeldingsdager = null,
                egenmeldingsdager = null
            )

            val sporsmalOgSvarListe = sykmeldingUserEvent.toSporsmalSvarListe(sykmeldingId = "id")

            val expected = listOf(
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.ARBEIDSSITUASJON,
                    SvartypeDTO.ARBEIDSSITUASJON,
                    svar = ArbeidssituasjonDTO.NAERINGSDRIVENDE.name
                ),
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.FRAVAER,
                    SvartypeDTO.JA_NEI,
                    svar = JaEllerNei.NEI.name
                ),
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.FORSIKRING,
                    SvartypeDTO.JA_NEI,
                    svar = JaEllerNei.JA.name
                )
            )

            sporsmalOgSvarListe shouldBeEqualTo expected
        }

        test("Skal lage SporsmalOgSvarDTO for egenmeldingsperioder") {
            val sykmeldingUserEvent = SykmeldingUserEvent(
                erOpplysningeneRiktige = SporsmalSvar(
                    sporsmaltekst = "",
                    svar = JaEllerNei.JA
                ),
                uriktigeOpplysninger = null,
                arbeidssituasjon = SporsmalSvar(
                    sporsmaltekst = "",
                    svar = ArbeidssituasjonDTO.FRILANSER
                ),
                arbeidsgiverOrgnummer = null,
                riktigNarmesteLeder = null,
                harBruktEgenmelding = SporsmalSvar(
                    sporsmaltekst = "",
                    svar = JaEllerNei.JA
                ),
                egenmeldingsperioder = SporsmalSvar(
                    sporsmaltekst = "",
                    svar = listOf(
                        Egenmeldingsperiode(
                            fom = LocalDate.now(),
                            tom = LocalDate.now()
                        )
                    )
                ),
                harForsikring = null,
                harBruktEgenmeldingsdager = null,
                egenmeldingsdager = null
            )

            val sporsmalOgSvarListe = sykmeldingUserEvent.toSporsmalSvarListe(sykmeldingId = "id")

            val expected = listOf(
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.ARBEIDSSITUASJON,
                    SvartypeDTO.ARBEIDSSITUASJON,
                    svar = ArbeidssituasjonDTO.FRILANSER.name
                ),
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.FRAVAER,
                    SvartypeDTO.JA_NEI,
                    svar = JaEllerNei.JA.name
                ),
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.PERIODE,
                    SvartypeDTO.PERIODER,
                    svar = objectMapper.writeValueAsString(
                        listOf(
                            Egenmeldingsperiode(
                                fom = LocalDate.now(),
                                tom = LocalDate.now()
                            )
                        )
                    )
                )
            )

            sporsmalOgSvarListe shouldBeEqualTo expected
        }
    }
})
