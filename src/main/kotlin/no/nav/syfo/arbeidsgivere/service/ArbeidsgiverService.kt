package no.nav.syfo.arbeidsgivere.service

import java.time.LocalDate
import no.nav.syfo.arbeidsforhold.ArbeidsforholdService
import no.nav.syfo.arbeidsgivere.db.ArbeidsforholdDb
import no.nav.syfo.arbeidsgivere.model.Arbeidsforhold
import no.nav.syfo.arbeidsgivere.model.ArbeidsforholdType
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.model.NarmesteLeder
import no.nav.syfo.arbeidsgivere.narmesteleder.db.NarmestelederDb
import no.nav.syfo.arbeidsgivere.narmesteleder.db.NarmestelederDbModel
import no.nav.syfo.utils.objectMapper
import no.nav.syfo.utils.securelog
import org.slf4j.LoggerFactory

class ArbeidsgiverService(
    private val narmestelederDb: NarmestelederDb,
    private val arbeidsforholdDb: ArbeidsforholdDb,
    private val arbeidsfhorholdService: ArbeidsforholdService,
) {
    companion object {
        private val log = LoggerFactory.getLogger(ArbeidsgiverService::class.java)
    }

    suspend fun tryGetArbeidsforholdFromApi(fnr: String): List<Arbeidsforhold>? {
        try {
            val arbeidsforholdFromApi = arbeidsfhorholdService.getArbeidsforhold(fnr)
            return arbeidsforholdFromApi
        } catch (ex: Exception) {
            log.error("could not get arbeidsforhold from api", ex)
            securelog.error("Could not get arbeidsforhold from api for $fnr", ex)
            return null
        }
    }

    suspend fun getArbeidsgivereWithinSykmeldingPeriode(
        sykmeldingFom: LocalDate,
        sykmeldingTom: LocalDate,
        fnr: String,
        date: LocalDate = LocalDate.now()
    ): List<Arbeidsgiverinfo> {
        securelog.info(
            "getting arbeidsforhold for $fnr, sykmeldingFom: $sykmeldingFom, sykmeldingTom: $sykmeldingTom"
        )

        val currentArbeidsforhold = getCurrentArbeidsforhold(fnr)

        if (currentArbeidsforhold.isEmpty()) {
            return emptyList()
        }
        val aktiveNarmesteledere = narmestelederDb.getNarmesteleder(fnr)
        val arbeidsgivereWithinSykmeldingsperiode =
            filterArbeidsgivere(sykmeldingFom, sykmeldingTom, currentArbeidsforhold)
        val arbeidsforhold =
            arbeidsgivereWithinSykmeldingsperiode.map { arbeidsforhold ->
                arbeidsgiverinfo(aktiveNarmesteledere, arbeidsforhold, date)
            }
        securelog.info(
            "Arbeidsforhold for $fnr, ${arbeidsgivereWithinSykmeldingsperiode.joinToString { "id: ${it.id}: orgnummer:${it.orgnummer}: fom: ${it.fom}, tom:${it.tom}" }}"
        )
        return arbeidsforhold
    }

    private suspend fun ArbeidsgiverService.getCurrentArbeidsforhold(
        fnr: String
    ): List<Arbeidsforhold> {
        val arbeidsgivereFromDb = arbeidsforholdDb.getArbeidsforhold(fnr = fnr)
        val arbeidsforholdFromApi = tryGetArbeidsforholdFromApi(fnr)

        val currentArbeidsforhold =
            if (
                    arbeidsforholdFromApi != null &&
                        !hasTheSameArbeidsforhold(arbeidsgivereFromDb, arbeidsforholdFromApi)
                ) {
                    log.warn("Arbeidsforhold is not equal from db and API, updating")
                    securelog.warn(
                        "arbeidsforhold not equal from db and API for $fnr, arbeidsforholdDb: ${objectMapper.writeValueAsString(arbeidsgivereFromDb)}, arbeidsforholdApi: ${objectMapper.writeValueAsString(arbeidsforholdFromApi)}",
                    )
                    arbeidsfhorholdService.updateArbeidsforhold(
                        arbeidsforhold = arbeidsforholdFromApi,
                        arbeidsforholdFraDb = arbeidsgivereFromDb,
                    )
                    arbeidsforholdFromApi
                } else {
                    if (arbeidsforholdFromApi == null) {
                        securelog.error("Arbeidsforhold for api is null for $fnr")
                    } else if (
                        hasTheSameArbeidsforhold(arbeidsgivereFromDb, arbeidsforholdFromApi)
                    ) {
                        securelog.info("arbeidsforhold equal form db and api for $fnr")
                    }
                    arbeidsgivereFromDb
                }
                .filter { gyldigArbeidsforholdType(it) }
        return currentArbeidsforhold
    }

    private fun hasTheSameArbeidsforhold(
        arbeidsgivereFromDb: List<Arbeidsforhold>,
        arbeidsforholdFromApi: List<Arbeidsforhold>
    ): Boolean {
        return arbeidsgivereFromDb.map { it.copy(id = 0) }.toSet() ==
            arbeidsforholdFromApi.map { it.copy(id = 0) }.toSet()
    }

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
        val arbeidsgivere = getCurrentArbeidsforhold(fnr = fnr)

        if (arbeidsgivere.isEmpty()) {
            return emptyList()
        }

        val aktiveNarmesteledere = narmestelederDb.getNarmesteleder(fnr)

        val arbeidsforhold = arbeidsgivere
            .sortedWith(
                compareByDescending(nullsLast()) { it.tom },
            )
            .distinctBy { it.orgnummer }
            .map { arbeidsforhold -> arbeidsgiverinfo(aktiveNarmesteledere, arbeidsforhold, date) }

        securelog.info("getting arbeidsforhold for $fnr, ${objectMapper.writeValueAsString(arbeidsforhold)}")

        return arbeidsforhold
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
