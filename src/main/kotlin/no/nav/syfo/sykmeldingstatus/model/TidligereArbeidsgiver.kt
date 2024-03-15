package no.nav.syfo.sykmeldingstatus.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.syfo.metrics.ANTALL_TIDLIGERE_ARBEIDSGIVERE
import no.nav.syfo.metrics.TIDLIGERE_ARBEIDSGIVER_COUNTER
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.db.SykmeldingStatusDb
import no.nav.syfo.sykmeldingstatus.kafka.SykmeldingWithArbeidsgiverStatus
import no.nav.syfo.sykmeldingstatus.kafka.model.STATUS_SENDT
import no.nav.syfo.utils.logger
import no.nav.syfo.utils.securelog

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
            securelog.error(
                "Exception while finding tidligereArbeidsgiver {} {} {}",
                kv("SykmeldlingId", sykmeldingId),
                kv("fødselsnummer", fnr),
                kv("tidligereAgBrukerInput", tidligereArbeidsgiverBrukerInput)
            )
            logger.error("Exception while finding tidligereArbeidsgiver", ex)
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
        securelog.info(
            "Filtererer ut relevante sykmeldinger for å finne tidligere arbeidsgiver for {} {} {}",
            kv("sykmeldingId", sykmeldingId),
            kv("tidligereAgBrukerInput", tidligereArbeidsgiverBrukerInput),
            kv(
                "periode",
                currentSykmelding.sykmeldingsperioder.joinToString(separator = ", ") { periode ->
                    "fra ${periode.fom} til ${periode.tom}"
                }
            )
        )
        val filteredSykmeldinger = filterRelevantSykmeldinger(allSykmeldinger, currentSykmelding)
        securelog.info(
            "Filtrerte sykmeldinger som er relevante {} {} {}",
            kv("sykmeldingId", sykmeldingId),
            kv("tidligereAgBrukerInput", tidligereArbeidsgiverBrukerInput),
            kv("antall", filteredSykmeldinger.size)
        )
        return decideMostRelevantSykmelding(
            filteredSykmeldinger,
            tidligereArbeidsgiverBrukerInput,
            sykmeldingId
        )
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
        tidligereArbeidsgiverBrukerInput: String?,
        sykmeldingId: String
    ): SykmeldingWithArbeidsgiverStatus? {
        val uniqueArbeidsgiverCount =
            filteredSykmeldinger.distinctBy { it.first.arbeidsgiver?.orgnummer }.size
        updateMetricsForArbeidsgivere(uniqueArbeidsgiverCount)
        securelog.info(
            "Finner mest relevante sykmelding av de filterte sykmeldingene for å finne tidligere arbeidsgiver {} {} {}",
            kv("sykmeldingId", sykmeldingId),
            kv("tidligereAgBrukerInput", tidligereArbeidsgiverBrukerInput),
            kv(
                "antall unike arbeidsgivere kant til kant eller overlappende",
                uniqueArbeidsgiverCount
            )
        )
        return when {
            uniqueArbeidsgiverCount == 1 ->
                findSingleRelevantSykmelding(
                    filteredSykmeldinger,
                    tidligereArbeidsgiverBrukerInput,
                    sykmeldingId
                )
            tidligereArbeidsgiverBrukerInput == null -> {
                securelog.info(
                    "Finner ikke tidligere arbeidsgiver fordi antall relevante arbeidsgivere er > 1 og brukerInput er null {} {} {}",
                    kv("sykmeldingId", sykmeldingId),
                    kv("tidligereAgBrukerInput", tidligereArbeidsgiverBrukerInput),
                    kv(
                        "antall unike arbeidsgivere kant til kant eller overlappende",
                        uniqueArbeidsgiverCount
                    )
                )
                null
            }
            else ->
                findMatchingSykmeldingFromArbeidsgiver(
                    filteredSykmeldinger,
                    tidligereArbeidsgiverBrukerInput,
                    sykmeldingId
                )
        }
    }

    private fun findSingleRelevantSykmelding(
        filtrerteSykmeldinger:
            List<Pair<SykmeldingWithArbeidsgiverStatus, TidligereArbeidsgiverType>>,
        tidligereArbeidsgiverBrukerInput: String?,
        sykmeldingId: String
    ): SykmeldingWithArbeidsgiverStatus? {
        securelog.info(
            "Entrer kun-en-relevant-arbeidsgiver-flyten {} {}",
            kv("sykmeldingId", sykmeldingId),
            kv("tidligereAgBrukerInput", tidligereArbeidsgiverBrukerInput),
        )
        val relevantSykmelding = filtrerteSykmeldinger.firstOrNull()
        relevantSykmelding?.let {
            if (
                tidligereArbeidsgiverBrukerInput == null ||
                    it.first.arbeidsgiver?.orgnummer == tidligereArbeidsgiverBrukerInput ||
                    it.first.tidligereArbeidsgiver?.orgnummer == tidligereArbeidsgiverBrukerInput
            ) {
                securelog.info(
                    "Fant relevant sykmelding i kun-en-relevant-arbeidsgiver-flyten for {} {} {} {}",
                    kv("sykmeldingId", sykmeldingId),
                    kv("tidligereAgBrukerInput", tidligereArbeidsgiverBrukerInput),
                    kv("relevanteSykmelding orgnummer", it.first.arbeidsgiver?.orgnummer),
                    kv(
                        "relevanteSykmelding tidligereArbeidsgiver orgnummer",
                        it.first.tidligereArbeidsgiver?.orgnummer
                    )
                )
                TIDLIGERE_ARBEIDSGIVER_COUNTER.labels(it.second.name).inc()
                logger.info("Tidligere arbeidsgiver counter er oppdatert: ${it.second.name}")
                return it.first
            }
        }
        securelog.info(
            "Fant ingen relevant sykmelding i kun-en-relevant-arbeidsgiver-flyten for {} {}",
            kv("sykmeldingId", sykmeldingId),
            kv("tidligereAgBrukerInput", tidligereArbeidsgiverBrukerInput)
        )
        return null
    }

    private fun updateMetricsForArbeidsgivere(unikeArbeidsgivereCount: Int) {
        ANTALL_TIDLIGERE_ARBEIDSGIVERE.labels(unikeArbeidsgivereCount.toString()).inc()
        logger.info("Antall unike arbeidsgivere oppdatert til: $unikeArbeidsgivereCount")
    }

    private fun findMatchingSykmeldingFromArbeidsgiver(
        filtrerteSykmeldinger:
            List<Pair<SykmeldingWithArbeidsgiverStatus, TidligereArbeidsgiverType>>,
        tidligereArbeidsgiverBrukerInput: String,
        sykmeldingId: String,
    ): SykmeldingWithArbeidsgiverStatus? {
        securelog.info(
            "Entrer flere-relevante-arbeidsgivere-flyten {} {}",
            kv("sykmeldingId", sykmeldingId),
            kv("tidligereAgBrukerInput", tidligereArbeidsgiverBrukerInput)
        )
        val sykmeldingMatch =
            filtrerteSykmeldinger.firstOrNull { sykmelding ->
                sykmelding.first.arbeidsgiver?.orgnummer == tidligereArbeidsgiverBrukerInput ||
                    sykmelding.first.tidligereArbeidsgiver?.orgnummer ==
                        tidligereArbeidsgiverBrukerInput
            }
        if (sykmeldingMatch != null) {
            securelog.info(
                "Fant relevant sykmelding match i flere-relevante-arbeidsgivere-flyten {} {} {} {} {}",
                kv("sykmeldingId", sykmeldingId),
                kv("tidligereAgBrukerInput", tidligereArbeidsgiverBrukerInput),
                kv(
                    "relevanteSykmelding tidligereArbeidsgiver orgnummer",
                    sykmeldingMatch.first.tidligereArbeidsgiver?.orgnummer
                ),
                kv("relevantSykmelding orgnummer", sykmeldingMatch.first.arbeidsgiver?.orgnummer),
                kv("relevant sykmeldingId", sykmeldingMatch.first.sykmeldingId)
            )
            TIDLIGERE_ARBEIDSGIVER_COUNTER.labels(sykmeldingMatch.second.name).inc()
            logger.info(
                "Tidligere arbeidsgiver counter er oppdatert: ${sykmeldingMatch.second.name}"
            )
            return sykmeldingMatch.first
        }
        securelog.info(
            "Fant ingen relevant sykmelding match i flere-relevante-arbeidsgivere-flyten{} {}",
            kv("sykmeldingId", sykmeldingId),
            kv("tidligereAgBrukerInput", tidligereArbeidsgiverBrukerInput),
        )
        return null
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
