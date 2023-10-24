package no.nav.syfo.sykmeldingstatus

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.log
import no.nav.syfo.metrics.ANTALL_TIDLIGERE_ARBEIDSGIVERE
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
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService.TidligereArbeidsgiverType.INGEN
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService.TidligereArbeidsgiverType.KANT_TIL_KANT
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService.TidligereArbeidsgiverType.OVERLAPPENDE
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v2.ArbeidssituasjonDTO
import no.nav.syfo.sykmeldingstatus.api.v2.ArbeidssituasjonDTO.ARBEIDSLEDIG
import no.nav.syfo.sykmeldingstatus.api.v2.EndreEgenmeldingsdagerEvent
import no.nav.syfo.sykmeldingstatus.api.v2.SykmeldingFormResponse
import no.nav.syfo.sykmeldingstatus.db.SykmeldingStatusDb
import no.nav.syfo.sykmeldingstatus.exception.InvalidSykmeldingStatusException
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.kafka.tilStatusEventDTO
import no.nav.syfo.sykmeldingstatus.kafka.tilSykmeldingStatusKafkaEventDTO

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

    suspend fun createAvbruttStatus(
        sykmeldingId: String,
        source: String,
        fnr: String,
    ) =
        createGjenapneOrAvbruttStatus(
            StatusEventDTO.AVBRUTT,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            sykmeldingId,
            source,
            fnr,
        )

    suspend fun createGjenapneStatus(
        sykmeldingId: String,
        source: String,
        fnr: String,
    ) =
        createGjenapneOrAvbruttStatus(
            StatusEventDTO.APEN,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            sykmeldingId,
            source,
            fnr,
        )

    private suspend fun createGjenapneOrAvbruttStatus(
        statusEvent: StatusEventDTO,
        timestamp: OffsetDateTime,
        sykmeldingId: String,
        source: String,
        fnr: String,
    ) {
        require(statusEvent == StatusEventDTO.APEN || statusEvent == StatusEventDTO.AVBRUTT) {
            "createGjenapneOrAvbruttStatus kan ikke endre status til $statusEvent"
        }

        val sisteStatus = hentSisteStatusOgSjekkTilgang(sykmeldingId, fnr)
        requireCanChangeStatus(
            nyStatusEvent = statusEvent,
            sisteStatus = sisteStatus.statusEvent,
            erAvvist = sisteStatus.erAvvist,
            erEgenmeldt = sisteStatus.erEgenmeldt,
            sykmeldingId = sykmeldingId,
        )

        val sykmeldingStatusKafkaEventDTO =
            SykmeldingStatusKafkaEventDTO(
                sykmeldingId,
                timestamp,
                statusEvent.tilStatusEventDTO(),
                null,
                null,
            )
        sykmeldingStatusKafkaProducer.send(
            sykmeldingStatusKafkaEventDTO = sykmeldingStatusKafkaEventDTO,
            source = source,
            fnr = fnr,
        )
        sykmeldingStatusDb.insertStatus(sykmeldingStatusKafkaEventDTO)
    }

    suspend fun createSendtStatus(
        sykmeldingFormResponse: SykmeldingFormResponse,
        sykmeldingId: String,
        fnr: String,
    ) {
        val sisteStatus = hentSisteStatusOgSjekkTilgang(sykmeldingId, fnr)
        val nesteStatus = sykmeldingFormResponse.toStatusEvent()
        requireCanChangeStatus(
            nyStatusEvent = nesteStatus,
            sisteStatus = sisteStatus.statusEvent,
            erAvvist = sisteStatus.erAvvist,
            erEgenmeldt = sisteStatus.erEgenmeldt,
            sykmeldingId = sykmeldingId,
        )

        val arbeidsgiver =
            when (nesteStatus) {
                StatusEventDTO.SENDT ->
                    getArbeidsgiver(
                        fnr,
                        sykmeldingId,
                        sykmeldingFormResponse.arbeidsgiverOrgnummer!!.svar,
                    )
                else -> null
            }
        val timestamp = OffsetDateTime.now(ZoneOffset.UTC)

        val tidligereArbeidsgiver =
            when (sykmeldingFormResponse.arbeidssituasjon.svar) {
                ARBEIDSLEDIG -> tidligereArbeidsgiver(fnr, sykmeldingId, nesteStatus)
                else -> null
            }

        val sykmeldingStatusKafkaEventDTO =
            sykmeldingFormResponse.tilSykmeldingStatusKafkaEventDTO(
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
        (fom.isAfter(tidligereSmFom.minusDays(1)) &&
            fom.isBefore(
                tidligereSmTom.plusDays(1),
            ))

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
                .map {
                    val tidligereArbeidsgiverType =
                        tidligereArbeidsgiverType(
                            currentSykmeldingFirstFomDate,
                            it.sykmeldingsperioder,
                        )
                    it to tidligereArbeidsgiverType
                }
                .filter { it.second != INGEN }

        val antallTidligereArbeidsgivere =
            sykmeldinger.distinctBy { it.first.sykmeldingStatus.arbeidsgiver?.orgnummer }.size
        ANTALL_TIDLIGERE_ARBEIDSGIVERE.labels(antallTidligereArbeidsgivere.toString()).inc()
        if (antallTidligereArbeidsgivere != 1) {
            return null
        }

        val sisteStatus = sykmeldinger.first()
        TIDLIGERE_ARBEIDSGIVER_COUNTER.labels(sisteStatus.second.name).inc()
        return sisteStatus.first
    }

    private fun tidligereArbeidsgiverType(
        currentSykmeldingFirstFomDate: LocalDate,
        sykmeldingsperioder: List<SykmeldingsperiodeDTO>
    ): TidligereArbeidsgiverType {
        val kantTilKant = sisteTomIKantMedDag(sykmeldingsperioder, currentSykmeldingFirstFomDate)
        if (kantTilKant) return KANT_TIL_KANT
        val overlappende =
            isOverlappende(
                tidligereSmTom = sykmeldingsperioder.maxOf { it.tom },
                tidligereSmFom = sykmeldingsperioder.minOf { it.fom },
                fom = currentSykmeldingFirstFomDate,
            )
        if (overlappende) return OVERLAPPENDE
        return INGEN
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

    suspend fun createBekreftetAvvistStatus(sykmeldingId: String, source: String, fnr: String) {
        val sisteStatus = hentSisteStatusOgSjekkTilgang(sykmeldingId, fnr)
        when (sisteStatus.erAvvist) {
            true -> {
                requireCanChangeStatus(
                    nyStatusEvent = StatusEventDTO.BEKREFTET,
                    sisteStatus = sisteStatus.statusEvent,
                    erAvvist = true,
                    erEgenmeldt = sisteStatus.erEgenmeldt,
                    sykmeldingId = sykmeldingId,
                )

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
                sykmeldingStatusDb.insertStatus(sykmeldingStatusKafkaEventDTO)
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

    private fun requireCanChangeStatus(
        nyStatusEvent: StatusEventDTO,
        sisteStatus: StatusEventDTO,
        erAvvist: Boolean?,
        erEgenmeldt: Boolean?,
        sykmeldingId: String,
    ) {
        val allowedStatuses =
            when {
                erAvvist == true -> statusStatesAvvistSykmelding[sisteStatus]
                erEgenmeldt == true -> statusStatesEgenmelding[sisteStatus]
                else -> statusStates[sisteStatus]
            }

        if (allowedStatuses != null && allowedStatuses.contains(nyStatusEvent)) {
            return
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

fun SykmeldingFormResponse.toStatusEvent(): StatusEventDTO {
    if (arbeidssituasjon.svar == ArbeidssituasjonDTO.ARBEIDSTAKER) {
        return StatusEventDTO.SENDT
    }
    return StatusEventDTO.BEKREFTET
}
