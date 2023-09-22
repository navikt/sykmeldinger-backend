package no.nav.syfo.sykmeldingstatus

import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.log
import no.nav.syfo.metrics.BEKREFTET_AV_BRUKER_COUNTER
import no.nav.syfo.metrics.SENDT_AV_BRUKER_COUNTER
import no.nav.syfo.metrics.TIDLIGERE_ARBEIDSGIVER_COUNTER
import no.nav.syfo.model.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.objectMapper
import no.nav.syfo.securelog
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v2.ArbeidssituasjonDTO
import no.nav.syfo.sykmeldingstatus.api.v2.ArbeidssituasjonDTO.ARBEIDSLEDIG
import no.nav.syfo.sykmeldingstatus.api.v2.EndreEgenmeldingsdagerEvent
import no.nav.syfo.sykmeldingstatus.api.v2.SykmeldingUserEvent
import no.nav.syfo.sykmeldingstatus.db.SykmeldingStatusDb
import no.nav.syfo.sykmeldingstatus.exception.InvalidSykmeldingStatusException
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.kafka.tilSykmeldingStatusKafkaEventDTO
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class SykmeldingStatusService(
    private val sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer,
    private val arbeidsgiverService: ArbeidsgiverService,
    private val sykmeldingStatusDb: SykmeldingStatusDb,
    private val sykmeldingService: SykmeldingService
) {
    companion object {
        private val statusStates: Map<StatusEventDTO, List<StatusEventDTO>> =
            mapOf(
                Pair(
                    StatusEventDTO.APEN,
                    listOf(
                        StatusEventDTO.BEKREFTET,
                        StatusEventDTO.AVBRUTT,
                        StatusEventDTO.SENDT,
                        StatusEventDTO.APEN,
                        StatusEventDTO.UTGATT,
                    ),
                ),
                Pair(
                    StatusEventDTO.BEKREFTET,
                    listOf(
                        StatusEventDTO.APEN,
                        StatusEventDTO.AVBRUTT,
                        StatusEventDTO.BEKREFTET,
                        StatusEventDTO.SENDT,
                    ),
                ),
                Pair(StatusEventDTO.SENDT, emptyList()),
                Pair(
                    StatusEventDTO.AVBRUTT,
                    listOf(
                        StatusEventDTO.APEN,
                        StatusEventDTO.SENDT,
                        StatusEventDTO.BEKREFTET,
                        StatusEventDTO.AVBRUTT,
                    ),
                ),
                Pair(StatusEventDTO.UTGATT, listOf(StatusEventDTO.AVBRUTT)),
            )
        private val statusStatesAvvistSykmelding: Map<StatusEventDTO, List<StatusEventDTO>> =
            mapOf(
                Pair(StatusEventDTO.APEN, listOf(StatusEventDTO.BEKREFTET)),
                Pair(StatusEventDTO.BEKREFTET, emptyList()),
            )
        private val statusStatesEgenmelding: Map<StatusEventDTO, List<StatusEventDTO>> =
            mapOf(
                Pair(StatusEventDTO.APEN, listOf(StatusEventDTO.BEKREFTET, StatusEventDTO.AVBRUTT)),
                Pair(StatusEventDTO.BEKREFTET, emptyList()),
                Pair(StatusEventDTO.AVBRUTT, emptyList()),
            )
    }

    suspend fun registrerStatus(
        sykmeldingStatusEventDTO: SykmeldingStatusEventDTO,
        sykmeldingId: String,
        source: String,
        fnr: String,
    ) {
        val sisteStatus = hentSisteStatusOgSjekkTilgang(sykmeldingId, fnr)
        if (
            canChangeStatus(
                nyStatusEvent = sykmeldingStatusEventDTO.statusEvent,
                sisteStatus = sisteStatus.statusEvent,
                erAvvist = sisteStatus.erAvvist,
                erEgenmeldt = sisteStatus.erEgenmeldt,
                sykmeldingId = sykmeldingId,
            )
        ) {
            val sykmeldingStatusKafkaEventDTO =
                sykmeldingStatusEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId)
            sykmeldingStatusKafkaProducer.send(
                sykmeldingStatusKafkaEventDTO = sykmeldingStatusKafkaEventDTO,
                source = source,
                fnr = fnr,
            )
            sykmeldingStatusDb.insertStatus(sykmeldingStatusKafkaEventDTO)
        }
    }

    suspend fun registrerUserEvent(
        sykmeldingUserEvent: SykmeldingUserEvent,
        sykmeldingId: String,
        fnr: String,
    ) {
        val sisteStatus = hentSisteStatusOgSjekkTilgang(sykmeldingId, fnr)
        val nesteStatus = sykmeldingUserEvent.toStatusEvent()
        if (
            canChangeStatus(
                nyStatusEvent = nesteStatus,
                sisteStatus = sisteStatus.statusEvent,
                erAvvist = sisteStatus.erAvvist,
                erEgenmeldt = sisteStatus.erEgenmeldt,
                sykmeldingId = sykmeldingId,
            )
        ) {
            val arbeidsgiver =
                when (nesteStatus) {
                    StatusEventDTO.SENDT ->
                        getArbeidsgiver(
                            fnr,
                            sykmeldingId,
                            sykmeldingUserEvent.arbeidsgiverOrgnummer!!.svar,
                        )
                    else -> null
                }
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)

            val tidligereArbeidsgiver =
                when (sykmeldingUserEvent.arbeidssituasjon.svar) {
                    ARBEIDSLEDIG -> tidligereArbeidsgiver(fnr, sykmeldingId, nesteStatus)
                    else -> null
                }

            val sykmeldingStatusKafkaEventDTO =
                sykmeldingUserEvent.tilSykmeldingStatusKafkaEventDTO(
                    timestamp,
                    sykmeldingId,
                    arbeidsgiver,
                    tidligereArbeidsgiver,
                )

            if (tidligereArbeidsgiver != null) {
                securelog.info(
                    "legger til tidligere arbeidsgiver for fnr: $fnr orgnummer: ${sykmeldingStatusKafkaEventDTO.tidligereArbeidsgiver?.orgnummer} sykmeldingsId: $sykmeldingId",
                )
            }

            sykmeldingStatusKafkaProducer.send(
                sykmeldingStatusKafkaEventDTO = sykmeldingStatusKafkaEventDTO,
                source = "user",
                fnr = fnr,
            )
            sykmeldingStatusDb.insertStatus(sykmeldingStatusKafkaEventDTO)

            when (nesteStatus) {
                StatusEventDTO.SENDT -> SENDT_AV_BRUKER_COUNTER.inc()
                StatusEventDTO.BEKREFTET -> BEKREFTET_AV_BRUKER_COUNTER.inc()
                else -> Unit
            }
        }
    }

    private suspend fun tidligereArbeidsgiver(
        sykmeldtFnr: String,
        sykmeldingId: String,
        nesteStatus: StatusEventDTO
    ): TidligereArbeidsgiverDTO? {
        val currentSykmelding = fetchSykmelding(sykmeldtFnr, sykmeldingId)

        val currentSykmeldingFirstFomDate =
            if (currentSykmelding != null) {
                finnForsteFom(currentSykmelding.sykmeldingsperioder)
            } else {
                throw IllegalStateException(
                    "Skal finnes ein sykmelding med sykmeldingsid: $sykmeldingId",
                )
            }

        return if (nesteStatus == StatusEventDTO.BEKREFTET) {
            return opprettTidligereArbeidsgiver(sykmeldtFnr, currentSykmeldingFirstFomDate)
        } else {
            null
        }
    }

    private suspend fun opprettTidligereArbeidsgiver(
        sykmeldtFnr: String,
        currentSykmeldingFirstFomDate: LocalDate
    ): TidligereArbeidsgiverDTO? {
        val sisteSykmelding = findLastSendtSykmelding(sykmeldtFnr, currentSykmeldingFirstFomDate)
        return sisteSykmelding?.sykmeldingStatus?.arbeidsgiver?.let {
            TidligereArbeidsgiverDTO(
                orgNavn = it.orgNavn,
                orgnummer = it.orgnummer,
                sykmeldingsId = sisteSykmelding.id,
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
        fom.isAfter(tidligereSmFom.minusDays(1)) &&
            fom.isBefore(
                    tidligereSmTom.plusDays(1),
                )
                .also { if (it) TIDLIGERE_ARBEIDSGIVER_COUNTER.labels("overlappende").inc() }

    private suspend fun findLastSendtSykmelding(
        fnr: String,
        currentSykmeldingFirstFomDate: LocalDate
    ): SykmeldingDTO? {

        val alleSykmeldinger = sykmeldingService.getSykmeldinger(fnr)
        log.info("antall sykmeldinger ${alleSykmeldinger.size}")
        val sykmeldinger =
            alleSykmeldinger
                .filter {
                    it.sykmeldingStatus.statusEvent == StatusEventDTO.SENDT.toString() ||
                        it.sykmeldingStatus.tidligereArbeidsgiver?.orgnummer != null
                }
                .filter {
                    sisteTomIKantMedDag(it.sykmeldingsperioder, currentSykmeldingFirstFomDate) ||
                        isOverlappende(
                            tidligereSmTom = it.sykmeldingsperioder.maxOf { it.tom },
                            tidligereSmFom = it.sykmeldingsperioder.minOf { it.fom },
                            fom = currentSykmeldingFirstFomDate,
                        )
                }

        if (sykmeldinger.distinctBy { it.sykmeldingStatus.arbeidsgiver?.orgnummer }.size != 1) {
            return null
        }
        return sykmeldinger.firstOrNull()
    }

    private fun sisteTomIKantMedDag(
        perioder: List<SykmeldingsperiodeDTO>,
        dag: LocalDate
    ): Boolean {
        val sisteTom =
            perioder.maxByOrNull { it.tom }?.tom
                ?: throw IllegalStateException("Skal ikke kunne ha periode uten tom")
        return !isWorkingdaysBetween(sisteTom, dag).also {
            if (it) TIDLIGERE_ARBEIDSGIVER_COUNTER.labels("kant_til_kant").inc()
        }
    }

    private suspend fun fetchSykmelding(fnr: String, sykmeldingId: String): SykmeldingDTO? {
        return sykmeldingService.getSykmelding(fnr, sykmeldingId)
    }

    private fun finnForsteFom(perioder: List<SykmeldingsperiodeDTO>): LocalDate {
        return perioder.minByOrNull { it.fom }?.fom
            ?: throw IllegalStateException("Skal ikke kunne ha periode uten fom")
    }

    suspend fun endreEgenmeldingsdager(
        sykmeldingId: String,
        egenmeldingsdagerEvent: EndreEgenmeldingsdagerEvent,
        fnr: String
    ) {
        val sykmeldingStatusKafkaEventDTO: SykmeldingStatusKafkaEventDTO =
            sykmeldingStatusDb.getSykmeldingStatus(sykmeldingId, fnr)

        val sykmeldingStatusKafkaEventDTOUpdated =
            sykmeldingStatusKafkaEventDTO.copy(
                sporsmals =
                    updateEgenemeldingsdagerSporsmal(
                        sykmeldingStatusKafkaEventDTO.sporsmals,
                        egenmeldingsdagerEvent,
                    ),
                timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                erSvarOppdatering = true,
            )

        sykmeldingStatusKafkaProducer.send(
            sykmeldingStatusKafkaEventDTO = sykmeldingStatusKafkaEventDTOUpdated,
            source = "user",
            fnr = fnr,
        )

        sykmeldingStatusDb.insertStatus(sykmeldingStatusKafkaEventDTO)
    }

    private fun updateEgenemeldingsdagerSporsmal(
        sporsmalSvar: List<SporsmalOgSvarDTO>?,
        egenmeldingsdagerEvent: EndreEgenmeldingsdagerEvent
    ): List<SporsmalOgSvarDTO> {
        requireNotNull(sporsmalSvar) {
            "Forsøkte å oppdatere egenmeldingsdager, men spørsmål og svar er ikke satt."
        }
        return sporsmalSvar
            .filter { it.shortName != ShortNameDTO.EGENMELDINGSDAGER }
            .let {
                if (egenmeldingsdagerEvent.dager.isNotEmpty()) {
                    it.plus(
                        listOf(
                            SporsmalOgSvarDTO(
                                tekst = egenmeldingsdagerEvent.tekst,
                                shortName = ShortNameDTO.EGENMELDINGSDAGER,
                                svartype = SvartypeDTO.DAGER,
                                svar =
                                    objectMapper.writeValueAsString(egenmeldingsdagerEvent.dager),
                            ),
                        ),
                    )
                } else {
                    it
                }
            }
    }

    private suspend fun getArbeidsgiver(
        fnr: String,
        sykmeldingId: String,
        orgnummer: String,
    ): Arbeidsgiverinfo {
        return arbeidsgiverService.getArbeidsgivere(fnr).find { it.orgnummer == orgnummer }
            ?: throw InvalidSykmeldingStatusException(
                "Kan ikke sende sykmelding $sykmeldingId til orgnummer $orgnummer fordi bruker ikke har arbeidsforhold der",
            )
    }

    suspend fun registrerBekreftetAvvist(sykmeldingId: String, source: String, fnr: String) {
        val sisteStatus = hentSisteStatusOgSjekkTilgang(sykmeldingId, fnr)
        when (sisteStatus.erAvvist) {
            true -> {
                if (
                    canChangeStatus(
                        nyStatusEvent = StatusEventDTO.BEKREFTET,
                        sisteStatus = sisteStatus.statusEvent,
                        erAvvist = true,
                        erEgenmeldt = sisteStatus.erEgenmeldt,
                        sykmeldingId = sykmeldingId,
                    )
                ) {
                    val sykmeldingBekreftEventDTO =
                        SykmeldingBekreftEventDTO(
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                            sporsmalOgSvarListe = emptyList(),
                        )
                    val sykmeldingStatusKafkaEventDTO =
                        sykmeldingBekreftEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId)
                    sykmeldingStatusKafkaProducer.send(
                        sykmeldingStatusKafkaEventDTO,
                        source,
                        fnr,
                    )
                    sykmeldingStatusDb.insertStatus(
                        sykmeldingStatusKafkaEventDTO,
                    )
                } else {
                    log.warn(
                        "Kan ikke endre status fra ${sisteStatus.statusEvent} til ${StatusEventDTO.BEKREFTET} for sykmelding med id: $sykmeldingId",
                    )
                    throw InvalidSykmeldingStatusException(
                        "Kan ikke endre status fra ${sisteStatus.statusEvent} til ${StatusEventDTO.BEKREFTET} for sykmelding med id: $sykmeldingId",
                    )
                }
            }
            else -> {
                log.warn(
                    "Forsøk på å bekrefte avvist sykmelding som ikke er avvist. SykmeldingId: $sykmeldingId",
                )
                throw InvalidSykmeldingStatusException(
                    "Kan ikke bekrefte sykmelding med id: $sykmeldingId fordi den ikke er avvist",
                )
            }
        }
    }

    private fun canChangeStatus(
        nyStatusEvent: StatusEventDTO,
        sisteStatus: StatusEventDTO,
        erAvvist: Boolean?,
        erEgenmeldt: Boolean?,
        sykmeldingId: String,
    ): Boolean {
        val allowedStatuses =
            when {
                erAvvist == true -> {
                    statusStatesAvvistSykmelding[sisteStatus]
                }
                erEgenmeldt == true -> {
                    statusStatesEgenmelding[sisteStatus]
                }
                else -> {
                    statusStates[sisteStatus]
                }
            }
        if (allowedStatuses != null && allowedStatuses.contains(nyStatusEvent)) {
            return true
        }
        log.warn(
            "Kan ikke endre status fra $sisteStatus til $nyStatusEvent for sykmeldingID $sykmeldingId",
        )
        throw InvalidSykmeldingStatusException(
            "Kan ikke endre status fra $sisteStatus til $nyStatusEvent for sykmeldingID $sykmeldingId",
        )
    }

    suspend fun hentSisteStatusOgSjekkTilgang(
        sykmeldingId: String,
        fnr: String
    ): SykmeldingStatusEventDTO {
        return sykmeldingStatusDb.getLatestStatus(sykmeldingId, fnr)
    }
}

fun SykmeldingUserEvent.toStatusEvent(): StatusEventDTO {
    if (arbeidssituasjon.svar == ArbeidssituasjonDTO.ARBEIDSTAKER) {
        return StatusEventDTO.SENDT
    }
    return StatusEventDTO.BEKREFTET
}

fun SykmeldingsperiodeDTO.range(): ClosedRange<LocalDate> = fom.rangeTo(tom)
