package no.nav.syfo.sykmeldingstatus.api.v2

import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmeldingstatus.kafka.toSporsmalSvarListe
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import kotlin.test.assertFailsWith

class ValidationKtTest : Spek({
    describe("Validation") {
        describe("erOpplysnigeneRiktige") {
            it("Skal kaste exception hvis opplysningene ikke stemmer, men uriktige opplysninger er tom") {
                val sykmeldingUserEvent = SykmeldingUserEvent(
                    erOpplysningeneRiktige = SporsmalSvar(
                        sporsmaltekst = "",
                        svartekster = "",
                        svar = JaEllerNei.NEI,
                    ),
                    uriktigeOpplysninger = null,
                    arbeidssituasjon = SporsmalSvar(
                        sporsmaltekst = "",
                        svartekster = "",
                        svar = ArbeidssituasjonDTO.ANNET,
                    ),
                    arbeidsgiverOrgnummer = null,
                    nyNarmesteLeder = null,
                    harBruktEgenmelding = null,
                    egenmeldingsperioder = null,
                    harForsikring = null,
                )

                assertFailsWith<ValidationException> {
                    sykmeldingUserEvent.validate()
                }
            }
        }

        describe("arbeidssituasjon") {
            describe("arbeidsgiver") {
                it("Skal kaste exception hvis arbeidssituasjon == ARBEIDSGIVER, men arbeidsgiverOrgnummer og nyNarmesteLeder mangler") {
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
                        arbeidsgiverOrgnummer = null,
                        nyNarmesteLeder = null,
                        harBruktEgenmelding = null,
                        egenmeldingsperioder = null,
                        harForsikring = null,
                    )

                    assertFailsWith<ValidationException> {
                        sykmeldingUserEvent.validate()
                    }
                }

                it("Skal kaste exception hvis arbeidssituasjon != ARBEIDSGIVER, men arbeidsgiverOrgnummer og nyNarmesteLeder er satt") {
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
                            svar = ArbeidssituasjonDTO.ANNET,
                        ),
                        arbeidsgiverOrgnummer = SporsmalSvar(
                            sporsmaltekst = "",
                            svartekster = "",
                            svar = "13456789"
                        ),
                        nyNarmesteLeder = SporsmalSvar(
                            sporsmaltekst = "",
                            svartekster = "",
                            svar = JaEllerNei.JA,
                        ),
                        harBruktEgenmelding = null,
                        egenmeldingsperioder = null,
                        harForsikring = null,
                    )

                    assertFailsWith<ValidationException> {
                        sykmeldingUserEvent.validate()
                    }
                }

                it("Skal kaste exception hvis harBruktEgenmelding == JA, men egenmeldingsperioder og harForsikring mangler") {
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
                        arbeidsgiverOrgnummer = null,
                        nyNarmesteLeder = null,
                        harBruktEgenmelding = null,
                        egenmeldingsperioder = null,
                        harForsikring = null,
                    )

                    assertFailsWith<ValidationException> {
                        sykmeldingUserEvent.validate()
                    }
                }
            }

            describe("frilanser") {
                it("Skal kaste exception hvis harBruktEgenmelding == JA, men egenmeldingsperioder og harForsikring mangler") {
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
                        nyNarmesteLeder = null,
                        harBruktEgenmelding = SporsmalSvar(
                            sporsmaltekst = "",
                            svartekster = "",
                            svar = JaEllerNei.JA,
                        ),
                        egenmeldingsperioder = null,
                        harForsikring = null,
                    )

                    assertFailsWith<ValidationException> {
                        sykmeldingUserEvent.validate()
                    }
                }
            }

            describe("selvstendig naringsdrivende") {
                it("Skal kaste exception hvis harBruktEgenmelding == JA, men egenmeldingsperioder og harForsikring mangler") {
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
                            svar = ArbeidssituasjonDTO.NAERINGSDRIVENDE,
                        ),
                        arbeidsgiverOrgnummer = null,
                        nyNarmesteLeder = null,
                        harBruktEgenmelding = SporsmalSvar(
                            sporsmaltekst = "",
                            svartekster = "",
                            svar = JaEllerNei.JA,
                        ),
                        egenmeldingsperioder = null,
                        harForsikring = null,
                    )

                    assertFailsWith<ValidationException> {
                        sykmeldingUserEvent.validate()
                    }
                }
            }

            describe("arbeidsledig") {
                it("Skal kaste exception hvis egenmeldingsperioder er satt") {
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
                            svar = ArbeidssituasjonDTO.ARBEIDSLEDIG,
                        ),
                        arbeidsgiverOrgnummer = null,
                        nyNarmesteLeder = null,
                        harBruktEgenmelding = null,
                        egenmeldingsperioder = SporsmalSvar(
                            sporsmaltekst = "",
                            svartekster = "",
                            svar = listOf(),
                        ),
                        harForsikring = null,
                    )

                    assertFailsWith<ValidationException> {
                        sykmeldingUserEvent.validate()
                    }
                }

                it("Skal kaste exception hvis harForsikring er satt") {
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
                            svar = ArbeidssituasjonDTO.ARBEIDSLEDIG,
                        ),
                        arbeidsgiverOrgnummer = null,
                        nyNarmesteLeder = null,
                        harBruktEgenmelding = null,
                        egenmeldingsperioder = null,
                        harForsikring = SporsmalSvar(
                            sporsmaltekst = "",
                            svartekster = "",
                            svar = JaEllerNei.JA,
                        ),
                    )

                    assertFailsWith<ValidationException> {
                        sykmeldingUserEvent.validate()
                    }
                }
            }

            describe("permittert") {
                it("Skal kaste exception hvis egenmeldingsperioder er satt") {
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
                            svar = ArbeidssituasjonDTO.PERMITTERT,
                        ),
                        arbeidsgiverOrgnummer = null,
                        nyNarmesteLeder = null,
                        harBruktEgenmelding = null,
                        egenmeldingsperioder = SporsmalSvar(
                            sporsmaltekst = "",
                            svartekster = "",
                            svar = listOf(),
                        ),
                        harForsikring = null,
                    )

                    assertFailsWith<ValidationException> {
                        sykmeldingUserEvent.validate()
                    }
                }

                it("Skal kaste exception hvis harForsikring er satt") {
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
                            svar = ArbeidssituasjonDTO.PERMITTERT,
                        ),
                        arbeidsgiverOrgnummer = null,
                        nyNarmesteLeder = null,
                        harBruktEgenmelding = null,
                        egenmeldingsperioder = null,
                        harForsikring = SporsmalSvar(
                            sporsmaltekst = "",
                            svartekster = "",
                            svar = JaEllerNei.JA,
                        ),
                    )

                    assertFailsWith<ValidationException> {
                        sykmeldingUserEvent.validate()
                    }
                }
            }

            describe("annet") {
                it("Skal kaste exception hvis egenmeldingsperioder er satt") {
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
                            svar = ArbeidssituasjonDTO.ANNET,
                        ),
                        arbeidsgiverOrgnummer = null,
                        nyNarmesteLeder = null,
                        harBruktEgenmelding = null,
                        egenmeldingsperioder = SporsmalSvar(
                            sporsmaltekst = "",
                            svartekster = "",
                            svar = listOf(),
                        ),
                        harForsikring = null,
                    )

                    assertFailsWith<ValidationException> {
                        sykmeldingUserEvent.validate()
                    }
                }

                it("Skal kaste exception hvis harForsikring er satt") {
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
                            svar = ArbeidssituasjonDTO.ANNET,
                        ),
                        arbeidsgiverOrgnummer = null,
                        nyNarmesteLeder = null,
                        harBruktEgenmelding = null,
                        egenmeldingsperioder = null,
                        harForsikring = SporsmalSvar(
                            sporsmaltekst = "",
                            svartekster = "",
                            svar = JaEllerNei.JA,
                        ),
                    )

                    assertFailsWith<ValidationException> {
                        sykmeldingUserEvent.validate()
                    }
                }
            }
        }
    }

    describe("SporsmalOgSvar builders") {
        it("Skal lage SporsmalOgSvarDTO for arbeidssituasjon") {
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
                nyNarmesteLeder = null,
                harBruktEgenmelding = null,
                egenmeldingsperioder = null,
                harForsikring = null,
            )

            val sporsmalOgSvarListe = sykmeldingUserEvent.toSporsmalSvarListe()

            val expected = listOf(
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.ARBEIDSSITUASJON,
                    SvartypeDTO.ARBEIDSSITUASJON,
                    svar = ArbeidssituasjonDTO.FRILANSER.name,
                )
            )

            sporsmalOgSvarListe shouldBeEqualTo expected
        }

        it("Skal lage SporsmalOgSvarDTO for nyNarmesteLeder med aktiv arbeidsgiver") {
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
                nyNarmesteLeder = SporsmalSvar(
                    sporsmaltekst = "",
                    svartekster = "",
                    svar = JaEllerNei.JA,
                ),
                harBruktEgenmelding = null,
                egenmeldingsperioder = null,
                harForsikring = null,
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

            val sporsmalOgSvarListe = sykmeldingUserEvent.toSporsmalSvarListe(arbeidsgiver)

            val expected = listOf(
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.ARBEIDSSITUASJON,
                    SvartypeDTO.ARBEIDSSITUASJON,
                    svar = ArbeidssituasjonDTO.ARBEIDSTAKER.name,
                ),
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.NY_NARMESTE_LEDER,
                    SvartypeDTO.JA_NEI,
                    svar = JaEllerNei.JA.name,
                )
            )

            sporsmalOgSvarListe shouldBeEqualTo expected
        }

        it("Skal lage SporsmalOgSvarDTO for nyNarmesteLeder med inaktiv arbeidsgiver") {
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
                nyNarmesteLeder = SporsmalSvar(
                    sporsmaltekst = "",
                    svartekster = "",
                    svar = JaEllerNei.JA,
                ),
                harBruktEgenmelding = null,
                egenmeldingsperioder = null,
                harForsikring = null,
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

            val sporsmalOgSvarListe = sykmeldingUserEvent.toSporsmalSvarListe(arbeidsgiver)

            val expected = listOf(
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.ARBEIDSSITUASJON,
                    SvartypeDTO.ARBEIDSSITUASJON,
                    svar = ArbeidssituasjonDTO.ARBEIDSTAKER.name,
                ),
                SporsmalOgSvarDTO(
                    "Skal finne ny nærmeste leder",
                    ShortNameDTO.NY_NARMESTE_LEDER,
                    SvartypeDTO.JA_NEI,
                    svar = JaEllerNei.NEI.name,
                )
            )

            sporsmalOgSvarListe shouldBeEqualTo expected
        }

        it("Skal lage SporsmalOgSvarDTO for fravarSporsmal") {
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
                    svar = ArbeidssituasjonDTO.NAERINGSDRIVENDE,
                ),
                arbeidsgiverOrgnummer = null,
                nyNarmesteLeder = null,
                harBruktEgenmelding = SporsmalSvar(
                    sporsmaltekst = "",
                    svartekster = "",
                    svar = JaEllerNei.NEI,
                ),
                egenmeldingsperioder = null,
                harForsikring = SporsmalSvar(
                    sporsmaltekst = "",
                    svartekster = "",
                    svar = JaEllerNei.JA,
                ),
            )

            val sporsmalOgSvarListe = sykmeldingUserEvent.toSporsmalSvarListe()

            val expected = listOf(
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.ARBEIDSSITUASJON,
                    SvartypeDTO.ARBEIDSSITUASJON,
                    svar = ArbeidssituasjonDTO.NAERINGSDRIVENDE.name,
                ),
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.FRAVAER,
                    SvartypeDTO.JA_NEI,
                    svar = JaEllerNei.NEI.name,
                ),
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.FORSIKRING,
                    SvartypeDTO.JA_NEI,
                    svar = JaEllerNei.JA.name,
                )
            )

            sporsmalOgSvarListe shouldBeEqualTo expected
        }

        it("Skal lage SporsmalOgSvarDTO for egenmeldingsperioder") {
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
                nyNarmesteLeder = null,
                harBruktEgenmelding = SporsmalSvar(
                    sporsmaltekst = "",
                    svartekster = "",
                    svar = JaEllerNei.JA,
                ),
                egenmeldingsperioder = SporsmalSvar(
                    sporsmaltekst = "",
                    svartekster = "",
                    svar = listOf(
                        Egenmeldingsperiode(
                            fom = LocalDate.now(),
                            tom = LocalDate.now(),
                        )
                    )
                ),
                harForsikring = null,
            )

            val sporsmalOgSvarListe = sykmeldingUserEvent.toSporsmalSvarListe()

            val expected = listOf(
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.ARBEIDSSITUASJON,
                    SvartypeDTO.ARBEIDSSITUASJON,
                    svar = ArbeidssituasjonDTO.FRILANSER.name,
                ),
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.FRAVAER,
                    SvartypeDTO.JA_NEI,
                    svar = JaEllerNei.JA.name,
                ),
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.PERIODE,
                    SvartypeDTO.PERIODER,
                    svar = objectMapper.writeValueAsString(
                        listOf(
                            Egenmeldingsperiode(
                                fom = LocalDate.now(),
                                tom = LocalDate.now(),
                            )
                        )
                    ),
                )
            )

            sporsmalOgSvarListe shouldBeEqualTo expected
        }
    }
})
