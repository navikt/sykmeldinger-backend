package no.nav.syfo.arbeidsgivere.service

import java.time.LocalDate
import no.nav.syfo.arbeidsgivere.db.ArbeidsforholdDb
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.model.NarmesteLeder
import no.nav.syfo.arbeidsgivere.narmesteleder.db.NarmestelederDb
import no.nav.syfo.arbeidsgivere.narmesteleder.db.NarmestelederDbModel

class ArbeidsgiverService(
    private val narmestelederDb: NarmestelederDb,
    private val arbeidsforholdDb: ArbeidsforholdDb,
) {
    suspend fun getArbeidsgivere(
        fnr: String,
        date: LocalDate = LocalDate.now()
    ): List<Arbeidsgiverinfo> {
        val arbeidsgivere = arbeidsforholdDb.getArbeidsforhold(fnr = fnr)

        if (arbeidsgivere.isEmpty()) {
            return emptyList()
        }
        val aktiveNarmesteledere = narmestelederDb.getNarmesteleder(fnr)

        return arbeidsgivere
            .sortedWith(
                compareByDescending(nullsLast()) { it.tom },
            )
            .distinctBy { it.orgnummer }
            .map { arbeidsforhold ->
                val narmesteLeder =
                    aktiveNarmesteledere.find { it.orgnummer == arbeidsforhold.orgnummer }
                Arbeidsgiverinfo(
                    orgnummer = arbeidsforhold.orgnummer,
                    juridiskOrgnummer = arbeidsforhold.juridiskOrgnummer,
                    navn = arbeidsforhold.orgNavn,
                    aktivtArbeidsforhold =
                        arbeidsforhold.tom == null ||
                            !date.isAfter(arbeidsforhold.tom) && !date.isBefore(arbeidsforhold.fom),
                    naermesteLeder = narmesteLeder?.tilNarmesteLeder(arbeidsforhold.orgNavn),
                )
            }
    }

    private fun NarmestelederDbModel.tilNarmesteLeder(orgnavn: String): NarmesteLeder {
        return NarmesteLeder(
            navn = navn,
            orgnummer = orgnummer,
            organisasjonsnavn = orgnavn,
        )
    }
}
