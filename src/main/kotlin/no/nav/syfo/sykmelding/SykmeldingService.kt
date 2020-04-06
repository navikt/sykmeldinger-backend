package no.nav.syfo.sykmelding

import no.nav.syfo.sykmelding.api.ApiFilter
import no.nav.syfo.sykmelding.client.SyfosmregisterSykmeldingClient
import no.nav.syfo.sykmelding.model.ShortNameDTO
import no.nav.syfo.sykmelding.model.SporsmalDTO
import no.nav.syfo.sykmelding.model.SvarDTO
import no.nav.syfo.sykmelding.model.SvartypeDTO
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.model.SykmeldingStatusDTO
import no.nav.syfo.sykmeldingstatus.api.SporsmalOgSvarDTO
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisModel
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisService

class SykmeldingService(
    private val syfosmregisterSykmeldingClient: SyfosmregisterSykmeldingClient,
    private val sykmeldingStatusRedisService: SykmeldingStatusRedisService
) {
    suspend fun hentSykmeldinger(token: String, apiFilter: ApiFilter?): List<SykmeldingDTO> {
        return syfosmregisterSykmeldingClient.getSykmeldinger(token = token, apiFilter = apiFilter)
                .map(this::getSykmeldingWithLatestStatus)
    }

    private fun getSykmeldingWithLatestStatus(sykmelding: SykmeldingDTO): SykmeldingDTO {
        val redisStatus = sykmeldingStatusRedisService.getStatus(sykmelding.id)
        return when {
            redisStatus != null && redisStatus.timestamp.isAfter(sykmelding.sykmeldingStatus.timestamp) ->
                sykmelding.copy(sykmeldingStatus = redisStatus.toSykmeldingStatusDto())
            else -> sykmelding
        }
    }
}

private fun SykmeldingStatusRedisModel.toSykmeldingStatusDto(): SykmeldingStatusDTO {
    return SykmeldingStatusDTO(timestamp = timestamp,
            statusEvent = statusEvent.name,
            arbeidsgiver = arbeidsgiver,
            sporsmalOgSvarListe = sporsmals?.map { it.toSporsmalDTO() } ?: emptyList())
}

private fun SporsmalOgSvarDTO.toSporsmalDTO(): SporsmalDTO {
    return SporsmalDTO(tekst = tekst, shortName = shortName.tilShortNameDTO(), svar = SvarDTO(svarType = svartype.tilSvartypeDTO(), svar = svar))
}

private fun no.nav.syfo.sykmeldingstatus.api.ShortNameDTO.tilShortNameDTO(): ShortNameDTO {
    return when (this) {
        no.nav.syfo.sykmeldingstatus.api.ShortNameDTO.ARBEIDSSITUASJON -> ShortNameDTO.ARBEIDSSITUASJON
        no.nav.syfo.sykmeldingstatus.api.ShortNameDTO.PERIODE -> ShortNameDTO.PERIODE
        no.nav.syfo.sykmeldingstatus.api.ShortNameDTO.FRAVAER -> ShortNameDTO.FRAVAER
        no.nav.syfo.sykmeldingstatus.api.ShortNameDTO.FORSIKRING -> ShortNameDTO.FORSIKRING
        no.nav.syfo.sykmeldingstatus.api.ShortNameDTO.NY_NARMESTE_LEDER -> ShortNameDTO.NY_NARMESTE_LEDER
    }
}

fun no.nav.syfo.sykmeldingstatus.api.SvartypeDTO.tilSvartypeDTO(): SvartypeDTO {
    return when (this) {
        no.nav.syfo.sykmeldingstatus.api.SvartypeDTO.ARBEIDSSITUASJON -> SvartypeDTO.ARBEIDSSITUASJON
        no.nav.syfo.sykmeldingstatus.api.SvartypeDTO.PERIODER -> SvartypeDTO.PERIODER
        no.nav.syfo.sykmeldingstatus.api.SvartypeDTO.JA_NEI -> SvartypeDTO.JA_NEI
    }
}
