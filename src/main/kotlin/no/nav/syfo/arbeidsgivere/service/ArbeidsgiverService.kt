package no.nav.syfo.arbeidsgivere.service

import no.nav.syfo.arbeidsgivere.db.ArbeidsforholdDb
import no.nav.syfo.arbeidsgivere.model.Arbeidsforhold
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.model.NarmesteLeder
import no.nav.syfo.arbeidsgivere.narmesteleder.db.NarmestelederDb
import no.nav.syfo.arbeidsgivere.narmesteleder.db.NarmestelederDbModel
import java.time.LocalDate

class ArbeidsgiverService(
    private val narmestelederDb: NarmestelederDb,
    private val arbeidsforholdDb: ArbeidsforholdDb
) {
    suspend fun getArbeidsgivere(fnr: String, date: LocalDate = LocalDate.now()): List<Arbeidsgiverinfo> {

        val arbeidsgivere = arbeidsforholdDb.getArbeidsforhold(fnr = fnr)

        if (arbeidsgivere.isEmpty()) {
            return emptyList()
        }
        val aktiveNarmesteledere = narmestelederDb.getNarmesteleder(fnr)

        val arbeidsgiverList = ArrayList<Arbeidsgiverinfo>()
        arbeidsgivere.sortedWith(
            compareByDescending(nullsLast()) {
                it.tom
            }
        ).distinctBy {
            it.orgnummer
        }.forEach { arbeidsforhold ->
            val narmesteLeder = aktiveNarmesteledere.find { it.orgnummer == arbeidsforhold.orgnummer }
            addArbeidsinfo(arbeidsgiverList, arbeidsforhold, narmesteLeder, date)
        }
        return arbeidsgiverList
    }

    private fun addArbeidsinfo(
        arbeidsgiverList: ArrayList<Arbeidsgiverinfo>,
        arbeidsforhold: Arbeidsforhold,
        narmestelederDbModel: NarmestelederDbModel?,
        date: LocalDate
    ) {
        arbeidsgiverList.add(
            Arbeidsgiverinfo(
                orgnummer = arbeidsforhold.orgnummer,
                juridiskOrgnummer = arbeidsforhold.juridiskOrgnummer,
                navn = arbeidsforhold.orgNavn,
                stilling = "", // denne brukes ikke, men er påkrevd i formatet
                stillingsprosent = "", // denne brukes ikke, men er påkrevd i formatet
                aktivtArbeidsforhold = arbeidsforhold.tom == null ||
                    !date.isAfter(arbeidsforhold.tom) && !date.isBefore(arbeidsforhold.fom),
                naermesteLeder = narmestelederDbModel?.tilNarmesteLeder(arbeidsforhold.orgNavn)
            )
        )
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
}
