package no.nav.syfo.arbeidsgivere.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Gyldighetsperiode
import no.nav.syfo.arbeidsgivere.client.narmesteleder.NarmestelederClient
import no.nav.syfo.arbeidsgivere.client.organisasjon.client.OrganisasjonsinfoClient
import no.nav.syfo.arbeidsgivere.redis.ArbeidsgiverRedisService
import no.nav.syfo.client.OidcToken
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.pdl.service.PdlPersonService
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

@KtorExperimentalAPI
class ArbeidsgiverServiceTest : Spek({

    val arbeidsforholdClient = mockkClass(ArbeidsforholdClient::class)
    val organisasjonsinfoClient = mockkClass(OrganisasjonsinfoClient::class)
    val narmestelederClient = mockkClass(NarmestelederClient::class)
    val pdlPersonService = mockkClass(PdlPersonService::class)
    val arbeidsgiverRedisService = mockkClass(ArbeidsgiverRedisService::class, relaxed = true)
    val stsOidcToken = mockkClass(StsOidcClient::class)

    val sykmeldingId = "sykmeldingId"

    val arbeidsgiverService = ArbeidsgiverService(arbeidsforholdClient, organisasjonsinfoClient, narmestelederClient, pdlPersonService, stsOidcToken, arbeidsgiverRedisService)

    coEvery { organisasjonsinfoClient.getOrginfo(any()) } returns getOrganisasjonsinfo()
    coEvery { narmestelederClient.getNarmesteledere(any()) } returns getNarmestelederRelasjoner()
    coEvery { pdlPersonService.getPerson(any(), any(), any(), any()) } returns getPdlPerson()
    coEvery { stsOidcToken.oidcToken() } returns OidcToken("token", "jwt", 1L)

    beforeEachTest {
        clearMocks(arbeidsforholdClient, arbeidsgiverRedisService)
        coEvery { arbeidsgiverRedisService.getArbeidsgivere(any()) } returns null
    }

    describe("Test ArbeidsgiverService") {
        it("arbeidsgiverService should return list") {
            runBlocking {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any()) } returns getArbeidsgiverforhold(
                    Gyldighetsperiode(fom = LocalDate.now(), tom = null)
                )

                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", LocalDate.now(), sykmeldingId)
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
                arbeidsgiverinformasjon[0].navn shouldBeEqualTo "Navn 1"
                arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
                arbeidsgiverinformasjon[0].naermesteLeder?.aktoerId shouldBeEqualTo "nlAktorId"
                arbeidsgiverinformasjon[0].naermesteLeder?.navn shouldBeEqualTo "Leder Ledersen"
                arbeidsgiverinformasjon[0].naermesteLeder?.orgnummer shouldBeEqualTo "123456789"
                arbeidsgiverinformasjon[0].naermesteLeder?.organisasjonsnavn shouldBeEqualTo "Navn 1"
            }
            coVerify { arbeidsgiverRedisService.updateArbeidsgivere(any(), any()) }
        }

        it("hente arbeidsgivere fra redis") {
            coEvery { arbeidsgiverRedisService.getArbeidsgivere(any()) } returns listOf(getArbeidsgiverInfoRedisModel())
            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", LocalDate.now(), sykmeldingId)
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
            }
            coVerify(exactly = 0) { arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any()) }
            coVerify(exactly = 0) { arbeidsgiverRedisService.updateArbeidsgivere(any(), any()) }
        }

        it("Skal ikke hente arbeidsgiver når dato er før FOM dato i arbeidsavtale") {
            runBlocking {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any()) } returns getArbeidsgiverforhold(
                    Gyldighetsperiode(fom = LocalDate.now().plusDays(1), tom = LocalDate.now().plusMonths(1))
                )
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", LocalDate.now(), sykmeldingId)
                arbeidsgiverinformasjon.size shouldBeEqualTo 0
            }
        }

        it("Skal ikke hente arbeidsgiver når dato er etter TOM dato i arbeidsavtale") {
            runBlocking {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any()) } returns getArbeidsgiverforhold(
                    Gyldighetsperiode(fom = LocalDate.now().plusDays(1), tom = LocalDate.now().plusDays(2))
                )
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", LocalDate.now().plusDays(3), sykmeldingId)
                arbeidsgiverinformasjon.size shouldBeEqualTo 0
            }
        }

        it("Skal hente arbeidsgiver når dato er etter FOM og TOM er null i arbeidsavtale") {
            runBlocking {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any()) } returns getArbeidsgiverforhold(
                    Gyldighetsperiode(fom = LocalDate.now().plusDays(1), tom = null)
                )
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", LocalDate.now().plusDays(3), sykmeldingId)
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
            }
        }
        it("Skal ikke hente arbeidsgiver når FOM null i arbeidsavtale") {
            runBlocking {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any()) } returns getArbeidsgiverforhold(
                    Gyldighetsperiode(fom = null, tom = null)
                )
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", LocalDate.now().plusDays(3), sykmeldingId)
                arbeidsgiverinformasjon.size shouldBeEqualTo 0
            }
        }
    }
})
