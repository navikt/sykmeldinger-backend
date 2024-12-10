package no.nav.syfo.arbeidsgivere.service

import java.time.LocalDate
import no.nav.syfo.arbeidsgivere.db.ArbeidsforholdDb
import no.nav.syfo.arbeidsgivere.model.Arbeidsforhold
import no.nav.syfo.arbeidsgivere.model.ArbeidsforholdType
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.model.NarmesteLeder
import no.nav.syfo.arbeidsgivere.narmesteleder.db.NarmestelederDb
import no.nav.syfo.arbeidsgivere.narmesteleder.db.NarmestelederDbModel
import no.nav.syfo.utils.securelog

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
        securelog.info(
            "getting arbeidsforhold for $fnr, sykmeldingFom: $sykmeldingFom, sykmeldingTom: $sykmeldingTom"
        )
        val arbeidsgivere = getArbeidsforhold(fnr)

        if (arbeidsgivere.isEmpty()) {
            return emptyList()
        }
        val aktiveNarmesteledere = narmestelederDb.getNarmesteleder(fnr)
        val arbeidsgivereWithinSykmeldingsperiode =
            filterArbeidsgivere(sykmeldingFom, sykmeldingTom, arbeidsgivere)
        val arbeidsforhold =
            arbeidsgivereWithinSykmeldingsperiode.map { arbeidsforhold ->
                arbeidsgiverinfo(aktiveNarmesteledere, arbeidsforhold, date)
            }
        securelog.info(
            "Arbeidsforhold for $fnr, ${arbeidsgivereWithinSykmeldingsperiode.joinToString { "id: ${it.id}: orgnummer:${it.orgnummer}: fom: ${it.fom}, tom:${it.tom}" }}"
        )
        return arbeidsforhold
    }

    private suspend fun getArbeidsforhold(fnr: String) =
        arbeidsforholdDb.getArbeidsforhold(fnr = fnr).filter { gyldigArbeidsforholdType(it) }

    private fun gyldigArbeidsforholdType(arbeidsforhold: Arbeidsforhold): Boolean {
        return when (arbeidsforhold.type) {
            ArbeidsforholdType.FRILANSER_OPPDRAGSTAKER_HONORAR_PERSONER_MM -> false
            else -> true
        }
    }

    suspend fun getArbeidsgivere(
        fnr: String,
        date: LocalDate = LocalDate.now()
    ): List<Arbeidsgiverinfo> {
        val arbeidsgivere = getArbeidsforhold(fnr = fnr)

        if (arbeidsgivere.isEmpty()) {
            return emptyList()
        }
        val aktiveNarmesteledere = narmestelederDb.getNarmesteleder(fnr)

        return arbeidsgivere
            .sortedWith(
                compareByDescending(nullsLast()) { it.tom },
            )
            .distinctBy { it.orgnummer }
            .map { arbeidsforhold -> arbeidsgiverinfo(aktiveNarmesteledere, arbeidsforhold, date) }
    }

    private fun arbeidsgiverinfo(
        aktiveNarmesteledere: List<NarmestelederDbModel>,
        arbeidsforhold: Arbeidsforhold,
        date: LocalDate
    ): Arbeidsgiverinfo {
        val narmesteLeder = aktiveNarmesteledere.find { it.orgnummer == arbeidsforhold.orgnummer }
        return Arbeidsgiverinfo(
            orgnummer = arbeidsforhold.orgnummer,
            juridiskOrgnummer = arbeidsforhold.juridiskOrgnummer,
            navn = arbeidsforhold.orgNavn,
            aktivtArbeidsforhold =
                arbeidsforhold.tom == null ||
                    !date.isAfter(arbeidsforhold.tom) && !date.isBefore(arbeidsforhold.fom),
            naermesteLeder = narmesteLeder?.tilNarmesteLeder(arbeidsforhold.orgNavn),
        )
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
