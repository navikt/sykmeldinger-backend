package no.nav.syfo.sykmelding

import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.api.ApiFilter
import no.nav.syfo.sykmelding.client.SyfosmregisterSykmeldingClient
import no.nav.syfo.sykmelding.model.ShortNameDTO
import no.nav.syfo.sykmelding.model.SporsmalDTO
import no.nav.syfo.sykmelding.model.SvarDTO
import no.nav.syfo.sykmelding.model.SvartypeDTO
import no.nav.syfo.sykmelding.model.Sykmelding
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.model.SykmeldingStatusDTO
import no.nav.syfo.sykmelding.model.toSykmeldingDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SporsmalOgSvarDTO
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisModel
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisService
import java.util.UUID

class SykmeldingService(
    private val syfosmregisterSykmeldingClient: SyfosmregisterSykmeldingClient,
    private val sykmeldingStatusRedisService: SykmeldingStatusRedisService,
    private val pdlPersonService: PdlPersonService
) {
    suspend fun hentSykmelding(fnr: String, token: String, sykmeldingid: String, erTokenX: Boolean = false): SykmeldingDTO? {
        val callId = UUID.randomUUID().toString()
        return if (erTokenX) {
            syfosmregisterSykmeldingClient.getSykmeldingTokenX(subjectToken = token, sykmeldingid = sykmeldingid)
                ?.run(this::getSykmeldingWithLatestStatus)
                ?.toSykmeldingDTO(fnr, pdlPersonService.getPerson(fnr, token, callId, erTokenX = erTokenX))
        } else {
            syfosmregisterSykmeldingClient.getSykmelding(token = token, sykmeldingid = sykmeldingid)
                ?.run(this::getSykmeldingWithLatestStatus)
                ?.toSykmeldingDTO(fnr, pdlPersonService.getPerson(fnr, token, callId))
        }
    }

    suspend fun hentSykmeldinger(fnr: String, token: String, apiFilter: ApiFilter?, erTokenX: Boolean = false): List<SykmeldingDTO> {
        val callId = UUID.randomUUID().toString()
        return if (erTokenX) {
            syfosmregisterSykmeldingClient.getSykmeldingerTokenX(subjectToken = token, apiFilter = apiFilter)
                .map(this::getSykmeldingWithLatestStatus)
                .map { it.toSykmeldingDTO(fnr, pdlPersonService.getPerson(fnr, token, callId, erTokenX = erTokenX)) }
        } else {
            syfosmregisterSykmeldingClient.getSykmeldinger(token = token, apiFilter = apiFilter)
                .map(this::getSykmeldingWithLatestStatus)
                .map { it.toSykmeldingDTO(fnr, pdlPersonService.getPerson(fnr, token, callId)) }
        }
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
