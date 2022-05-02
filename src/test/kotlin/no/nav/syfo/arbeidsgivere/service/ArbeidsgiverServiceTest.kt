package no.nav.syfo.arbeidsgivere.service

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Ansettelsesperiode
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsavtale
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsforhold
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsgiver
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Gyldighetsperiode
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Opplysningspliktig
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Periode
import no.nav.syfo.arbeidsgivere.client.narmesteleder.NarmestelederClient
import no.nav.syfo.arbeidsgivere.client.organisasjon.client.OrganisasjonsinfoClient
import no.nav.syfo.arbeidsgivere.redis.ArbeidsgiverRedisService
import no.nav.syfo.client.OidcToken
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

class ArbeidsgiverServiceTest : Spek({

    val arbeidsforholdClient = mockkClass(ArbeidsforholdClient::class)
    val organisasjonsinfoClient = mockkClass(OrganisasjonsinfoClient::class)
    val narmestelederClient = mockkClass(NarmestelederClient::class)
    val pdlPersonService = mockkClass(PdlPersonService::class)
    val arbeidsgiverRedisService = mockkClass(ArbeidsgiverRedisService::class, relaxed = true)
    val stsOidcToken = mockkClass(StsOidcClient::class)

    val sykmeldingId = "sykmeldingId"

    val arbeidsgiverService = ArbeidsgiverService(
        arbeidsforholdClient,
        organisasjonsinfoClient,
        narmestelederClient,
        pdlPersonService,
        stsOidcToken,
        arbeidsgiverRedisService
    )

    coEvery { stsOidcToken.oidcToken() } returns OidcToken("token", "jwt", 1L)

    beforeEachTest {
        clearMocks(
            arbeidsforholdClient,
            arbeidsgiverRedisService,
            narmestelederClient,
            organisasjonsinfoClient,
            pdlPersonService
        )
        coEvery { arbeidsgiverRedisService.getArbeidsgivere(any()) } returns null
        coEvery { narmestelederClient.getNarmesteledere(any()) } returns getNarmesteledere()
        coEvery { organisasjonsinfoClient.getOrginfo(any()) } returns getOrganisasjonsinfo()
        coEvery { pdlPersonService.getPerson(any(), any(), any(), any()) } returns getPdlPerson()
    }

    describe("Test ArbeidsgiverService") {
        it("arbeidsgiverService returnerer liste med arbeidsforhold") {
            coEvery {
                arbeidsforholdClient.getArbeidsforholdTokenX(
                    any(),
                    any(),
                    any()
                )
            } returns getArbeidsgiverforhold()
            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", sykmeldingId)
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
                arbeidsgiverinformasjon[0].navn shouldBeEqualTo "Navn 1"
                arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
                arbeidsgiverinformasjon[0].stillingsprosent shouldBeEqualTo "100.0"
                arbeidsgiverinformasjon[0].naermesteLeder?.navn shouldBeEqualTo "Leder Ledersen"
                arbeidsgiverinformasjon[0].naermesteLeder?.orgnummer shouldBeEqualTo "123456789"
                arbeidsgiverinformasjon[0].naermesteLeder?.organisasjonsnavn shouldBeEqualTo "Navn 1"
            }
            coVerify { arbeidsgiverRedisService.updateArbeidsgivere(any(), any()) }
        }
        it("arbeidsgiverService returnerer tom liste hvis bruker ikke har arbeidsforhold") {
            coEvery { arbeidsforholdClient.getArbeidsforholdTokenX(any(), any(), any()) } returns emptyList()
            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", sykmeldingId)
                arbeidsgiverinformasjon.size shouldBeEqualTo 0
            }
            coVerify(exactly = 0) { narmestelederClient.getNarmesteledere(any()) }
        }
        it("arbeidsgiverService returnerer tom liste hvis bruker har diskresjonskode") {
            coEvery { pdlPersonService.getPerson(any(), any(), any(), any()) } returns PdlPerson(
                Navn("", "", ""),
                "aktørid",
                diskresjonskode = true
            )
            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", sykmeldingId)
                arbeidsgiverinformasjon.size shouldBeEqualTo 0
            }
            coVerify(exactly = 0) { arbeidsforholdClient.getArbeidsforholdTokenX(any(), any(), any()) }
        }
        it("henter arbeidsgivere fra redis") {
            coEvery { arbeidsgiverRedisService.getArbeidsgivere(any()) } returns listOf(getArbeidsgiverInfoRedisModel())
            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", sykmeldingId)
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
            }
            coVerify(exactly = 0) { arbeidsforholdClient.getArbeidsforholdTokenX(any(), any(), any()) }
            coVerify(exactly = 0) { arbeidsgiverRedisService.updateArbeidsgivere(any(), any()) }
        }

        it("Viser arbeidsforhold som ikke aktivt hvis tom er satt for ansettelsesperiode før dagens dato") {
            coEvery {
                arbeidsforholdClient.getArbeidsforholdTokenX(
                    any(),
                    any(),
                    any()
                )
            } returns getArbeidsgiverforhold(
                ansettelsesperiode = Ansettelsesperiode(
                    Periode(
                        fom = LocalDate.of(2019, 1, 1),
                        tom = LocalDate.now().minusDays(1)
                    )
                )
            )
            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", sykmeldingId)
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
                arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo false
            }
        }
        it("Viser arbeidsforhold som ikke aktivt hvis fom er satt for ansettelsesperiode etter dagens dato") {
            coEvery {
                arbeidsforholdClient.getArbeidsforholdTokenX(
                    any(),
                    any(),
                    any()
                )
            } returns getArbeidsgiverforhold(
                ansettelsesperiode = Ansettelsesperiode(Periode(fom = LocalDate.now().plusDays(1), tom = LocalDate.now().plusDays(10)))
            )
            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", sykmeldingId)
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
                arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo false
            }
        }
        it("Viser arbeidsforhold som aktivt hvis tom-dato er i fremtiden") {
            coEvery {
                arbeidsforholdClient.getArbeidsforholdTokenX(
                    any(),
                    any(),
                    any()
                )
            } returns getArbeidsgiverforhold(
                ansettelsesperiode = Ansettelsesperiode(Periode(fom = LocalDate.now().minusDays(1), tom = LocalDate.now().plusDays(1)))
            )
            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", sykmeldingId)
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
                arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
            }
        }
        it("Bruker stillingsprosent fra nyeste arbeidsavtale") {
            coEvery { arbeidsforholdClient.getArbeidsforholdTokenX(any(), any(), any()) } returns listOf(
                Arbeidsforhold(
                    Arbeidsgiver("Organisasjon", "123456789"),
                    Opplysningspliktig("Organisasjon", "987654321"),
                    Ansettelsesperiode(
                        Periode(fom = LocalDate.of(2020, 6, 1), tom = null)
                    ),
                    listOf(
                        Arbeidsavtale(
                            gyldighetsperiode = Gyldighetsperiode(
                                fom = LocalDate.now().minusYears(1),
                                tom = LocalDate.now()
                            ),
                            stillingsprosent = 100.0
                        ),
                        Arbeidsavtale(
                            gyldighetsperiode = Gyldighetsperiode(
                                fom = LocalDate.now(),
                                tom = null
                            ),
                            stillingsprosent = 50.0
                        )
                    )
                )
            )

            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", sykmeldingId)
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
                arbeidsgiverinformasjon[0].stillingsprosent shouldBeEqualTo "50.0"
            }
        }
        it("Antar 100% stilling hvis arbeidsavtale mangler") {
            coEvery { arbeidsforholdClient.getArbeidsforholdTokenX(any(), any(), any()) } returns listOf(
                Arbeidsforhold(
                    Arbeidsgiver("Organisasjon", "123456789"),
                    Opplysningspliktig("Organisasjon", "987654321"),
                    Ansettelsesperiode(
                        Periode(fom = LocalDate.of(2020, 6, 1), tom = null)
                    ),
                    emptyList()
                )
            )

            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", sykmeldingId)
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
                arbeidsgiverinformasjon[0].stillingsprosent shouldBeEqualTo "100.0"
            }
        }

        it("arbeidsgiverService filtrerer bort duplikate arbeidsforhold for samme orgnummer") {
            coEvery { arbeidsforholdClient.getArbeidsforholdTokenX(any(), any(), any()) } returns listOf(
                Arbeidsforhold(
                    Arbeidsgiver("Organisasjon", "123456789"),
                    Opplysningspliktig("Organisasjon", "987654321"),
                    Ansettelsesperiode(
                        Periode(fom = LocalDate.of(2020, 6, 1), tom = null)
                    ),
                    listOf(
                        Arbeidsavtale(
                            gyldighetsperiode = Gyldighetsperiode(
                                fom = LocalDate.now(),
                                tom = null
                            ),
                            stillingsprosent = 100.0
                        )
                    )
                ),
                Arbeidsforhold(
                    Arbeidsgiver("Organisasjon", "123456789"),
                    Opplysningspliktig("Organisasjon", "987654321"),
                    Ansettelsesperiode(
                        Periode(fom = LocalDate.of(2020, 6, 1), tom = null)
                    ),
                    listOf(
                        Arbeidsavtale(
                            gyldighetsperiode = Gyldighetsperiode(
                                fom = LocalDate.now(),
                                tom = null
                            ),
                            stillingsprosent = 100.0
                        )
                    )
                ),
                Arbeidsforhold(
                    Arbeidsgiver("Organisasjon", "234567891"),
                    Opplysningspliktig("Organisasjon", "987654321"),
                    Ansettelsesperiode(
                        Periode(fom = LocalDate.of(2020, 6, 1), tom = null)
                    ),
                    listOf(
                        Arbeidsavtale(
                            gyldighetsperiode = Gyldighetsperiode(
                                fom = LocalDate.now(),
                                tom = null
                            ),
                            stillingsprosent = 100.0
                        )
                    )
                )
            )
            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", sykmeldingId)
                arbeidsgiverinformasjon.size shouldBeEqualTo 2
                arbeidsgiverinformasjon[0].navn shouldBeEqualTo "Navn 1"
                arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
                arbeidsgiverinformasjon[0].naermesteLeder?.navn shouldBeEqualTo "Leder Ledersen"
                arbeidsgiverinformasjon[0].naermesteLeder?.orgnummer shouldBeEqualTo "123456789"
                arbeidsgiverinformasjon[0].naermesteLeder?.organisasjonsnavn shouldBeEqualTo "Navn 1"
            }
            coVerify { arbeidsgiverRedisService.updateArbeidsgivere(any(), any()) }
        }
        it("arbeidsgiverService velger det aktive arbeidsforholdet ved duplikate arbeidsforhold for samme orgnummer") {
            coEvery { arbeidsforholdClient.getArbeidsforholdTokenX(any(), any(), any()) } returns listOf(
                Arbeidsforhold(
                    Arbeidsgiver("Organisasjon", "123456789"),
                    Opplysningspliktig("Organisasjon", "987654321"),
                    Ansettelsesperiode(
                        Periode(fom = LocalDate.of(2020, 5, 1), tom = LocalDate.of(2020, 6, 1))
                    ),
                    listOf(
                        Arbeidsavtale(
                            gyldighetsperiode = Gyldighetsperiode(
                                fom = LocalDate.now(),
                                tom = LocalDate.now()
                            ),
                            stillingsprosent = 100.0
                        )
                    )
                ),
                Arbeidsforhold(
                    Arbeidsgiver("Organisasjon", "123456789"),
                    Opplysningspliktig("Organisasjon", "987654321"),
                    Ansettelsesperiode(
                        Periode(fom = LocalDate.of(2020, 1, 1), tom = null)
                    ),
                    listOf(
                        Arbeidsavtale(
                            gyldighetsperiode = Gyldighetsperiode(
                                fom = LocalDate.now(),
                                tom = null
                            ),
                            stillingsprosent = 100.0
                        )
                    )
                )
            )
            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", sykmeldingId)
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
                arbeidsgiverinformasjon[0].navn shouldBeEqualTo "Navn 1"
                arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
                arbeidsgiverinformasjon[0].naermesteLeder?.navn shouldBeEqualTo "Leder Ledersen"
                arbeidsgiverinformasjon[0].naermesteLeder?.orgnummer shouldBeEqualTo "123456789"
                arbeidsgiverinformasjon[0].naermesteLeder?.organisasjonsnavn shouldBeEqualTo "Navn 1"
            }
            coVerify { arbeidsgiverRedisService.updateArbeidsgivere(any(), any()) }
        }
        it("arbeidsgiverService velger det aktive arbeidsforholdet ved duplikate arbeidsforhold der alle har satt tom-dato for samme orgnummer") {
            coEvery { arbeidsforholdClient.getArbeidsforholdTokenX(any(), any(), any()) } returns listOf(
                Arbeidsforhold(
                    Arbeidsgiver("Organisasjon", "123456789"),
                    Opplysningspliktig("Organisasjon", "987654321"),
                    Ansettelsesperiode(
                        Periode(fom = LocalDate.of(2021, 3, 12), tom = LocalDate.of(2021, 4, 22))
                    ),
                    listOf(
                        Arbeidsavtale(
                            gyldighetsperiode = Gyldighetsperiode(
                                fom = LocalDate.now(),
                                tom = LocalDate.now()
                            ),
                            stillingsprosent = 100.0
                        )
                    )
                ),
                Arbeidsforhold(
                    Arbeidsgiver("Organisasjon", "123456789"),
                    Opplysningspliktig("Organisasjon", "987654321"),
                    Ansettelsesperiode(
                        Periode(fom = LocalDate.of(2021, 4, 23), tom = LocalDate.of(2021, 9, 1))
                    ),
                    listOf(
                        Arbeidsavtale(
                            gyldighetsperiode = Gyldighetsperiode(
                                fom = LocalDate.now(),
                                tom = LocalDate.now()
                            ),
                            stillingsprosent = 100.0
                        )
                    )
                )
            )
            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", sykmeldingId, date = LocalDate.of(2021, 8, 22))
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
                arbeidsgiverinformasjon[0].navn shouldBeEqualTo "Navn 1"
                arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
                arbeidsgiverinformasjon[0].naermesteLeder?.navn shouldBeEqualTo "Leder Ledersen"
                arbeidsgiverinformasjon[0].naermesteLeder?.orgnummer shouldBeEqualTo "123456789"
                arbeidsgiverinformasjon[0].naermesteLeder?.organisasjonsnavn shouldBeEqualTo "Navn 1"
            }
            coVerify { arbeidsgiverRedisService.updateArbeidsgivere(any(), any()) }
        }
    }
})
