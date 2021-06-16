package no.nav.syfo.sykmelding

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.log
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.api.ApiFilter
import no.nav.syfo.sykmelding.client.SyfosmregisterSykmeldingClient
import no.nav.syfo.sykmelding.model.RegelStatusDTO
import no.nav.syfo.sykmelding.model.ShortNameDTO
import no.nav.syfo.sykmelding.model.SporsmalDTO
import no.nav.syfo.sykmelding.model.SvarDTO
import no.nav.syfo.sykmelding.model.SvartypeDTO
import no.nav.syfo.sykmelding.model.Sykmelding
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.model.SykmeldingStatusDTO
import no.nav.syfo.sykmelding.model.toSykmeldingDTO
import no.nav.syfo.sykmelding.syforestmodel.SyforestSykmelding
import no.nav.syfo.sykmelding.syforestmodel.pdlPersonTilPasient
import no.nav.syfo.sykmelding.syforestmodel.tilSyforestSykmelding
import no.nav.syfo.sykmeldingstatus.api.v1.SporsmalOgSvarDTO
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisModel
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisService
import java.util.UUID

@KtorExperimentalAPI
class SykmeldingService(
    private val syfosmregisterSykmeldingClient: SyfosmregisterSykmeldingClient,
    private val sykmeldingStatusRedisService: SykmeldingStatusRedisService,
    private val pdlPersonService: PdlPersonService
) {
    suspend fun hentSykmelding(fnr: String, token: String, sykmeldingid: String): SykmeldingDTO? {
        val callId = UUID.randomUUID().toString()
        return syfosmregisterSykmeldingClient.getSykmelding(token = token, sykmeldingid = sykmeldingid)
            ?.run(this::getSykmeldingWithLatestStatus)
            ?.toSykmeldingDTO(fnr, pdlPersonService.getPerson(fnr, token, callId))
    }

    suspend fun hentSykmeldinger(fnr: String, token: String, apiFilter: ApiFilter?): List<SykmeldingDTO> {
        val callId = UUID.randomUUID().toString()
        return syfosmregisterSykmeldingClient.getSykmeldinger(token = token, apiFilter = apiFilter)
            .map(this::getSykmeldingWithLatestStatus)
            .map { it.toSykmeldingDTO(fnr, pdlPersonService.getPerson(fnr, token, callId)) }
    }

    suspend fun hentSykmeldingerSyforestFormat(token: String, fnr: String, arbeidsgivervisning: Boolean, apiFilter: ApiFilter?): List<SyforestSykmelding> {
        val sykmeldingsliste = syfosmregisterSykmeldingClient.getSykmeldinger(token = token, apiFilter = apiFilter)
            .filter { it.behandlingsutfall.status != RegelStatusDTO.INVALID }
            .map(this::getSykmeldingWithLatestStatus)
        if (sykmeldingsliste.isNotEmpty()) {
            val callId = UUID.randomUUID().toString()
            try {
                val pasient = pdlPersonTilPasient(fnr, pdlPersonService.getPerson(fnr = fnr, userToken = token, callId = callId))
                return sykmeldingsliste.map { tilSyforestSykmelding(it, pasient, arbeidsgivervisning) }
            } catch (e: Exception) {
                log.error("Noe gikk galt ved mapping av sykmeldinger med id: ${sykmeldingsliste.first().id}, callid: $callId")
                throw e
            }
        }
        return emptyList()
    }

    private fun getSykmeldingWithLatestStatus(sykmelding: Sykmelding): Sykmelding {
        val redisStatus = sykmeldingStatusRedisService.getStatus(sykmelding.id)
        return when {
            redisStatus != null && redisStatus.timestamp.isAfter(sykmelding.sykmeldingStatus.timestamp) ->
                sykmelding.copy(sykmeldingStatus = redisStatus.toSykmeldingStatusDto())
            else -> sykmelding
        }
    }
}

private fun SykmeldingStatusRedisModel.toSykmeldingStatusDto(): SykmeldingStatusDTO {
    return SykmeldingStatusDTO(
        timestamp = timestamp,
        statusEvent = statusEvent.name,
        arbeidsgiver = arbeidsgiver,
        sporsmalOgSvarListe = sporsmals?.map { it.toSporsmalDTO() } ?: emptyList()
    )
}

private fun SporsmalOgSvarDTO.toSporsmalDTO(): SporsmalDTO {
    return SporsmalDTO(tekst = tekst, shortName = shortName.tilShortNameDTO(), svar = SvarDTO(svarType = svartype.tilSvartypeDTO(), svar = svar))
}

private fun no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.tilShortNameDTO(): ShortNameDTO {
    return when (this) {
        no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.ARBEIDSSITUASJON -> ShortNameDTO.ARBEIDSSITUASJON
        no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.PERIODE -> ShortNameDTO.PERIODE
        no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.FRAVAER -> ShortNameDTO.FRAVAER
        no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.FORSIKRING -> ShortNameDTO.FORSIKRING
        no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.NY_NARMESTE_LEDER -> ShortNameDTO.NY_NARMESTE_LEDER
    }
}

fun no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO.tilSvartypeDTO(): SvartypeDTO {
    return when (this) {
        no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO.ARBEIDSSITUASJON -> SvartypeDTO.ARBEIDSSITUASJON
        no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO.PERIODER -> SvartypeDTO.PERIODER
        no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO.JA_NEI -> SvartypeDTO.JA_NEI
    }
}
