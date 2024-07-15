package no.nav.syfo.arbeidsgivere.service

import java.time.LocalDate
import no.nav.syfo.arbeidsgivere.db.ArbeidsforholdDb
import no.nav.syfo.arbeidsgivere.model.Arbeidsforhold
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.model.NarmesteLeder
import no.nav.syfo.arbeidsgivere.narmesteleder.db.NarmestelederDb
import no.nav.syfo.arbeidsgivere.narmesteleder.db.NarmestelederDbModel

class ArbeidsgiverService(
    private val narmestelederDb: NarmestelederDb,
    private val arbeidsforholdDb: ArbeidsforholdDb,
) {

    suspend fun getArbeidsgivereWithinSykmeldingPeriode(
        sykmeldingFom: LocalDate,
        sykmeldingTom: LocalDate,
        fnr: String,
        date: LocalDate = LocalDate.now()
    ): List<Arbeidsgiverinfo> {
        val arbeidsgivere = arbeidsforholdDb.getArbeidsforhold(fnr = fnr)
        if (arbeidsgivere.isEmpty()) {
            return emptyList()
        }
        val aktiveNarmesteledere = narmestelederDb.getNarmesteleder(fnr)
        val arbeidsgivereWithinSykmeldingsperiode =
            filterArbeidsgivere(sykmeldingFom, sykmeldingTom, arbeidsgivere)
        return arbeidsgivereWithinSykmeldingsperiode.map { arbeidsforhold ->
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

    private fun filterArbeidsgivere(
        sykmeldingFom: LocalDate,
        sykmeldingTom: LocalDate,
        allArbeidsgivere: List<Arbeidsforhold>,
    ): List<Arbeidsforhold> {
        return allArbeidsgivere.filter {
            isArbeidsforholdWithinSykmeldingPeriode(it, sykmeldingFom, sykmeldingTom)
        }
    }

    private fun isArbeidsforholdWithinSykmeldingPeriode(
        arbeidsforhold: Arbeidsforhold,
        sykmeldingFom: LocalDate,
        sykmeldingTom: LocalDate,
    ): Boolean {
        val checkSluttdato =
            arbeidsforhold.tom == null ||
                arbeidsforhold.tom.isAfter(sykmeldingFom) ||
                arbeidsforhold.tom == sykmeldingFom
        val checkStartdato =
            arbeidsforhold.fom.isBefore(sykmeldingTom) || arbeidsforhold.fom == sykmeldingTom
        return checkStartdato && checkSluttdato
    }
}
