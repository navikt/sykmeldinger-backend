package no.nav.syfo.sykmeldingstatus

import java.time.OffsetDateTime
import java.time.ZoneOffset
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.metrics.BEKREFTET_AV_BRUKER_COUNTER
import no.nav.syfo.metrics.SENDT_AV_BRUKER_COUNTER
import no.nav.syfo.sykmelding.model.TidligereArbeidsgiver
import no.nav.syfo.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v2.Arbeidssituasjon
import no.nav.syfo.sykmeldingstatus.api.v2.Arbeidssituasjon.ARBEIDSLEDIG
import no.nav.syfo.sykmeldingstatus.api.v2.Arbeidssituasjon.FISKER
import no.nav.syfo.sykmeldingstatus.api.v2.LottOgHyre
import no.nav.syfo.sykmeldingstatus.api.v2.SykmeldingFormResponse
import no.nav.syfo.sykmeldingstatus.db.SykmeldingStatusDb
import no.nav.syfo.sykmeldingstatus.exception.InvalidSykmeldingStatusException
import no.nav.syfo.sykmeldingstatus.kafka.SykmeldingWithArbeidsgiverStatus
import no.nav.syfo.sykmeldingstatus.kafka.model.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.kafka.tilStatusEventDTO
import no.nav.syfo.sykmeldingstatus.kafka.tilSykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmeldingstatus.tidligereArbeidsgiver.TidligereArbeidsgiverService
import no.nav.syfo.utils.applog
import no.nav.syfo.utils.teamLogger

class SykmeldingStatusService(
    private val sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer,
    private val arbeidsgiverService: ArbeidsgiverService,
    private val sykmeldingStatusDb: SykmeldingStatusDb,
) {
    private val logger = applog()
    private val teamlog = teamLogger()

    private val tidligereArbeidsgiverService = TidligereArbeidsgiverService()

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
    ): List<TidligereArbeidsgiver> {
        val sykmeldingerWithStatus = sykmeldingStatusDb.getSykmeldingWithStatus(fnr)
        val sykmeldingWithStatus = sykmeldingerWithStatus.find { it.sykmeldingId == sykmeldingId }
        if (sykmeldingWithStatus == null) return emptyList()
        val muligeTidligereArbeidsgivere =
            tidligereArbeidsgiverService.filterRelevantSykmeldinger(
                sykmeldingerWithStatus,
                sykmeldingWithStatus
            )
        if (muligeTidligereArbeidsgivere.isNotEmpty()) {
            return muligeTidligereArbeidsgivere.mapNotNull {
                transformToTidligereArbeidsgiver(it.first)
            }
        }
        return emptyList()
    }

    private fun transformToTidligereArbeidsgiver(
        data: SykmeldingWithArbeidsgiverStatus
    ): TidligereArbeidsgiver? {
        val arbeidsgiver = data.arbeidsgiver
        val tidligereArbeidsgiver = data.tidligereArbeidsgiver
        if (arbeidsgiver != null) {
            return TidligereArbeidsgiver(
                orgNavn = arbeidsgiver.orgNavn,
                orgnummer = arbeidsgiver.orgnummer,
            )
        }
        if (tidligereArbeidsgiver != null) {
            return TidligereArbeidsgiver(
                tidligereArbeidsgiver.orgNavn,
                tidligereArbeidsgiver.orgnummer
            )
        }
        return null
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
            teamlog.info(
                "Opprettet sendt status for {} {} {}",
                kv("fødselsnummer", fnr),
                kv("sykmeldingId", sykmeldingId),
                kv("nesteStatus", nesteStatus)
            )
            SENDT_AV_BRUKER_COUNTER.inc()
            return
        }
        createBekreftetStatus(fnr, sykmeldingId, sykmeldingFormResponse)
        teamlog.info(
            "Opprettet bekreftet status for {} {} {}",
            kv("fødselsnummer", fnr),
            kv("sykmeldingId", sykmeldingId),
            kv("nesteStatus", nesteStatus)
        )
        BEKREFTET_AV_BRUKER_COUNTER.inc()
    }

    suspend fun createBekreftetStatus(
        fnr: String,
        sykmeldingId: String,
        sykmeldingFormResponse: SykmeldingFormResponse,
    ) {
        val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
        val tidligereArbeidsgiver =
            getTidligereArbeidsgiverIfExists(sykmeldingId, fnr, sykmeldingFormResponse)
        val sykmeldingStatusKafkaEventDTO =
            sykmeldingFormResponse.tilSykmeldingStatusKafkaEventDTO(
                timestamp = timestamp,
                sykmeldingId = sykmeldingId,
                arbeidsgiver = null,
                tidligereArbeidsgiver = tidligereArbeidsgiver,
            )
        updateStatus(sykmeldingStatusKafkaEventDTO, fnr, sykmeldingFormResponse)
    }

    private suspend fun getTidligereArbeidsgiverIfExists(
        sykmeldingId: String,
        fnr: String,
        sykmeldingFormResponse: SykmeldingFormResponse
    ): TidligereArbeidsgiverDTO? {
        if (sykmeldingFormResponse.arbeidssituasjon.svar != ARBEIDSLEDIG) return null
        val arbeidsledigOrgnummer =
            sykmeldingFormResponse.arbeidsledig?.arbeidsledigFraOrgnummer?.svar
        val arbeidsledigOrgNavn =
            sykmeldingStatusDb.getArbeidsledigOrgNavnFromOrgnummer(fnr, arbeidsledigOrgnummer)
        if (arbeidsledigOrgNavn == null || arbeidsledigOrgnummer == null) return null
        val tidligereArbeidsgiver =
            TidligereArbeidsgiverDTO(
                orgnummer = arbeidsledigOrgnummer,
                orgNavn = arbeidsledigOrgNavn,
                sykmeldingsId = sykmeldingId
            )
        teamlog.info(
            "Setter tidligere arbeidsgiver for {} {} {} {}",
            kv("fødselsnummer", fnr),
            kv("sykmeldingId", sykmeldingId),
            kv("tidligereAgOrgnummer", tidligereArbeidsgiver.orgnummer),
            kv("tidligereAgNavn", tidligereArbeidsgiver.orgNavn)
        )
        return tidligereArbeidsgiver
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
