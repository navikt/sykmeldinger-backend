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
        (fom.isAfter(tidligereSmFom) &&
            fom.isBefore(
                tidligereSmTom.plusDays(1),
            ))

    private fun isDirekteOverlappende(
        tidligereSmTom: LocalDate,
        tidligereSmFom: LocalDate,
        fom: LocalDate,
        tom: LocalDate,
    ) = (tidligereSmFom == fom && tidligereSmTom == tom)

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
        val filteredSykmeldinger =
            filterRelevantSykmeldinger(
                allSykmeldinger,
                currentSykmelding,
                tidligereArbeidsgiverBrukerInput
            )
        securelog.info(
            "Filtrerte sykmeldinger som er relevante {} {} {}",
            kv("sykmeldingId", sykmeldingId),
            kv("tidligereAgBrukerInput", tidligereArbeidsgiverBrukerInput),
            kv("antall", filteredSykmeldinger.size)
        )
        return decideMostRelevantSykmelding(
            filteredSykmeldinger,
            tidligereArbeidsgiverBrukerInput,
            sykmeldingId,
        )
    }

    private fun filterRelevantSykmeldinger(
        allSykmeldinger: List<SykmeldingWithArbeidsgiverStatus>,
        currentSykmelding: SykmeldingWithArbeidsgiverStatus,
        tidligereArbeidsgiverBrukerInput: String?
    ): List<Pair<SykmeldingWithArbeidsgiverStatus, TidligereArbeidsgiverType>> {
        val relevante =
            allSykmeldinger.filterNot { it.sykmeldingId == currentSykmelding.sykmeldingId }
        val sendte = relevante.filter { it.statusEvent == STATUS_SENDT }
        val bekreftedeWithTidligereAg =
            relevante.filter { it.tidligereArbeidsgiver?.orgnummer != null }
        val relevantsykmeldingerMappedToPairs =
            mapToRelevantPairs(sendte + bekreftedeWithTidligereAg, currentSykmelding)
        val finalRelevantSykmeldingerPairs =
            filterOutDirectOverlapp(
                bekreftedeWithTidligereAg,
                relevantsykmeldingerMappedToPairs,
                currentSykmelding,
                tidligereArbeidsgiverBrukerInput
            )
        return finalRelevantSykmeldingerPairs
    }

    private fun filterOutDirectOverlapp(
        bekreftedeSykmeldinger: List<SykmeldingWithArbeidsgiverStatus>,
        relevantsykmeldingerMappedToPairs:
            List<Pair<SykmeldingWithArbeidsgiverStatus, TidligereArbeidsgiverType>>,
        currentSykmelding: SykmeldingWithArbeidsgiverStatus,
        tidligereArbeidsgiverBrukerInput: String?
    ): List<Pair<SykmeldingWithArbeidsgiverStatus, TidligereArbeidsgiverType>> {
        val bekreftedeDirectOverlapp =
            bekreftedeSykmeldinger.filter {
                getBekreftedeDirectOverlapp(
                    tidligereArbeidsgiverBrukerInput = tidligereArbeidsgiverBrukerInput,
                    tidligereAgPaaTidligereSykmelding = it.tidligereArbeidsgiver!!.orgnummer,
                    fom = findFirstFom(currentSykmelding.sykmeldingsperioder),
                    tom = findLastTom(currentSykmelding.sykmeldingsperioder),
                    sykmeldingsperioder = it.sykmeldingsperioder
                )
            }
        val updatedMapOfRelevantSykmeldingerPairs =
            removeBekreftedDirectOverlapp(
                bekreftedeDirectOverlapp,
                relevantsykmeldingerMappedToPairs
            )
        return updatedMapOfRelevantSykmeldingerPairs
    }

    private fun removeBekreftedDirectOverlapp(
        bekreftedeDirectOverlapp: List<SykmeldingWithArbeidsgiverStatus>,
        relevantsykmeldingerMappedToPairs:
            List<Pair<SykmeldingWithArbeidsgiverStatus, TidligereArbeidsgiverType>>
    ): List<Pair<SykmeldingWithArbeidsgiverStatus, TidligereArbeidsgiverType>> {
        var filteredList = relevantsykmeldingerMappedToPairs
        bekreftedeDirectOverlapp.forEach { overlapp ->
            filteredList =
                filteredList.filterNot { relevantPair ->
                    isKantTilKant(
                        relevantPair.first.sykmeldingsperioder,
                        findFirstFom(overlapp.sykmeldingsperioder)
                    ) || relevantPair.first.sykmeldingId == overlapp.sykmeldingId
                }
        }
        return filteredList
    }

    private fun mapToRelevantPairs(
        sykmeldingWithArbeidsgiverStatuses: List<SykmeldingWithArbeidsgiverStatus>,
        currentSykmelding: SykmeldingWithArbeidsgiverStatus
    ): List<Pair<SykmeldingWithArbeidsgiverStatus, TidligereArbeidsgiverType>> {
        return sykmeldingWithArbeidsgiverStatuses.mapNotNull {
            val tidligereArbeidsgiverType =
                tidligereArbeidsgiverPeriodeType(
                    findFirstFom(currentSykmelding.sykmeldingsperioder),
                    it.sykmeldingsperioder
                )
            if (tidligereArbeidsgiverType != TidligereArbeidsgiverType.INGEN)
                it to tidligereArbeidsgiverType
            else null
        }
    }

    private fun getBekreftedeDirectOverlapp(
        tidligereArbeidsgiverBrukerInput: String?,
        tidligereAgPaaTidligereSykmelding: String,
        fom: LocalDate,
        tom: LocalDate,
        sykmeldingsperioder: List<SykmeldingsperiodeDTO>
    ): Boolean {
        return (isDirekteOverlappende(
            tidligereSmTom = sykmeldingsperioder.maxOf { it.tom },
            tidligereSmFom = sykmeldingsperioder.minOf { it.fom },
            fom = fom,
            tom = tom
        ) &&
            (tidligereArbeidsgiverBrukerInput == tidligereAgPaaTidligereSykmelding ||
                tidligereArbeidsgiverBrukerInput == null))
    }

    private fun decideMostRelevantSykmelding(
        filteredSykmeldinger:
            List<Pair<SykmeldingWithArbeidsgiverStatus, TidligereArbeidsgiverType>>,
        tidligereArbeidsgiverBrukerInput: String?,
        sykmeldingId: String,
    ): SykmeldingWithArbeidsgiverStatus? {
        if (filteredSykmeldinger.isEmpty()) return null
        val uniqueArbeidsgiverCount = getUniqueArbeidsgiverCount(filteredSykmeldinger)
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
            sykmeldingId,
        )
    }

    private fun getUniqueArbeidsgiverCount(
        filteredSykmeldinger:
            List<Pair<SykmeldingWithArbeidsgiverStatus, TidligereArbeidsgiverType>>
    ): Int {
        val uniqueArbeidsgiverList =
            filteredSykmeldinger
                .filter { it.first.tidligereArbeidsgiver == null }
                .distinctBy { it.first.arbeidsgiver?.orgnummer }
                .map { it.first.arbeidsgiver?.orgnummer }
        val uniqueTidligereArbeidsgiverList =
            filteredSykmeldinger
                .filter { it.first.arbeidsgiver == null }
                .distinctBy { it.first.tidligereArbeidsgiver?.orgnummer }
                .map { it.first.tidligereArbeidsgiver?.orgnummer }
        return (uniqueTidligereArbeidsgiverList + uniqueArbeidsgiverList).distinctBy { it }.size
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
        if (tidligereArbeidsgiverBrukerInput == null) {
            securelog.info(
                "TidligereArbeidsgivereBrukerInput felt er null i flere-relevante-arbeidsgivere-flyten. Dette skal ikke være mulig for sykmeldingId $sykmeldingId"
            )
            return null
        }
        val sykmeldingMatch =
            filtrerteSykmeldinger.firstOrNull { sykmelding ->
                sykmelding.first.arbeidsgiver?.orgnummer == tidligereArbeidsgiverBrukerInput ||
                    sykmelding.first.tidligereArbeidsgiver?.orgnummer ==
                        tidligereArbeidsgiverBrukerInput
            }
        if (sykmeldingMatch != null) {
            logInfoAndUpdateMetrics(sykmeldingId, tidligereArbeidsgiverBrukerInput, sykmeldingMatch)
            return sykmeldingMatch.first
        }
        securelog.info(
            "Fant ingen relevant sykmelding match i flere-relevante-arbeidsgivere-flyten{} {}",
            kv("sykmeldingId", sykmeldingId),
            kv("tidligereAgBrukerInput", tidligereArbeidsgiverBrukerInput),
        )
        return null
    }

    private fun logInfoAndUpdateMetrics(
        sykmeldingId: String,
        tidligereArbeidsgiverBrukerInput: String?,
        sykmeldingMatch: Pair<SykmeldingWithArbeidsgiverStatus, TidligereArbeidsgiverType>
    ) {
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
        logger.info("Tidligere arbeidsgiver counter er oppdatert: ${sykmeldingMatch.second.name}")
    }

    private fun tidligereArbeidsgiverPeriodeType(
        currentSykmeldingFirstFomDate: LocalDate,
        sykmeldingsperioder: List<SykmeldingsperiodeDTO>
    ): TidligereArbeidsgiverType {
        val kantTilKant = isKantTilKant(sykmeldingsperioder, currentSykmeldingFirstFomDate)
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

    private fun isKantTilKant(perioder: List<SykmeldingsperiodeDTO>, dag: LocalDate): Boolean {
        val sisteTom = findLastTom(perioder)
        return !isWorkingdaysBetween(sisteTom, dag)
    }

    private fun findFirstFom(perioder: List<SykmeldingsperiodeDTO>): LocalDate {
        return perioder.minByOrNull { it.fom }?.fom
            ?: throw IllegalStateException("Skal ikke kunne ha periode uten fom")
    }

    private fun findLastTom(perioder: List<SykmeldingsperiodeDTO>): LocalDate {
        return perioder.maxByOrNull { it.tom }?.tom
            ?: throw IllegalStateException("Skal ikke kunne ha periode uten tom")
    }
}
