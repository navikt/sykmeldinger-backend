package no.nav.syfo.sykmeldingstatus.model

import io.ktor.util.logging.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import no.nav.syfo.metrics.ANTALL_TIDLIGERE_ARBEIDSGIVERE
import no.nav.syfo.metrics.TIDLIGERE_ARBEIDSGIVER_COUNTER
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.db.SykmeldingStatusDb
import no.nav.syfo.sykmeldingstatus.kafka.SykmeldingWithArbeidsgiverStatus
import no.nav.syfo.sykmeldingstatus.kafka.model.STATUS_SENDT
import no.nav.syfo.utils.logger

class EmploymentHistoryRetriever(private val sykmeldingStatusDb: SykmeldingStatusDb) {
    private val logger = logger()

    suspend fun tidligereArbeidsgiver(
        sykmeldtFnr: String,
        sykmeldingId: String,
        nesteStatus: StatusEventDTO,
        tidligereArbeidsgiverFraBruker: String?,
    ): TidligereArbeidsgiverDTO? {
        if (nesteStatus == StatusEventDTO.BEKREFTET) {
            return createTidligereArbeidsgiver(
                sykmeldtFnr,
                sykmeldingId,
                tidligereArbeidsgiverFraBruker
            )
        }
        return null
    }

    private suspend fun createTidligereArbeidsgiver(
        sykmeldtFnr: String,
        sykmeldingId: String,
        tidligereArbeidsgiverFraBruker: String?,
    ): TidligereArbeidsgiverDTO? {
        val sisteSykmelding =
            findLastSendtSykmelding(sykmeldtFnr, sykmeldingId, tidligereArbeidsgiverFraBruker)
        if (sisteSykmelding?.arbeidsgiver == null) {
            return sisteSykmelding?.tidligereArbeidsgiver
        }
        return sisteSykmelding.arbeidsgiver.let {
            TidligereArbeidsgiverDTO(
                orgNavn = it.orgNavn,
                orgnummer = it.orgnummer,
                sykmeldingsId = sisteSykmelding.sykmeldingId,
            )
        }
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

    private fun isOverlappende(
        tidligereSmTom: LocalDate,
        tidligereSmFom: LocalDate,
        fom: LocalDate
    ) =
        (fom.isAfter(tidligereSmFom.minusDays(1)) &&
            fom.isBefore(
                tidligereSmTom.plusDays(1),
            ))

    suspend fun findLastSendtSykmelding(
        fnr: String,
        sykmeldingId: String,
        tidligereArbeidsgiver: String?,
    ): SykmeldingWithArbeidsgiverStatus? {
        try {
            val allSykmeldinger = sykmeldingStatusDb.getSykmeldingWithStatus(fnr)
            return findLastCorrectSykmelding(allSykmeldinger, sykmeldingId, tidligereArbeidsgiver)
        } catch (ex: Exception) {
            logger.error(ex)
            throw ex
        }
    }

    fun findLastCorrectSykmelding(
        allSykmeldinger: List<SykmeldingWithArbeidsgiverStatus>,
        sykmeldingId: String,
        tidligereArbeidsgiverFraBruker: String?
    ): SykmeldingWithArbeidsgiverStatus? {
        val currentSykmelding = allSykmeldinger.single { it.sykmeldingId == sykmeldingId }
        val otherSykmeldinger = allSykmeldinger.filterNot { it.sykmeldingId == sykmeldingId }
        val currentSykmeldingFirstFomDate = finnForsteFom(currentSykmelding.sykmeldingsperioder)

        logger.info("antall sykmeldinger ${allSykmeldinger.size}")

        val sykmeldinger =
            otherSykmeldinger
                .filter {
                    it.statusEvent == STATUS_SENDT || it.tidligereArbeidsgiver?.orgnummer != null
                }
                .map {
                    val tidligereArbeidsgiverType =
                        tidligereArbeidsgiverType(
                            currentSykmeldingFirstFomDate,
                            it.sykmeldingsperioder,
                        )
                    it to tidligereArbeidsgiverType
                }
                .filter { it.second != TidligereArbeidsgiverType.INGEN }

        val antallTidligereArbeidsgivere =
            sykmeldinger.distinctBy { it.first.arbeidsgiver?.orgnummer }.size
        ANTALL_TIDLIGERE_ARBEIDSGIVERE.labels(antallTidligereArbeidsgivere.toString()).inc()
        val sisteStatus = sykmeldinger.firstOrNull() ?: return null
        TIDLIGERE_ARBEIDSGIVER_COUNTER.labels(sisteStatus.second.name).inc()

        if (antallTidligereArbeidsgivere != 1) {
            if (tidligereArbeidsgiverFraBruker == null) return null
            if (sisteStatus.first.arbeidsgiver?.orgnummer == tidligereArbeidsgiverFraBruker) {
                return sisteStatus.first
            }
            val sisteStatusCorrectAg =
                sykmeldinger.find {
                    it.first.arbeidsgiver?.orgnummer == tidligereArbeidsgiverFraBruker
                }
            if (
                sisteStatus.second == TidligereArbeidsgiverType.KANT_TIL_KANT ||
                    sisteStatus.second == TidligereArbeidsgiverType.OVERLAPPENDE
            ) {
                return sisteStatusCorrectAg?.first
            }
            return null
        }

        return sisteStatus.first
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

    private fun sisteTomIKantMedDag(
        perioder: List<SykmeldingsperiodeDTO>,
        dag: LocalDate
    ): Boolean {
        val sisteTom =
            perioder.maxByOrNull { it.tom }?.tom
                ?: throw IllegalStateException("Skal ikke kunne ha periode uten tom")
        return !isWorkingdaysBetween(sisteTom, dag)
    }

    private fun finnForsteFom(perioder: List<SykmeldingsperiodeDTO>): LocalDate {
        return perioder.minByOrNull { it.fom }?.fom
            ?: throw IllegalStateException("Skal ikke kunne ha periode uten fom")
    }
}
