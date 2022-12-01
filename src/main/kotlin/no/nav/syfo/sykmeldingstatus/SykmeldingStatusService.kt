package no.nav.syfo.sykmeldingstatus

import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.log
import no.nav.syfo.metrics.BEKREFTET_AV_BRUKER_COUNTER
import no.nav.syfo.metrics.SENDT_AV_BRUKER_COUNTER
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v2.ArbeidssituasjonDTO
import no.nav.syfo.sykmeldingstatus.api.v2.SykmeldingUserEvent
import no.nav.syfo.sykmeldingstatus.db.SykmeldingStatusDb
import no.nav.syfo.sykmeldingstatus.exception.InvalidSykmeldingStatusException
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.kafka.tilSykmeldingStatusKafkaEventDTO
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SykmeldingStatusService(
    private val sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer,
    private val arbeidsgiverService: ArbeidsgiverService,
    private val sykmeldingStatusDb: SykmeldingStatusDb
) {
    companion object {
        private val statusStates: Map<StatusEventDTO, List<StatusEventDTO>> = mapOf(
            Pair(
                StatusEventDTO.APEN,
                listOf(
                    StatusEventDTO.BEKREFTET,
                    StatusEventDTO.AVBRUTT,
                    StatusEventDTO.SENDT,
                    StatusEventDTO.APEN,
                    StatusEventDTO.UTGATT
                )
            ),
            Pair(
                StatusEventDTO.BEKREFTET,
                listOf(
                    StatusEventDTO.APEN, StatusEventDTO.AVBRUTT,
                    StatusEventDTO
                        .BEKREFTET,
                    StatusEventDTO.SENDT
                )
            ),
            Pair(StatusEventDTO.SENDT, emptyList()),
            Pair(StatusEventDTO.AVBRUTT, listOf(StatusEventDTO.APEN)),
            Pair(StatusEventDTO.UTGATT, listOf(StatusEventDTO.AVBRUTT))
        )
        private val statusStatesAvvistSykmelding: Map<StatusEventDTO, List<StatusEventDTO>> = mapOf(
            Pair(StatusEventDTO.APEN, listOf(StatusEventDTO.BEKREFTET)),
            Pair(StatusEventDTO.BEKREFTET, emptyList())
        )
        private val statusStatesEgenmelding: Map<StatusEventDTO, List<StatusEventDTO>> = mapOf(
            Pair(StatusEventDTO.APEN, listOf(StatusEventDTO.BEKREFTET, StatusEventDTO.AVBRUTT)),
            Pair(StatusEventDTO.BEKREFTET, emptyList()),
            Pair(StatusEventDTO.AVBRUTT, emptyList())
        )
    }

    suspend fun registrerStatus(
        sykmeldingStatusEventDTO: SykmeldingStatusEventDTO,
        sykmeldingId: String,
        source: String,
        fnr: String
    ) {
        val sisteStatus = hentSisteStatusOgSjekkTilgang(sykmeldingId, fnr)
        if (canChangeStatus(
                nyStatusEvent = sykmeldingStatusEventDTO.statusEvent,
                sisteStatus = sisteStatus.statusEvent,
                erAvvist = sisteStatus.erAvvist,
                erEgenmeldt = sisteStatus.erEgenmeldt,
                sykmeldingId = sykmeldingId
            )
        ) {
            val sykmeldingStatusKafkaEventDTO = sykmeldingStatusEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId)
            sykmeldingStatusKafkaProducer.send(
                sykmeldingStatusKafkaEventDTO = sykmeldingStatusKafkaEventDTO,
                source = source,
                fnr = fnr
            )
            sykmeldingStatusDb.insertStatus(sykmeldingStatusKafkaEventDTO)
        }
    }

    suspend fun registrerUserEvent(
        sykmeldingUserEvent: SykmeldingUserEvent,
        sykmeldingId: String,
        fnr: String,
        token: String
    ) {
        val sisteStatus = hentSisteStatusOgSjekkTilgang(sykmeldingId, fnr)
        val nesteStatus = sykmeldingUserEvent.toStatusEvent()
        if (canChangeStatus(
                nyStatusEvent = nesteStatus,
                sisteStatus = sisteStatus.statusEvent,
                erAvvist = sisteStatus.erAvvist,
                erEgenmeldt = sisteStatus.erEgenmeldt,
                sykmeldingId = sykmeldingId
            )
        ) {
            val arbeidsgiver = when (nesteStatus) {
                StatusEventDTO.SENDT -> getArbeidsgiver(
                    fnr,
                    sykmeldingId,
                    sykmeldingUserEvent.arbeidsgiverOrgnummer!!.svar
                )

                else -> null
            }
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)

            val sykmeldingStatusKafkaEventDTO =
                sykmeldingUserEvent.tilSykmeldingStatusKafkaEventDTO(timestamp, sykmeldingId, arbeidsgiver)
            sykmeldingStatusKafkaProducer.send(
                sykmeldingStatusKafkaEventDTO = sykmeldingStatusKafkaEventDTO,
                source = "user",
                fnr = fnr
            )
            sykmeldingStatusDb.insertStatus(sykmeldingStatusKafkaEventDTO)

            when (nesteStatus) {
                StatusEventDTO.SENDT -> SENDT_AV_BRUKER_COUNTER.inc()
                StatusEventDTO.BEKREFTET -> BEKREFTET_AV_BRUKER_COUNTER.inc()
                else -> Unit
            }
        }
    }

    private suspend fun getArbeidsgiver(
        fnr: String,
        sykmeldingId: String,
        orgnummer: String
    ): Arbeidsgiverinfo {
        return arbeidsgiverService.getArbeidsgivere(fnr)
            .find { it.orgnummer == orgnummer }
            ?: throw InvalidSykmeldingStatusException("Kan ikke sende sykmelding $sykmeldingId til orgnummer $orgnummer fordi bruker ikke har arbeidsforhold der")
    }

    suspend fun registrerBekreftetAvvist(sykmeldingId: String, source: String, fnr: String) {
        val sisteStatus = hentSisteStatusOgSjekkTilgang(sykmeldingId, fnr)
        when (sisteStatus.erAvvist) {
            true -> {
                if (canChangeStatus(
                        nyStatusEvent = StatusEventDTO.BEKREFTET,
                        sisteStatus = sisteStatus.statusEvent,
                        erAvvist = true,
                        erEgenmeldt = sisteStatus.erEgenmeldt,
                        sykmeldingId = sykmeldingId
                    )
                ) {
                    val sykmeldingBekreftEventDTO = SykmeldingBekreftEventDTO(
                        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                        sporsmalOgSvarListe = emptyList(),
                    )
                    val sykmeldingStatusKafkaEventDTO =
                        sykmeldingBekreftEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId)
                    sykmeldingStatusKafkaProducer.send(
                        sykmeldingStatusKafkaEventDTO,
                        source,
                        fnr
                    )
                    sykmeldingStatusDb.insertStatus(
                        sykmeldingStatusKafkaEventDTO
                    )
                } else {
                    log.warn("Kan ikke endre status fra ${sisteStatus.statusEvent} til ${StatusEventDTO.BEKREFTET} for sykmelding med id: $sykmeldingId")
                    throw InvalidSykmeldingStatusException("Kan ikke endre status fra ${sisteStatus.statusEvent} til ${StatusEventDTO.BEKREFTET} for sykmelding med id: $sykmeldingId")
                }
            }

            else -> {
                log.warn("Forsøk på å bekrefte avvist sykmelding som ikke er avvist. SykmeldingId: $sykmeldingId")
                throw InvalidSykmeldingStatusException("Kan ikke bekrefte sykmelding med id: $sykmeldingId fordi den ikke er avvist")
            }
        }
    }

    private fun canChangeStatus(
        nyStatusEvent: StatusEventDTO,
        sisteStatus: StatusEventDTO,
        erAvvist: Boolean?,
        erEgenmeldt: Boolean?,
        sykmeldingId: String
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
        log.warn("Kan ikke endre status fra $sisteStatus til $nyStatusEvent for sykmeldingID $sykmeldingId")
        throw InvalidSykmeldingStatusException("Kan ikke endre status fra $sisteStatus til $nyStatusEvent for sykmeldingID $sykmeldingId")
    }

    suspend fun hentSisteStatusOgSjekkTilgang(sykmeldingId: String, fnr: String): SykmeldingStatusEventDTO {
        return sykmeldingStatusDb.getLatestStatus(sykmeldingId, fnr)
    }
}

fun SykmeldingUserEvent.toStatusEvent(): StatusEventDTO {
    if (arbeidssituasjon.svar == ArbeidssituasjonDTO.ARBEIDSTAKER) {
        return StatusEventDTO.SENDT
    }
    return StatusEventDTO.BEKREFTET
}
