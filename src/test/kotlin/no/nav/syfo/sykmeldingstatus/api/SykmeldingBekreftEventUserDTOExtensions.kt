package no.nav.syfo.sykmeldingstatus.api

import no.nav.syfo.objectMapper
import no.nav.syfo.sykmeldingstatus.api.v1.ArbeidssituasjonDTO
import no.nav.syfo.sykmeldingstatus.api.v1.Egenmeldingsperiode
import no.nav.syfo.sykmeldingstatus.api.v1.JaEllerNei
import no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SporsmalOgSvarDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SporsmalSvar
import no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingBekreftEventUserDTOv2
import no.nav.syfo.sykmeldingstatus.api.v1.toSporsmalSvarListe
import no.nav.syfo.sykmeldingstatus.api.v1.validate
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import kotlin.test.assertFailsWith

class SykmeldingBekreftEventUserDTOExtensions : Spek({
    describe("Validation") {
        describe("erOpplysnigeneRiktige") {
            it("Skal kaste exception hvis opplysningene ikke stemmer, men uriktige opplysninger er tom") {
                val sykmeldingBekreftEventUserDTO = SykmeldingBekreftEventUserDTOv2(
                    erOpplysnigeneRiktige = SporsmalSvar(
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

                assertFailsWith<IllegalArgumentException> {
                    sykmeldingBekreftEventUserDTO.validate()
                }
            }
        }

        describe("arbeidssituasjon") {
            describe("arbeidsgiver") {
                it("Skal kaste exception hvis arbeidssituasjon == ARBEIDSGIVER, men arbeidsgiverOrgnummer og nyNarmesteLeder mangler") {
                    val sykmeldingBekreftEventUserDTO = SykmeldingBekreftEventUserDTOv2(

                        erOpplysnigeneRiktige = SporsmalSvar(
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

                    assertFailsWith<IllegalArgumentException> {
                        sykmeldingBekreftEventUserDTO.validate()
                    }
                }

                it("Skal kaste exception hvis arbeidssituasjon != ARBEIDSGIVER, men arbeidsgiverOrgnummer og nyNarmesteLeder er satt") {
                    val sykmeldingBekreftEventUserDTO = SykmeldingBekreftEventUserDTOv2(
                        erOpplysnigeneRiktige = SporsmalSvar(
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

                    assertFailsWith<IllegalArgumentException> {
                        sykmeldingBekreftEventUserDTO.validate()
                    }
                }

                it("Skal kaste exception hvis harBruktEgenmelding == JA, men egenmeldingsperioder og harForsikring mangler") {
                    val sykmeldingBekreftEventUserDTO = SykmeldingBekreftEventUserDTOv2(
                        erOpplysnigeneRiktige = SporsmalSvar(
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

                    assertFailsWith<IllegalArgumentException> {
                        sykmeldingBekreftEventUserDTO.validate()
                    }
                }
            }

            describe("frilanser") {
                it("Skal kaste exception hvis harBruktEgenmelding == JA, men egenmeldingsperioder og harForsikring mangler") {
                    val sykmeldingBekreftEventUserDTO = SykmeldingBekreftEventUserDTOv2(
                        erOpplysnigeneRiktige = SporsmalSvar(
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

                    assertFailsWith<IllegalArgumentException> {
                        sykmeldingBekreftEventUserDTO.validate()
                    }
                }
            }

            describe("selvstendig naringsdrivende") {
                it("Skal kaste exception hvis harBruktEgenmelding == JA, men egenmeldingsperioder og harForsikring mangler") {
                    val sykmeldingBekreftEventUserDTO = SykmeldingBekreftEventUserDTOv2(
                        erOpplysnigeneRiktige = SporsmalSvar(
                            sporsmaltekst = "",
                            svartekster = "",
                            svar = JaEllerNei.JA,
                        ),
                        uriktigeOpplysninger = null,
                        arbeidssituasjon = SporsmalSvar(
                            sporsmaltekst = "",
                            svartekster = "",
                            svar = ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE,
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

                    assertFailsWith<IllegalArgumentException> {
                        sykmeldingBekreftEventUserDTO.validate()
                    }
                }
            }

            describe("arbeidsledig") {
                it("Skal kaste exception hvis egenmeldingsperioder er satt") {
                    val sykmeldingBekreftEventUserDTO = SykmeldingBekreftEventUserDTOv2(
                        erOpplysnigeneRiktige = SporsmalSvar(
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

                    assertFailsWith<IllegalArgumentException> {
                        sykmeldingBekreftEventUserDTO.validate()
                    }
                }

                it("Skal kaste exception hvis harForsikring er satt") {
                    val sykmeldingBekreftEventUserDTO = SykmeldingBekreftEventUserDTOv2(
                        erOpplysnigeneRiktige = SporsmalSvar(
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

                    assertFailsWith<IllegalArgumentException> {
                        sykmeldingBekreftEventUserDTO.validate()
                    }
                }
            }

            describe("permittert") {
                it("Skal kaste exception hvis egenmeldingsperioder er satt") {
                    val sykmeldingBekreftEventUserDTO = SykmeldingBekreftEventUserDTOv2(
                        erOpplysnigeneRiktige = SporsmalSvar(
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

                    assertFailsWith<IllegalArgumentException> {
                        sykmeldingBekreftEventUserDTO.validate()
                    }
                }

                it("Skal kaste exception hvis harForsikring er satt") {
                    val sykmeldingBekreftEventUserDTO = SykmeldingBekreftEventUserDTOv2(
                        erOpplysnigeneRiktige = SporsmalSvar(
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

                    assertFailsWith<IllegalArgumentException> {
                        sykmeldingBekreftEventUserDTO.validate()
                    }
                }
            }

            describe("annet") {
                it("Skal kaste exception hvis egenmeldingsperioder er satt") {
                    val sykmeldingBekreftEventUserDTO = SykmeldingBekreftEventUserDTOv2(
                        erOpplysnigeneRiktige = SporsmalSvar(
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

                    assertFailsWith<IllegalArgumentException> {
                        sykmeldingBekreftEventUserDTO.validate()
                    }
                }

                it("Skal kaste exception hvis harForsikring er satt") {
                    val sykmeldingBekreftEventUserDTO = SykmeldingBekreftEventUserDTOv2(
                        erOpplysnigeneRiktige = SporsmalSvar(
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

                    assertFailsWith<IllegalArgumentException> {
                        sykmeldingBekreftEventUserDTO.validate()
                    }
                }
            }
        }
    }

    describe("SporsmalOgSvar builders") {
        it("Skal lage SporsmalOgSvarDTO for arbeidssituasjon") {
            val sykmeldingBekreftEventUserDTO = SykmeldingBekreftEventUserDTOv2(
                erOpplysnigeneRiktige = SporsmalSvar(
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

            val sporsmalOgSvarListe = sykmeldingBekreftEventUserDTO.toSporsmalSvarListe()

            val expected = listOf(
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.ARBEIDSSITUASJON,
                    SvartypeDTO.ARBEIDSSITUASJON,
                    svar = objectMapper.writeValueAsString(ArbeidssituasjonDTO.FRILANSER.name),
                )
            )

            sporsmalOgSvarListe shouldBeEqualTo expected
        }

        it("Skal lage SporsmalOgSvarDTO for nyNarmesteLeder") {
            val sykmeldingBekreftEventUserDTO = SykmeldingBekreftEventUserDTOv2(
                erOpplysnigeneRiktige = SporsmalSvar(
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

            val sporsmalOgSvarListe = sykmeldingBekreftEventUserDTO.toSporsmalSvarListe()

            val expected = listOf(
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.ARBEIDSSITUASJON,
                    SvartypeDTO.ARBEIDSSITUASJON,
                    svar = objectMapper.writeValueAsString(ArbeidssituasjonDTO.ARBEIDSTAKER.name),
                ),
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.NY_NARMESTE_LEDER,
                    SvartypeDTO.JA_NEI,
                    svar = objectMapper.writeValueAsString(JaEllerNei.JA.name),
                )
            )

            sporsmalOgSvarListe shouldBeEqualTo expected
        }

        it("Skal lage SporsmalOgSvarDTO for fravarSporsmal") {
            val sykmeldingBekreftEventUserDTO = SykmeldingBekreftEventUserDTOv2(
                erOpplysnigeneRiktige = SporsmalSvar(
                    sporsmaltekst = "",
                    svartekster = "",
                    svar = JaEllerNei.JA,
                ),
                uriktigeOpplysninger = null,
                arbeidssituasjon = SporsmalSvar(
                    sporsmaltekst = "",
                    svartekster = "",
                    svar = ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE,
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

            val sporsmalOgSvarListe = sykmeldingBekreftEventUserDTO.toSporsmalSvarListe()

            val expected = listOf(
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.ARBEIDSSITUASJON,
                    SvartypeDTO.ARBEIDSSITUASJON,
                    svar = objectMapper.writeValueAsString(ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE.name),
                ),
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.FRAVAER,
                    SvartypeDTO.JA_NEI,
                    svar = objectMapper.writeValueAsString(JaEllerNei.NEI.name),
                ),
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.FORSIKRING,
                    SvartypeDTO.JA_NEI,
                    svar = objectMapper.writeValueAsString(JaEllerNei.JA.name),
                )
            )

            sporsmalOgSvarListe shouldBeEqualTo expected
        }

        it("Skal lage SporsmalOgSvarDTO for egenmeldingsperioder") {
            val sykmeldingBekreftEventUserDTO = SykmeldingBekreftEventUserDTOv2(
                erOpplysnigeneRiktige = SporsmalSvar(
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

            val sporsmalOgSvarListe = sykmeldingBekreftEventUserDTO.toSporsmalSvarListe()

            val expected = listOf(
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.ARBEIDSSITUASJON,
                    SvartypeDTO.ARBEIDSSITUASJON,
                    svar = objectMapper.writeValueAsString(ArbeidssituasjonDTO.FRILANSER.name),
                ),
                SporsmalOgSvarDTO(
                    "",
                    ShortNameDTO.FRAVAER,
                    SvartypeDTO.JA_NEI,
                    svar = objectMapper.writeValueAsString(JaEllerNei.JA.name),
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
