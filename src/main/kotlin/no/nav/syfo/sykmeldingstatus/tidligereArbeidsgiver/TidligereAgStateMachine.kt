package no.nav.syfo.sykmeldingstatus.tidligereArbeidsgiver

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import no.nav.syfo.metrics.ANTALL_TIDLIGERE_ARBEIDSGIVERE
import no.nav.syfo.metrics.TIDLIGERE_ARBEIDSGIVER_COUNTER
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmeldingstatus.kafka.SykmeldingWithArbeidsgiverStatus
import no.nav.syfo.sykmeldingstatus.kafka.model.STATUS_SENDT
import no.nav.syfo.utils.logger

class TidligereAgStateMachine(var tilstand: State = Start) {
    private val logger = logger()
    private var tidligereArbeidsgiver: SykmeldingWithArbeidsgiverStatus? = null
    private lateinit var allSykmeldinger: List<SykmeldingWithArbeidsgiverStatus>
    private lateinit var currentSykmelding: SykmeldingWithArbeidsgiverStatus
    private var relevantsykmeldingerMappedToPairs:
        List<Pair<SykmeldingWithArbeidsgiverStatus, TidligereArbeidsgiverType>>? =
        null

    private lateinit var sykmeldingId: String
    private var tidligereArbeidsgiverInput: String? = null

    fun setTidligereArbeidsgiver(
        sykmeldingId: String,
        tidligereArbeidsgiverInput: String?,
        alleSykmeldingerDb: List<SykmeldingWithArbeidsgiverStatus>
    ) {
        this.sykmeldingId = sykmeldingId
        this.tidligereArbeidsgiverInput = tidligereArbeidsgiverInput
        this.allSykmeldinger = alleSykmeldingerDb
        tilstand.entering(this)
    }

    fun getTidligereArbeidsgiver(): SykmeldingWithArbeidsgiverStatus? {
        return tidligereArbeidsgiver
    }

    fun changeState() {
        tilstand.entering(this)
    }

    sealed interface State {
        val type: StateType

        fun entering(context: TidligereAgStateMachine) {}
    }

    internal data object Start : State {
        override val type = StateType.start

        override fun entering(context: TidligereAgStateMachine) {
            context.tilstand =
                when {
                    context.allSykmeldinger.isNotEmpty() -> SjekkerSykmeldinger
                    else -> TidligereAgIkkeFunnet
                }
            context.changeState()
        }
    }

    internal data object SjekkerSykmeldinger : State {
        override val type = StateType.sjekkerSykmeldinger

        override fun entering(context: TidligereAgStateMachine) {
            context.currentSykmelding =
                context.allSykmeldinger.find { it.sykmeldingId == context.sykmeldingId } ?: return
            context.filterRelevantSykmeldinger()
            context.tilstand =
                when {
                    context.relevantsykmeldingerMappedToPairs.isNullOrEmpty() -> TidligereAgFunnet
                    else -> FinnMatch
                }
            context.changeState()
        }
    }

    internal data object FinnMatch : State {
        override val type = StateType.sjekkerSykmeldinger

        override fun entering(context: TidligereAgStateMachine) {
            context.decideMostRelevantSykmelding()
            context.tilstand =
                when {
                    context.tidligereArbeidsgiver != null -> TidligereAgFunnet
                    else -> TidligereAgIkkeFunnet
                }
            context.changeState()
        }
    }

    internal data object TidligereAgIkkeFunnet : State {
        override val type = StateType.tidligereAgikkeFunnet
    }

    internal data object TidligereAgFunnet : State {
        override val type = StateType.tidligereAgikkeFunnet
    }

    private fun decideMostRelevantSykmelding() {
        val uniqueArbeidsgiverCount = getUniqueArbeidsgiverCount()
        updateMetricsForArbeidsgivere(uniqueArbeidsgiverCount)
        if (uniqueArbeidsgiverCount == 1 && tidligereArbeidsgiverInput == null) {
            this.tidligereArbeidsgiver = findSingleRelevantSykmelding()
            return
        }
        this.tidligereArbeidsgiver = findMatchingSykmeldingFromArbeidsgiver()
    }

    private fun findMatchingSykmeldingFromArbeidsgiver(): SykmeldingWithArbeidsgiverStatus? {
        if (tidligereArbeidsgiverInput == null) {
            return null
        }
        val sykmeldingMatch =
            relevantsykmeldingerMappedToPairs!!.firstOrNull { sykmelding ->
                sykmelding.first.arbeidsgiver?.orgnummer == tidligereArbeidsgiverInput ||
                    sykmelding.first.tidligereArbeidsgiver?.orgnummer == tidligereArbeidsgiverInput
            }
        if (sykmeldingMatch != null) {
            TIDLIGERE_ARBEIDSGIVER_COUNTER.labels(sykmeldingMatch.second.name).inc()
        }
        return sykmeldingMatch?.first
    }

    private fun findSingleRelevantSykmelding(): SykmeldingWithArbeidsgiverStatus {
        val relevantSykmelding = relevantsykmeldingerMappedToPairs!!.first()
        TIDLIGERE_ARBEIDSGIVER_COUNTER.labels(relevantSykmelding.second.name).inc()
        logger.info(
            "Tidligere arbeidsgiver counter er oppdatert: ${relevantSykmelding.second.name}"
        )
        return relevantSykmelding.first
    }

    private fun updateMetricsForArbeidsgivere(unikeArbeidsgivereCount: Int) {
        ANTALL_TIDLIGERE_ARBEIDSGIVERE.labels(unikeArbeidsgivereCount.toString()).inc()
        logger.info(
            "Antall unike arbeidsgivere oppdatert til: $unikeArbeidsgivereCount for sykmeldingId: $sykmeldingId"
        )
    }

    private fun getUniqueArbeidsgiverCount(): Int {
        val uniqueArbeidsgiverList =
            relevantsykmeldingerMappedToPairs!!
                .filter { it.first.tidligereArbeidsgiver == null }
                .distinctBy { it.first.arbeidsgiver?.orgnummer }
                .map { it.first.arbeidsgiver?.orgnummer }
        val uniqueTidligereArbeidsgiverList =
            relevantsykmeldingerMappedToPairs!!
                .filter { it.first.arbeidsgiver == null }
                .distinctBy { it.first.tidligereArbeidsgiver?.orgnummer }
                .map { it.first.tidligereArbeidsgiver?.orgnummer }
        return (uniqueTidligereArbeidsgiverList + uniqueArbeidsgiverList).distinctBy { it }.size
    }

    private fun filterRelevantSykmeldinger() {
        val relevante = allSykmeldinger.filterNot { it.sykmeldingId == this.sykmeldingId }
        val sendte = relevante.filter { it.statusEvent == STATUS_SENDT }
        val bekreftedeWithTidligereAg =
            relevante.filter { it.tidligereArbeidsgiver?.orgnummer != null }
        val potentialRelevantPairs =
            mapToRelevantPairs(sendte + bekreftedeWithTidligereAg, currentSykmelding)
        if (potentialRelevantPairs.isEmpty()) return
        val finalRelevantSykmeldingerPairs =
            removeDirectOverlapp(bekreftedeWithTidligereAg, potentialRelevantPairs)
        relevantsykmeldingerMappedToPairs = finalRelevantSykmeldingerPairs
    }

    private fun removeDirectOverlapp(
        bekreftedeSykmeldinger: List<SykmeldingWithArbeidsgiverStatus>,
        potentialRelevantPairs:
            List<Pair<SykmeldingWithArbeidsgiverStatus, TidligereArbeidsgiverType>>,
    ): List<Pair<SykmeldingWithArbeidsgiverStatus, TidligereArbeidsgiverType>> {
        val bekreftedeDirectOverlapp =
            bekreftedeSykmeldinger.filter {
                getBekreftedeDirectOverlapp(
                    tidligereArbeidsgiverBrukerInput = tidligereArbeidsgiverInput,
                    tidligereAgPaaTidligereSykmelding = it.tidligereArbeidsgiver!!.orgnummer,
                    sykmeldingsperioder = it.sykmeldingsperioder
                )
            }
        val updatedMapOfRelevantSykmeldingerPairs =
            removeBekreftedDirectOverlapp(bekreftedeDirectOverlapp, potentialRelevantPairs)
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

    private fun getBekreftedeDirectOverlapp(
        tidligereArbeidsgiverBrukerInput: String?,
        tidligereAgPaaTidligereSykmelding: String,
        sykmeldingsperioder: List<SykmeldingsperiodeDTO>
    ): Boolean {
        return (isDirekteOverlappende(
            tidligereSmTom = sykmeldingsperioder.maxOf { it.tom },
            tidligereSmFom = sykmeldingsperioder.minOf { it.fom },
            fom = findFirstFom(currentSykmelding.sykmeldingsperioder),
            tom = findLastTom(currentSykmelding.sykmeldingsperioder),
        ) &&
            (tidligereArbeidsgiverBrukerInput == tidligereAgPaaTidligereSykmelding ||
                tidligereArbeidsgiverBrukerInput == null))
    }

    private fun isDirekteOverlappende(
        tidligereSmTom: LocalDate,
        tidligereSmFom: LocalDate,
        fom: LocalDate,
        tom: LocalDate,
    ) = (tidligereSmFom == fom && tidligereSmTom == tom)

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

    private fun isOverlappende(
        tidligereSmTom: LocalDate,
        tidligereSmFom: LocalDate,
        fom: LocalDate
    ) =
        (fom.isAfter(tidligereSmFom) &&
            fom.isBefore(
                tidligereSmTom.plusDays(1),
            ))

    private fun isKantTilKant(perioder: List<SykmeldingsperiodeDTO>, dag: LocalDate): Boolean {
        val sisteTom = findLastTom(perioder)
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

    private fun findLastTom(perioder: List<SykmeldingsperiodeDTO>): LocalDate {
        return perioder.maxByOrNull { it.tom }?.tom
            ?: throw IllegalStateException("Skal ikke kunne ha periode uten tom")
    }
}

enum class StateType {
    start,
    sjekkerSykmeldinger,
    finnMatch,
    tidligereAgFunnet,
    tidligereAgikkeFunnet
}
