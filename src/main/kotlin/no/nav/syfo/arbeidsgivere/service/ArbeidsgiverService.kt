package no.nav.syfo.arbeidsgivere.service

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsavtale
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsforhold
import no.nav.syfo.arbeidsgivere.client.narmesteleder.NarmesteLederRelasjon
import no.nav.syfo.arbeidsgivere.client.narmesteleder.NarmestelederClient
import no.nav.syfo.arbeidsgivere.client.organisasjon.client.OrganisasjonsinfoClient
import no.nav.syfo.arbeidsgivere.client.organisasjon.model.Navn
import no.nav.syfo.arbeidsgivere.client.organisasjon.model.Organisasjonsinfo
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.model.NarmesteLeder
import no.nav.syfo.client.StsOidcClient
import java.time.LocalDate
import kotlin.random.Random

@KtorExperimentalAPI
class ArbeidsgiverService(
    private val arbeidsforholdClient: ArbeidsforholdClient,
    private val organisasjonsinfoClient: OrganisasjonsinfoClient,
    private val narmestelederClient: NarmestelederClient,
    private val stsOidcClient: StsOidcClient
) {
    suspend fun getArbeidsgivere(fnr: String, token: String, date: LocalDate): List<Arbeidsgiverinfo> {
        val stsToken = stsOidcClient.oidcToken()
        val ansettelsesperiodeFom = LocalDate.now().minusMonths(4) // må sjekkes
        val arbeidsgivere = arbeidsforholdClient.getArbeidsforhold(fnr = fnr, ansettelsesperiodeFom = ansettelsesperiodeFom, token = token, stsToken = stsToken.access_token)
        val aktiveNarmesteledere = narmestelederClient.getNarmesteledere("aktørid").filter { it.aktivTom == null } // bruk aktørid
        val arbeidsgiverList = ArrayList<Arbeidsgiverinfo>()
        arbeidsgivere.filter {
            it.arbeidsgiver.type == "Organisasjon"
        }.forEach { arbeidsforhold ->
            val organisasjonsinfo =
                organisasjonsinfoClient.getOrginfo(arbeidsforhold.arbeidsgiver.organisasjonsnummer!!)
            val narmesteLeder = aktiveNarmesteledere.find { it.orgnummer == arbeidsforhold.arbeidsgiver.organisasjonsnummer }
            arbeidsforhold.arbeidsavtaler.asSequence().filter {
                checkGyldighetsperiode(it, date)
            }.forEach { arbeidsavtale ->
                addArbeidsinfo(arbeidsgiverList, organisasjonsinfo, arbeidsavtale, arbeidsforhold, narmesteLeder)
            }
        }
        return arbeidsgiverList
    }

    private fun addArbeidsinfo(
        arbeidsgiverList: ArrayList<Arbeidsgiverinfo>,
        organisasjonsinfo: Organisasjonsinfo,
        arbeidsavtale: Arbeidsavtale,
        arbeidsforhold: Arbeidsforhold,
        narmesteLederRelasjon: NarmesteLederRelasjon?
    ) {
        val orgnavn = getName(organisasjonsinfo.navn)
        arbeidsgiverList.add(
            Arbeidsgiverinfo(
                orgnummer = organisasjonsinfo.organisasjonsnummer,
                juridiskOrgnummer = arbeidsforhold.opplysningspliktig.organisasjonsnummer!!,
                navn = orgnavn,
                stilling = arbeidsavtale.stillingsprosent.toString(),
                aktivtArbeidsforhold = arbeidsavtale.gyldighetsperiode.tom == null,
                naermesteLeder = narmesteLederRelasjon?.tilNarmesteLeder(orgnavn)
            )
        )
    }

    private fun checkGyldighetsperiode(it: Arbeidsavtale, date: LocalDate): Boolean {
        val fom = it.gyldighetsperiode.fom
        val tom = it.gyldighetsperiode.tom
        val tomIsNullOrBeforeNow = !(tom?.isBefore(date) ?: false)
        val fomIsNullOrAfterNow = !(fom?.isAfter(date) ?: true)
        return tomIsNullOrBeforeNow && fomIsNullOrAfterNow
    }

    fun getName(navn: Navn): String {
        val builder = StringBuilder()
        if (!navn.navnelinje1.isNullOrBlank()) {
            builder.appendLine(navn.navnelinje1)
        }
        if (!navn.navnelinje2.isNullOrBlank()) {
            builder.appendLine(navn.navnelinje2)
        }
        if (!navn.navnelinje3.isNullOrBlank()) {
            builder.appendLine(navn.navnelinje3)
        }
        if (!navn.navnelinje4.isNullOrBlank()) {
            builder.appendLine(navn.navnelinje4)
        }
        if (!navn.navnelinje5.isNullOrBlank()) {
            builder.appendLine(navn.navnelinje5)
        }
        return builder.lineSequence().filter {
            it.isNotBlank()
        }.joinToString(separator = ",")
    }

    private fun NarmesteLederRelasjon.tilNarmesteLeder(orgnavn: String): NarmesteLeder {
        return NarmesteLeder(
            id = Random.nextLong(), // denne er ikke i bruk, men står som påkrevd i formatet
            aktoerId = narmesteLederAktorId,
            navn = navn ?: "",
            epost = narmesteLederEpost,
            mobil = narmesteLederTelefonnummer,
            orgnummer = orgnummer,
            organisasjonsnavn = orgnavn,
            aktivTom = aktivTom,
            arbeidsgiverForskuttererLoenn = arbeidsgiverForskutterer
        )
    }
}
