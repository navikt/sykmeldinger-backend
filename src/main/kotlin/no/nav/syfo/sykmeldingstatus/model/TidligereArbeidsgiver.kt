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

class TidligereArbeidsgiver(private val sykmeldingStatusDb: SykmeldingStatusDb) {
    private val logger = logger()

    suspend fun tidligereArbeidsgiver(
        sykmeldtFnr: String,
        sykmeldingId: String,
        nesteStatus: StatusEventDTO,
        tidligereArbeidsgiverBrukerInput: String?,
    ): TidligereArbeidsgiverDTO? {
        if (nesteStatus == StatusEventDTO.BEKREFTET) {
            return createTidligereArbeidsgiver(
                sykmeldtFnr,
                sykmeldingId,
                tidligereArbeidsgiverBrukerInput
            )
        }
        return null
    }

    private suspend fun createTidligereArbeidsgiver(
        sykmeldtFnr: String,
        sykmeldingId: String,
        tidligereArbeidsgiverBrukerInput: String?,
    ): TidligereArbeidsgiverDTO? {
        val sisteSykmelding =
            findLastSendtSykmelding(sykmeldtFnr, sykmeldingId, tidligereArbeidsgiverBrukerInput)
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
        tidligereArbeidsgiverBrukerInput: String?,
    ): SykmeldingWithArbeidsgiverStatus? {
        try {
            val allSykmeldinger = sykmeldingStatusDb.getSykmeldingWithStatus(fnr)
            return findLastRelevantSykmelding(
                allSykmeldinger,
                sykmeldingId,
                tidligereArbeidsgiverBrukerInput
            )
        } catch (ex: Exception) {
            logger.error(ex)
            throw ex
        }
    }

    fun findLastRelevantSykmelding(
        allSykmeldinger: List<SykmeldingWithArbeidsgiverStatus>,
        sykmeldingId: String,
        tidligereArbeidsgiverBrukerInput: String?
    ): SykmeldingWithArbeidsgiverStatus? {
        val currentSykmelding =
            allSykmeldinger.find { it.sykmeldingId == sykmeldingId } ?: return null
        val filteredSykmeldinger = filterRelevantSykmeldinger(allSykmeldinger, currentSykmelding)

        logger.info("antall sykmeldinger ${allSykmeldinger.size}")

        return decideMostRelevantSykmelding(filteredSykmeldinger, tidligereArbeidsgiverBrukerInput)
    }

    private fun filterRelevantSykmeldinger(
        allSykmeldinger: List<SykmeldingWithArbeidsgiverStatus>,
        currentSykmelding: SykmeldingWithArbeidsgiverStatus
    ): List<Pair<SykmeldingWithArbeidsgiverStatus, TidligereArbeidsgiverType>> =
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

    private fun decideMostRelevantSykmelding(
        filteredSykmeldinger:
            List<Pair<SykmeldingWithArbeidsgiverStatus, TidligereArbeidsgiverType>>,
        tidligereArbeidsgiverBrukerInput: String?
    ): SykmeldingWithArbeidsgiverStatus? {
        val uniqueArbeidsgiverCount =
            filteredSykmeldinger.distinctBy { it.first.arbeidsgiver?.orgnummer }.size
        updateMetricsForArbeidsgivere(uniqueArbeidsgiverCount)

        return when {
            uniqueArbeidsgiverCount == 1 ->
                findSingleRelevantSykmelding(filteredSykmeldinger, tidligereArbeidsgiverBrukerInput)
            tidligereArbeidsgiverBrukerInput == null -> null
            else ->
                findMatchingSykmeldingFromArbeidsgiver(
                    filteredSykmeldinger,
                    tidligereArbeidsgiverBrukerInput
                )
        }
    }

    private fun findSingleRelevantSykmelding(
        filtrerteSykmeldinger:
            List<Pair<SykmeldingWithArbeidsgiverStatus, TidligereArbeidsgiverType>>,
        tidligereArbeidsgiverBrukerInput: String?
    ): SykmeldingWithArbeidsgiverStatus? {
        val enkeltSykmelding = filtrerteSykmeldinger.firstOrNull()
        enkeltSykmelding?.let {
            if (
                tidligereArbeidsgiverBrukerInput == null ||
                    it.first.arbeidsgiver?.orgnummer == tidligereArbeidsgiverBrukerInput ||
                    it.first.tidligereArbeidsgiver?.orgnummer == tidligereArbeidsgiverBrukerInput
            ) {
                TIDLIGERE_ARBEIDSGIVER_COUNTER.labels(it.second.name).inc()
                logger.info("Tidligere arbeidsgiver counter er oppdatert: ${it.second.name}")
                return it.first
            }
        }
        return null
    }

    private fun updateMetricsForArbeidsgivere(unikeArbeidsgivereCount: Int) {
        ANTALL_TIDLIGERE_ARBEIDSGIVERE.labels(unikeArbeidsgivereCount.toString()).inc()
        logger.info("Antall unike arbeidsgivere oppdatert til: $unikeArbeidsgivereCount")
    }

    private fun findMatchingSykmeldingFromArbeidsgiver(
        filtrerteSykmeldinger:
            List<Pair<SykmeldingWithArbeidsgiverStatus, TidligereArbeidsgiverType>>,
        tidligereArbeidsgiverBrukerInput: String
    ): SykmeldingWithArbeidsgiverStatus? {
        // Filtrer sykmeldinger for å finne en som matcher med den angitte arbeidsgiveren fra
        // brukeren
        val sykmeldingMatch =
            filtrerteSykmeldinger.firstOrNull { sykmelding ->
                sykmelding.first.arbeidsgiver?.orgnummer == tidligereArbeidsgiverBrukerInput ||
                    sykmelding.first.tidligereArbeidsgiver?.orgnummer ==
                        tidligereArbeidsgiverBrukerInput
            }
        if (sykmeldingMatch != null) {
            TIDLIGERE_ARBEIDSGIVER_COUNTER.labels(sykmeldingMatch.second.name).inc()
            logger.info(
                "Tidligere arbeidsgiver counter er oppdatert: ${sykmeldingMatch.second.name}"
            )
        }
        return sykmeldingMatch?.first
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

    private fun findFirstFom(perioder: List<SykmeldingsperiodeDTO>): LocalDate {
        return perioder.minByOrNull { it.fom }?.fom
            ?: throw IllegalStateException("Skal ikke kunne ha periode uten fom")
    }
}
