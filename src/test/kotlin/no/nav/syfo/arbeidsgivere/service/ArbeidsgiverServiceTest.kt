package no.nav.syfo.arbeidsgivere.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Gyldighetsperiode
import no.nav.syfo.arbeidsgivere.client.organisasjon.client.OrganisasjonsinfoClient
import no.nav.syfo.client.OidcToken
import no.nav.syfo.client.StsOidcClient
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

@KtorExperimentalAPI
class ArbeidsgiverServiceTest : Spek({

    val arbeidsforholdClient = mockkClass(ArbeidsforholdClient::class)
    val organisasjonsinfoClient = mockkClass(OrganisasjonsinfoClient::class)
    val stsOidcToken = mockkClass(StsOidcClient::class)

    val arbeidsgiverService = ArbeidsgiverService(arbeidsforholdClient, organisasjonsinfoClient, stsOidcToken)
    coEvery { organisasjonsinfoClient.getOrginfo(any()) } returns getOrganisasjonsinfo()
    coEvery { stsOidcToken.oidcToken() } returns OidcToken("token", "jwt", 1L)
    describe("Test ArbeidsgiverService") {
        it("arbeidsgiverService should return list") {
            runBlocking {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any()) } returns getArbeidsgiverforhold()

                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", LocalDate.now())
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
                arbeidsgiverinformasjon[0].navn shouldBeEqualTo "Navn 1"
            }
        }

        it("Skal ikke hente arbeidsgiver når dato er før FOM dato i arbeidsavtale") {
            runBlocking {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any()) } returns getArbeidsgiverforhold(
                    Gyldighetsperiode(fom = LocalDate.now().plusDays(1), tom = LocalDate.now().plusMonths(1))
                )
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", LocalDate.now())
                arbeidsgiverinformasjon.size shouldBeEqualTo 0
            }
        }

        it("Skal ikke hente arbeidsgiver når dato er etter TOM dato i arbeidsavtale") {
            runBlocking {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any()) } returns getArbeidsgiverforhold(
                    Gyldighetsperiode(fom = LocalDate.now().plusDays(1), tom = LocalDate.now().plusDays(2))
                )
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", LocalDate.now().plusDays(3))
                arbeidsgiverinformasjon.size shouldBeEqualTo 0
            }
        }

        it("Skal hente arbeidsgiver når dato er etter FOM og TOM er null i arbeidsavtale") {
            runBlocking {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any()) } returns getArbeidsgiverforhold(
                    Gyldighetsperiode(fom = LocalDate.now().plusDays(1), tom = null)
                )
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", LocalDate.now().plusDays(3))
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
            }
        }
        it("Skal ikke hente arbeidsgiver når FOM null i arbeidsavtale") {
            runBlocking {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any()) } returns getArbeidsgiverforhold(
                    Gyldighetsperiode(fom = null, tom = null)
                )
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", LocalDate.now().plusDays(3))
                arbeidsgiverinformasjon.size shouldBeEqualTo 0
            }
        }
    }
})
