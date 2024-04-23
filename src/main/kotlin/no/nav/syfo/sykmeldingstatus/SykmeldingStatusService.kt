package no.nav.syfo.sykmeldingstatus

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.metrics.BEKREFTET_AV_BRUKER_COUNTER
import no.nav.syfo.metrics.SENDT_AV_BRUKER_COUNTER
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v2.Arbeidssituasjon
import no.nav.syfo.sykmeldingstatus.api.v2.Arbeidssituasjon.ARBEIDSLEDIG
import no.nav.syfo.sykmeldingstatus.api.v2.Arbeidssituasjon.FISKER
import no.nav.syfo.sykmeldingstatus.api.v2.EndreEgenmeldingsdagerEvent
import no.nav.syfo.sykmeldingstatus.api.v2.LottOgHyre
import no.nav.syfo.sykmeldingstatus.api.v2.SporsmalSvar
import no.nav.syfo.sykmeldingstatus.api.v2.SykmeldingFormResponse
import no.nav.syfo.sykmeldingstatus.db.SykmeldingStatusDb
import no.nav.syfo.sykmeldingstatus.exception.InvalidSykmeldingStatusException
import no.nav.syfo.sykmeldingstatus.kafka.SykmeldingWithArbeidsgiverStatus
import no.nav.syfo.sykmeldingstatus.kafka.model.STATUS_SENDT
import no.nav.syfo.sykmeldingstatus.kafka.model.ShortNameKafkaDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.SporsmalOgSvarKafkaDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.SvartypeKafkaDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.kafka.tilStatusEventDTO
import no.nav.syfo.sykmeldingstatus.kafka.tilSykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmeldingstatus.tidligereArbeidsgiver.TidligereArbeidsgiver
import no.nav.syfo.utils.logger
import no.nav.syfo.utils.objectMapper
import no.nav.syfo.utils.securelog

class SykmeldingStatusService(
    private val sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer,
    private val arbeidsgiverService: ArbeidsgiverService,
    private val sykmeldingStatusDb: SykmeldingStatusDb,
) {
    private val logger = logger()
    private val tidligereArbeidsgiver = TidligereArbeidsgiver(sykmeldingStatusDb)

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
        fnr: String,
    ) =
        createGjenapneOrAvbruttStatus(
            StatusEventDTO.AVBRUTT,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            sykmeldingId,
            fnr,
        )

    suspend fun createGjenapneStatus(
        sykmeldingId: String,
        fnr: String,
    ) =
        createGjenapneOrAvbruttStatus(
            StatusEventDTO.APEN,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            sykmeldingId,
            fnr,
        )

    private suspend fun createGjenapneOrAvbruttStatus(
        statusEvent: StatusEventDTO,
        timestamp: OffsetDateTime,
        sykmeldingId: String,
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

        sykmeldingStatusDb.insertStatus(
            sykmeldingStatusKafkaEventDTO,
            response = null,
            beforeCommit = {
                sykmeldingStatusKafkaProducer.send(
                    sykmeldingStatusKafkaEventDTO = sykmeldingStatusKafkaEventDTO,
                    fnr = fnr,
                )
            },
        )
    }

    suspend fun finnTidligereArbeidsgivere(
        fnr: String,
        sykmeldingId: String
    ): List<TidligereArbeidsgiverDTO>? {
        val sykmeldingerWithStatus = sykmeldingStatusDb.getSykmeldingWithStatus(fnr)
        val sykmeldingWithStatus = sykmeldingerWithStatus.find { it.sykmeldingId == sykmeldingId }
        if (sykmeldingWithStatus != null) {
            val muligeTidligereArbeidsgivere =
                filterRelevantSykmeldinger(sykmeldingerWithStatus, sykmeldingWithStatus)
            securelog.info(
                "finner tidligereAg til frontend {} {} {}",
                kv("sykmeldingId", sykmeldingId),
                kv("fnr", fnr),
                kv("antall", muligeTidligereArbeidsgivere.size)
            )
            if (muligeTidligereArbeidsgivere.isNotEmpty()) {
                return muligeTidligereArbeidsgivere.mapNotNull {
                    val arbeidsgiver = it.first.arbeidsgiver
                    if (arbeidsgiver != null) {
                        TidligereArbeidsgiverDTO(
                            orgNavn = arbeidsgiver.orgNavn,
                            orgnummer = arbeidsgiver.orgnummer,
                            sykmeldingsId = sykmeldingId
                        )
                    } else {
                        it.first.tidligereArbeidsgiver
                    }
                }
            }
        }
        return null
    }

    fun filterRelevantSykmeldinger(
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

    suspend fun createStatus(
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

        if (nesteStatus == StatusEventDTO.SENDT) {
            createSendtStatus(fnr, sykmeldingId, sykmeldingFormResponse)
            securelog.info(
                "Opprettet sendt status for {} {} {}",
                kv("fødselsnummer", fnr),
                kv("sykmeldingId", sykmeldingId),
                kv("nesteStatus", nesteStatus)
            )
            SENDT_AV_BRUKER_COUNTER.inc()
            return
        }
        securelog.info(
            "Prøver å opprette bekreftet status for {} {} {}",
            kv("fødselsnummer", fnr),
            kv("sykmeldingId", sykmeldingId),
            kv("nesteStatus", nesteStatus)
        )
        createBekreftetStatus(fnr, sykmeldingId, sykmeldingFormResponse, nesteStatus)
        securelog.info(
            "Opprettet bekreftet status for {} {} {}",
            kv("fødselsnummer", fnr),
            kv("sykmeldingId", sykmeldingId),
            kv("nesteStatus", nesteStatus)
        )
        BEKREFTET_AV_BRUKER_COUNTER.inc()
    }

    private suspend fun createBekreftetStatus(
        fnr: String,
        sykmeldingId: String,
        sykmeldingFormResponse: SykmeldingFormResponse,
        nesteStatus: StatusEventDTO,
    ) {
        val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
        val tidligereArbeidsgiver =
            if (sykmeldingFormResponse.arbeidssituasjon.svar == ARBEIDSLEDIG) {
                securelog.info(
                    "Prøver å finne tidligere arbeidsgiver for {} {} {} {}",
                    kv("fødselsnummer", fnr),
                    kv("sykmeldingId", sykmeldingId),
                    kv("nesteStatus", nesteStatus),
                    kv("tidligereAgBrukerInput", sykmeldingFormResponse.arbeidsgiverOrgnummer?.svar)
                )
                val arbeidsledig = sykmeldingFormResponse.arbeidsledig
                if (arbeidsledig != null) {
                    TidligereArbeidsgiverDTO(
                        orgnummer = arbeidsledig.arbeidsledigFraOrgnummer!!.svar,
                        orgNavn = arbeidsledig.arbeidsledigOrgnavn!!.svar,
                        sykmeldingsId = sykmeldingId
                    )
                } else null
            } else null
        if (tidligereArbeidsgiver != null) {
            securelog.info(
                "Fant tidligere arbeidsgiver for {} {} {} {} {}",
                kv("fødselsnummer", fnr),
                kv("sykmeldingId", sykmeldingId),
                kv("nesteStatus", nesteStatus),
                kv(
                    "tidligereAgBrukerInput",
                    sykmeldingFormResponse.arbeidsledig?.arbeidsledigFraOrgnummer?.svar
                ),
                kv("tidligere arbeidsgiver", tidligereArbeidsgiver)
            )
        }
        val sykmeldingStatusKafkaEventDTO =
            sykmeldingFormResponse.tilSykmeldingStatusKafkaEventDTO(
                timestamp,
                sykmeldingId,
                null,
                tidligereArbeidsgiver,
            )
        updateStatus(sykmeldingStatusKafkaEventDTO, fnr, sykmeldingFormResponse)
    }

    private suspend fun createSendtStatus(
        fnr: String,
        sykmeldingId: String,
        sykmeldingFormResponse: SykmeldingFormResponse
    ) {
        val arbeidsgiver =
            getArbeidsgiver(fnr, sykmeldingId, sykmeldingFormResponse.arbeidsgiverOrgnummer!!.svar)
        val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
        val sykmeldingStatusKafkaEventDTO =
            sykmeldingFormResponse.tilSykmeldingStatusKafkaEventDTO(
                timestamp,
                sykmeldingId,
                arbeidsgiver,
                null,
            )
        updateStatus(sykmeldingStatusKafkaEventDTO, fnr, sykmeldingFormResponse)
    }

    private suspend fun updateStatus(
        sykmeldingStatusKafkaEventDTO: SykmeldingStatusKafkaEventDTO,
        fnr: String,
        sykmeldingFormResponse: SykmeldingFormResponse
    ) {
        sykmeldingStatusDb.insertStatus(
            sykmeldingStatusKafkaEventDTO,
            sykmeldingFormResponse,
            beforeCommit = {
                sykmeldingStatusKafkaProducer.send(
                    sykmeldingStatusKafkaEventDTO = sykmeldingStatusKafkaEventDTO,
                    fnr = fnr,
                )
            },
        )
    }

    suspend fun endreEgenmeldingsdager(
        sykmeldingId: String,
        egenmeldingsdagerEvent: EndreEgenmeldingsdagerEvent,
        fnr: String
    ) {
        val (sykmeldingStatusKafkaEventDTO, formResponse) =
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

        val sykmeldingFormResponseUpdated =
            formResponse?.copy(
                egenmeldingsdager =
                    SporsmalSvar(
                        egenmeldingsdagerEvent.tekst,
                        egenmeldingsdagerEvent.dager,
                    ),
            )

        sykmeldingStatusDb.insertStatus(
            sykmeldingStatusKafkaEventDTOUpdated,
            sykmeldingFormResponseUpdated,
            beforeCommit = {
                sykmeldingStatusKafkaProducer.send(
                    sykmeldingStatusKafkaEventDTO = sykmeldingStatusKafkaEventDTOUpdated,
                    fnr = fnr,
                )
            },
        )
    }

    private fun updateEgenemeldingsdagerSporsmal(
        sporsmalSvar: List<SporsmalOgSvarKafkaDTO>?,
        egenmeldingsdagerEvent: EndreEgenmeldingsdagerEvent
    ): List<SporsmalOgSvarKafkaDTO> {
        requireNotNull(sporsmalSvar) {
            "Forsøkte å oppdatere egenmeldingsdager, men spørsmål og svar er ikke satt."
        }
        return sporsmalSvar
            .filter { it.shortName != ShortNameKafkaDTO.EGENMELDINGSDAGER }
            .let {
                if (egenmeldingsdagerEvent.dager.isNotEmpty()) {
                    it.plus(
                        listOf(
                            SporsmalOgSvarKafkaDTO(
                                tekst = egenmeldingsdagerEvent.tekst,
                                shortName = ShortNameKafkaDTO.EGENMELDINGSDAGER,
                                svartype = SvartypeKafkaDTO.DAGER,
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
        orgnummer: String?,
    ): Arbeidsgiverinfo? {
        if (orgnummer == null) return null
        return arbeidsgiverService.getArbeidsgivere(fnr).find { it.orgnummer == orgnummer }
            ?: throw InvalidSykmeldingStatusException(
                "Kan ikke sende sykmelding $sykmeldingId til orgnummer $orgnummer fordi bruker ikke har arbeidsforhold der",
            )
    }

    suspend fun createBekreftetAvvistStatus(sykmeldingId: String, fnr: String) {
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

                sykmeldingStatusDb.insertStatus(
                    sykmeldingStatusKafkaEventDTO,
                    response = null,
                    beforeCommit = {
                        sykmeldingStatusKafkaProducer.send(
                            sykmeldingStatusKafkaEventDTO,
                            fnr,
                        )
                    },
                )
            }
            else -> {
                logger.warn(
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

        logger.warn(
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
    if (
        arbeidssituasjon.svar == Arbeidssituasjon.ARBEIDSTAKER ||
            (arbeidssituasjon.svar == FISKER && fisker?.lottOgHyre?.svar == LottOgHyre.HYRE)
    ) {
        return StatusEventDTO.SENDT
    }
    return StatusEventDTO.BEKREFTET
}
