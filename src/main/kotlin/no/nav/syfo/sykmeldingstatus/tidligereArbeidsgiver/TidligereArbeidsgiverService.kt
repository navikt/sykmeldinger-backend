package no.nav.syfo.sykmeldingstatus.tidligereArbeidsgiver

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmeldingstatus.kafka.SykmeldingWithArbeidsgiverStatus
import no.nav.syfo.sykmeldingstatus.kafka.model.STATUS_SENDT
import no.nav.syfo.utils.logger

class TidligereArbeidsgiverService() {
    private val logger = logger()

    fun filterRelevantSykmeldinger(
        allSykmeldinger: List<SykmeldingWithArbeidsgiverStatus>,
        currentSykmelding: SykmeldingWithArbeidsgiverStatus
    ): List<Pair<SykmeldingWithArbeidsgiverStatus, TidligereArbeidsgiverType>> {
        val sykmeldingerMedUnikeOrgnummer =
            allSykmeldinger
                .filterNot { it.sykmeldingId == currentSykmelding.sykmeldingId }
                .filter {
                    it.statusEvent == STATUS_SENDT || it.tidligereArbeidsgiver?.orgnummer != null
                }
                .mapNotNull {
                    val tidligereArbeidsgiverType =
                        tidligereArbeidsgiverType(
                            findFirstFom(currentSykmelding.sykmeldingsperioder),
                            it.sykmeldingsperioder
                        )
                    if (tidligereArbeidsgiverType != TidligereArbeidsgiverType.INGEN)
                        it to tidligereArbeidsgiverType
                    else null
                }
                .distinctBy { sykmeldingWithStatus ->
                    sykmeldingWithStatus.first.arbeidsgiver?.orgnummer
                        ?: sykmeldingWithStatus.first.tidligereArbeidsgiver?.orgnummer
                }
        return sykmeldingerMedUnikeOrgnummer
    }

    private fun tidligereArbeidsgiverType(
        currentSykmeldingFirstFomDate: LocalDate,
        sykmeldingsperioder: List<SykmeldingsperiodeDTO>
    ): TidligereArbeidsgiverType {
        val kantTilKant = sisteTomIKantMedDag(sykmeldingsperioder, currentSykmeldingFirstFomDate)
        if (kantTilKant) return TidligereArbeidsgiverType.KANT_TIL_KANT
        val overlappende =
            isOverlappende(
                tidligereSmTom = sykmeldingsperioder.maxOf { it.tom },
                tidligereSmFom = sykmeldingsperioder.minOf { it.fom },
                fom = currentSykmeldingFirstFomDate,
            )
        if (overlappende) return TidligereArbeidsgiverType.OVERLAPPENDE
        return TidligereArbeidsgiverType.INGEN
    }

    enum class TidligereArbeidsgiverType {
        KANT_TIL_KANT,
        OVERLAPPENDE,
        INGEN
    }

    private fun isOverlappende(
        tidligereSmTom: LocalDate,
        tidligereSmFom: LocalDate,
        fom: LocalDate
    ) =
        (fom.isAfter(tidligereSmFom) &&
            fom.isBefore(
                tidligereSmTom.plusDays(1),
            ))

    private fun sisteTomIKantMedDag(
        perioder: List<SykmeldingsperiodeDTO>,
        dag: LocalDate
    ): Boolean {
        val sisteTom =
            perioder.maxByOrNull { it.tom }?.tom
                ?: throw IllegalStateException("Skal ikke kunne ha periode uten tom")
        return !isWorkingdaysBetween(sisteTom, dag)
    }

    private fun isWorkingdaysBetween(tom: LocalDate, fom: LocalDate): Boolean {
        val daysBetween = ChronoUnit.DAYS.between(tom, fom).toInt()
        if (daysBetween < 0) return true
        return when (fom.dayOfWeek) {
            DayOfWeek.MONDAY -> daysBetween > 3
            DayOfWeek.SUNDAY -> daysBetween > 2
            else -> daysBetween > 1
        }
    }

    private fun findFirstFom(perioder: List<SykmeldingsperiodeDTO>): LocalDate {
        return perioder.minByOrNull { it.fom }?.fom
            ?: throw IllegalStateException("Skal ikke kunne ha periode uten fom")
    }
}
