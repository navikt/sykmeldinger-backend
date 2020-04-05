package no.nav.syfo.sykmelding

import no.nav.syfo.sykmelding.api.ApiFilter
import no.nav.syfo.sykmelding.api.SykmeldingDTO
import no.nav.syfo.sykmelding.client.SyfosmregisterSykmeldingClient
import no.nav.syfo.sykmeldingstatus.api.SykmeldingStatusDTO
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
            statusEvent = statusEvent,
            arbeidsgiver = arbeidsgiver,
            sporsmalOgSvarListe = sporsmals)
}
