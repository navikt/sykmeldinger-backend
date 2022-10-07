package no.nav.syfo.arbeidsgivere.service

import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsavtale
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsforhold
import no.nav.syfo.arbeidsgivere.client.organisasjon.client.OrganisasjonsinfoClient
import no.nav.syfo.arbeidsgivere.client.organisasjon.model.Navn
import no.nav.syfo.arbeidsgivere.client.organisasjon.model.Organisasjonsinfo
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.model.NarmesteLeder
import no.nav.syfo.arbeidsgivere.narmesteleder.db.NarmestelederDb
import no.nav.syfo.arbeidsgivere.narmesteleder.db.NarmestelederDbModel
import no.nav.syfo.arbeidsgivere.redis.ArbeidsgiverRedisService
import no.nav.syfo.arbeidsgivere.redis.toArbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.redis.toArbeidsgiverinfoRedisModel
import no.nav.syfo.log
import no.nav.syfo.pdl.service.PdlPersonService
import java.time.LocalDate

class ArbeidsgiverService(
    private val arbeidsforholdClient: ArbeidsforholdClient,
    private val organisasjonsinfoClient: OrganisasjonsinfoClient,
    private val narmestelederDb: NarmestelederDb,
    private val pdlPersonService: PdlPersonService,
    private val arbeidsgiverRedisService: ArbeidsgiverRedisService
) {
    suspend fun getArbeidsgivere(fnr: String, token: String, sykmeldingId: String, date: LocalDate = LocalDate.now()): List<Arbeidsgiverinfo> {
        val arbeidsgivereFraRedis = getArbeidsgivereFromRedis(fnr)
        if (arbeidsgivereFraRedis != null) {
            log.debug("Fant arbeidsgivere i redis")
            return arbeidsgivereFraRedis
        }

        val person = pdlPersonService.getPerson(fnr = fnr, userToken = token, callId = sykmeldingId)
        if (person.diskresjonskode) {
            return emptyList() // personer med diskresjonskode skal ikke få hentet arbeidsforhold
        }
        val ansettelsesperiodeFom = LocalDate.now().minusMonths(4)
        val arbeidsgivere = arbeidsforholdClient.getArbeidsforholdTokenX(fnr = fnr, ansettelsesperiodeFom = ansettelsesperiodeFom, subjectToken = token)

        if (arbeidsgivere.isEmpty()) {
            return emptyList()
        }
        val aktiveNarmesteledere = narmestelederDb.getNarmesteleder(fnr)

        val arbeidsgiverList = ArrayList<Arbeidsgiverinfo>()
        arbeidsgivere.filter {
            it.arbeidsgiver.type == "Organisasjon"
        }.sortedWith(
            compareByDescending(nullsLast()) {
                it.ansettelsesperiode.periode.tom
            }
        ).distinctBy {
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
        narmestelederDbModel: NarmestelederDbModel?,
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
                naermesteLeder = narmestelederDbModel?.tilNarmesteLeder(orgnavn)
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

    private fun NarmestelederDbModel.tilNarmesteLeder(orgnavn: String): NarmesteLeder {
        return NarmesteLeder(
            aktoerId = "", // brukes ikke i frontend
            navn = navn,
            epost = "", // brukes ikke i frontend
            mobil = "", // brukes ikke i frontend
            orgnummer = orgnummer,
            organisasjonsnavn = orgnavn,
            aktivTom = null, // brukes ikke i frontend
            arbeidsgiverForskuttererLoenn = null // brukes ikke i frontend
        )
    }

    private suspend fun getArbeidsgivereFromRedis(fnr: String): List<Arbeidsgiverinfo>? {
        return arbeidsgiverRedisService.getArbeidsgivere(fnr)?.map { it.toArbeidsgiverinfo() }
    }
}
