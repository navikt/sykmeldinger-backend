package no.nav.syfo.sykmeldingstatus.model

import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.syfo.metrics.ANTALL_TIDLIGERE_ARBEIDSGIVERE
import no.nav.syfo.metrics.TIDLIGERE_ARBEIDSGIVER_COUNTER
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.db.SykmeldingStatusDb
import no.nav.syfo.sykmeldingstatus.exception.UserInputFlereArbeidsgivereIsNullException
import no.nav.syfo.sykmeldingstatus.kafka.SykmeldingWithArbeidsgiverStatus
import no.nav.syfo.sykmeldingstatus.kafka.model.STATUS_SENDT
import no.nav.syfo.utils.logger
import no.nav.syfo.utils.securelog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
        if (filteredSykmeldinger.isEmpty()) return null
        val uniqueArbeidsgiverCount =
            filteredSykmeldinger.distinctBy { it.first.arbeidsgiver?.orgnummer }.size
        updateMetricsForArbeidsgivere(uniqueArbeidsgiverCount, sykmeldingId)
        securelog.info(
            "Finner mest relevante sykmelding av de filterte sykmeldingene for å finne tidligere arbeidsgiver {} {} {}",
            kv("sykmeldingId", sykmeldingId),
            kv("tidligereAgBrukerInput", tidligereArbeidsgiverBrukerInput),
            kv(
                "antall unike arbeidsgivere kant til kant eller overlappende",
                uniqueArbeidsgiverCount
            )
        )
        if (uniqueArbeidsgiverCount == 1 && tidligereArbeidsgiverBrukerInput == null) {
            return findSingleRelevantSykmelding(filteredSykmeldinger, sykmeldingId)
        }
        return findMatchingSykmeldingFromArbeidsgiver(
            filteredSykmeldinger,
            tidligereArbeidsgiverBrukerInput,
            sykmeldingId
        )
    }

    private fun findSingleRelevantSykmelding(
        filtrerteSykmeldinger:
            List<Pair<SykmeldingWithArbeidsgiverStatus, TidligereArbeidsgiverType>>,
        sykmeldingId: String
    ): SykmeldingWithArbeidsgiverStatus? {
        securelog.info(
            "Entrer kun-en-relevant-arbeidsgiver-flyten {}",
            kv("sykmeldingId", sykmeldingId),
        )
        val relevantSykmelding = filtrerteSykmeldinger.firstOrNull()
        if (relevantSykmelding == null) {
            securelog.info("Fant ikke relevant sykmelding for {}", kv("sykmeldingId", sykmeldingId))
            return relevantSykmelding
        }
        securelog.info(
            "Fant relevant sykmelding i kun-en-relevant-arbeidsgiver-flyten for {} {} {}",
            kv("sykmeldingId", sykmeldingId),
            kv("relevanteSykmelding orgnummer", relevantSykmelding.first.arbeidsgiver?.orgnummer),
            kv(
                "relevanteSykmelding tidligereArbeidsgiver orgnummer",
                relevantSykmelding.first.tidligereArbeidsgiver?.orgnummer
            )
        )
        TIDLIGERE_ARBEIDSGIVER_COUNTER.labels(relevantSykmelding.second.name).inc()
        logger.info(
            "Tidligere arbeidsgiver counter er oppdatert: ${relevantSykmelding.second.name}"
        )
        return relevantSykmelding.first
    }

    private fun updateMetricsForArbeidsgivere(unikeArbeidsgivereCount: Int, sykmeldingId: String) {
        ANTALL_TIDLIGERE_ARBEIDSGIVERE.labels(unikeArbeidsgivereCount.toString()).inc()
        logger.info(
            "Antall unike arbeidsgivere oppdatert til: $unikeArbeidsgivereCount for sykmeldingId: $sykmeldingId"
        )
    }

    private fun findMatchingSykmeldingFromArbeidsgiver(
        filtrerteSykmeldinger:
            List<Pair<SykmeldingWithArbeidsgiverStatus, TidligereArbeidsgiverType>>,
        tidligereArbeidsgiverBrukerInput: String?,
        sykmeldingId: String,
    ): SykmeldingWithArbeidsgiverStatus? {
        securelog.info(
            "Entrer flere-relevante-arbeidsgivere-flyten {} {}",
            kv("sykmeldingId", sykmeldingId),
            kv("tidligereAgBrukerInput", tidligereArbeidsgiverBrukerInput)
        )
        if (tidligereArbeidsgiverBrukerInput == null)
            throw UserInputFlereArbeidsgivereIsNullException(
                "TidligereArbeidsgivereBrukerInput felt er null i flere-relevante-arbeidsgivere-flyten. Dette skal ikke være mulig for sykmeldingId $sykmeldingId"
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
