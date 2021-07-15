package no.nav.syfo.arbeidsgivere.service

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsavtale
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsforhold
import no.nav.syfo.arbeidsgivere.client.narmesteleder.NarmestelederClient
import no.nav.syfo.arbeidsgivere.client.organisasjon.client.OrganisasjonsinfoClient
import no.nav.syfo.arbeidsgivere.client.organisasjon.model.Navn
import no.nav.syfo.arbeidsgivere.client.organisasjon.model.Organisasjonsinfo
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.model.NarmesteLeder
import no.nav.syfo.arbeidsgivere.redis.ArbeidsgiverRedisService
import no.nav.syfo.arbeidsgivere.redis.toArbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.redis.toArbeidsgiverinfoRedisModel
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.log
import no.nav.syfo.pdl.service.PdlPersonService
import java.time.LocalDate

@KtorExperimentalAPI
class ArbeidsgiverService(
    private val arbeidsforholdClient: ArbeidsforholdClient,
    private val organisasjonsinfoClient: OrganisasjonsinfoClient,
    private val narmestelederClient: NarmestelederClient,
    private val pdlPersonService: PdlPersonService,
    private val stsOidcClient: StsOidcClient,
    private val arbeidsgiverRedisService: ArbeidsgiverRedisService
) {
    suspend fun getArbeidsgivere(fnr: String, token: String, sykmeldingId: String, date: LocalDate = LocalDate.now()): List<Arbeidsgiverinfo> {
        val arbeidsgivereFraRedis = getArbeidsgivereFromRedis(fnr)
        if (arbeidsgivereFraRedis != null) {
            log.debug("Fant arbeidsgivere i redis")
            return arbeidsgivereFraRedis
        }

        val stsToken = stsOidcClient.oidcToken()
        val person = pdlPersonService.getPerson(fnr = fnr, userToken = token, callId = sykmeldingId, stsToken = stsToken.access_token)
        if (person.diskresjonskode) {
            return emptyList() // personer med diskresjonskode skal ikke få hentet arbeidsforhold
        }
        val ansettelsesperiodeFom = LocalDate.now().minusMonths(4)
        val arbeidsgivere = arbeidsforholdClient.getArbeidsforhold(fnr = fnr, ansettelsesperiodeFom = ansettelsesperiodeFom, token = token, stsToken = stsToken.access_token)

        if (arbeidsgivere.isEmpty()) {
            return emptyList()
        }
        val aktiveNarmesteledere = narmestelederClient.getNarmesteledere(token).filter { it.aktivTom == null }
        val arbeidsgiverList = ArrayList<Arbeidsgiverinfo>()
        arbeidsgivere.filter {
            it.arbeidsgiver.type == "Organisasjon"
        }.sortedBy {
            it.ansettelsesperiode.periode.tom != null
        }.distinctBy {
            it.arbeidsgiver.organisasjonsnummer
        }.forEach { arbeidsforhold ->
            val organisasjonsinfo =
                organisasjonsinfoClient.getOrginfo(arbeidsforhold.arbeidsgiver.organisasjonsnummer!!)
            val narmesteLeder = aktiveNarmesteledere.find { it.orgnummer == arbeidsforhold.arbeidsgiver.organisasjonsnummer }
            val arbeidsavtale = if (arbeidsforhold.arbeidsavtaler.size < 2) {
                arbeidsforhold.arbeidsavtaler.firstOrNull()
            } else {
                arbeidsforhold.arbeidsavtaler.filter { it.gyldighetsperiode.fom != null }
                    .maxByOrNull { it.gyldighetsperiode.fom!! } ?: arbeidsforhold.arbeidsavtaler.first()
            }
            addArbeidsinfo(arbeidsgiverList, organisasjonsinfo, arbeidsavtale, arbeidsforhold, narmesteLeder, date)
        }
        arbeidsgiverRedisService.updateArbeidsgivere(arbeidsgiverList.map { it.toArbeidsgiverinfoRedisModel() }, fnr)
        return arbeidsgiverList
    }

    private fun addArbeidsinfo(
        arbeidsgiverList: ArrayList<Arbeidsgiverinfo>,
        organisasjonsinfo: Organisasjonsinfo,
        arbeidsavtale: Arbeidsavtale?,
        arbeidsforhold: Arbeidsforhold,
        narmesteLederRelasjon: no.nav.syfo.arbeidsgivere.client.narmesteleder.NarmesteLeder?,
        date: LocalDate
    ) {
        val orgnavn = getName(organisasjonsinfo.navn)
        arbeidsgiverList.add(
            Arbeidsgiverinfo(
                orgnummer = organisasjonsinfo.organisasjonsnummer,
                juridiskOrgnummer = arbeidsforhold.opplysningspliktig.organisasjonsnummer!!,
                navn = orgnavn,
                stilling = "", // denne brukes ikke, men er påkrevd i formatet
                stillingsprosent = arbeidsavtale?.stillingsprosent?.toString() ?: "100.0",
                aktivtArbeidsforhold = arbeidsforhold.ansettelsesperiode.periode.tom == null ||
                    !date.isAfter(arbeidsforhold.ansettelsesperiode.periode.tom) && !date.isBefore(arbeidsforhold.ansettelsesperiode.periode.fom),
                naermesteLeder = narmesteLederRelasjon?.tilNarmesteLeder(orgnavn)
            )
        )
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

    private fun no.nav.syfo.arbeidsgivere.client.narmesteleder.NarmesteLeder.tilNarmesteLeder(orgnavn: String): NarmesteLeder {
        return NarmesteLeder(
            aktoerId = "", // brukes ikke i frontend
            navn = navn ?: "",
            epost = narmesteLederEpost,
            mobil = narmesteLederTelefonnummer,
            orgnummer = orgnummer,
            organisasjonsnavn = orgnavn,
            aktivTom = aktivTom,
            arbeidsgiverForskuttererLoenn = arbeidsgiverForskutterer
        )
    }

    private fun getArbeidsgivereFromRedis(fnr: String): List<Arbeidsgiverinfo>? {
        return arbeidsgiverRedisService.getArbeidsgivere(fnr)?.map { it.toArbeidsgiverinfo() }
    }
}
