package no.nav.syfo.sykmelding

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nav.syfo.log
import no.nav.syfo.pdl.model.PdlPerson
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
    private val pdlPersonService: PdlPersonService,
    private val smregisterSykmeldingClient: SyfosmregisterSykmeldingClient
) {
    suspend fun hentSykmelding(fnr: String, token: String, sykmeldingid: String): SykmeldingDTO? = withContext(Dispatchers.IO) {
        val callId = UUID.randomUUID().toString()
        syfosmregisterSykmeldingClient.getSykmeldingTokenX(subjectToken = token, sykmeldingid = sykmeldingid)
            ?.run { getSykmeldingWithLatestStatus(this) }
            ?.toSykmeldingDTO(fnr, pdlPersonService.getPerson(fnr, token, callId))
    }

    suspend fun hentSykmeldinger(fnr: String, token: String, apiFilter: ApiFilter?): List<SykmeldingDTO> = withContext(Dispatchers.IO) {
        val callId = UUID.randomUUID().toString()
        val person = pdlPersonService.getPerson(fnr, token, callId)

        val sykmeldingerOnPremAsync = async(Dispatchers.IO) {
            syfosmregisterSykmeldingClient.getSykmeldingerTokenX(subjectToken = token, apiFilter = apiFilter)
                .map { getSykmeldingWithLatestStatus(it) }
                .map { it.toSykmeldingDTO(fnr, person) }
                .sortedBy { it.id }
        }
        val sykmeldingerGCPAsync = async(Dispatchers.IO) {
            try {
                smregisterSykmeldingClient.getSykmeldingerTokenX(subjectToken = token, apiFilter = apiFilter)
                    .map { getSykmeldingWithLatestStatus(it) }
                    .map { it.toSykmeldingDTO(fnr, person) }
                    .sortedBy { it.id }
            } catch (ex: Exception) {
                log.error("Klarte ikke hente sykmeldinger fra GCP", ex)
                emptyList()
            }
        }

        val sykmeldingerOnPrem = sykmeldingerOnPremAsync.await()
        val sykmeldingerGCP = sykmeldingerGCPAsync.await()

        if (sykmeldingerGCP == sykmeldingerOnPrem) {
            log.info("Sykmeldinger fra GCP og OnPrem er like, returnerer bare GCP")
            sykmeldingerGCP
        } else {
            checkMore(sykmeldingerOnPrem, sykmeldingerGCP, person, fnr, token, apiFilter)
            log.info("Sykmeldinger fra GCP og OnPrem er ikke like, returnerer bare ONPREM")
            sykmeldingerOnPrem
        }
    }

    private suspend fun getSykmeldingWithLatestStatus(sykmelding: Sykmelding): Sykmelding {
        val redisStatus = sykmeldingStatusRedisService.getStatus(sykmelding.id)
        return when {
            redisStatus != null && redisStatus.timestamp.isAfter(sykmelding.sykmeldingStatus.timestamp) ->
                sykmelding.copy(sykmeldingStatus = redisStatus.toSykmeldingStatusDto())
            else -> sykmelding
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun checkMore(sykmeldingerOnPrem: List<SykmeldingDTO>, sykmeldingerGCP: List<SykmeldingDTO>, person: PdlPerson, fnr: String, token: String, apiFilter: ApiFilter?) {
        GlobalScope.launch(Dispatchers.IO) {
            checkSykmeldinger(sykmeldingerGCP, sykmeldingerOnPrem)
            delay(10_000)
            log.info("check more")
            val newSykemldingerOnprem = syfosmregisterSykmeldingClient.getSykmeldingerTokenX(subjectToken = token, apiFilter)
                .map { getSykmeldingWithLatestStatus(it) }
                .map { it.toSykmeldingDTO(fnr, person) }
                .sortedBy { it.id }

            val newSykmeldingerGCP = smregisterSykmeldingClient.getSykmeldingerTokenX(subjectToken = token, apiFilter = apiFilter)
                .map { getSykmeldingWithLatestStatus(it) }
                .map { it.toSykmeldingDTO(fnr, person) }
                .sortedBy { it.id }

            if (newSykemldingerOnprem != newSykmeldingerGCP) {
                log.info("GCP resonse is still not equal to OnPrem response")
                checkSykmeldinger(newSykmeldingerGCP, newSykemldingerOnprem)
            } else {
                log.info("GCP resonse is equal to OnPrem response")
            }
        }
    }

    private fun checkSykmeldinger(
        sykmeldingerGCP: List<SykmeldingDTO>,
        sykmeldingerOnPrem: List<SykmeldingDTO>
    ) {
        log.info("GCP size ${sykmeldingerGCP.size} != OnPrem size ${sykmeldingerOnPrem.size}")
        if (sykmeldingerGCP.size == sykmeldingerOnPrem.size) {
            (sykmeldingerGCP.indices).forEach {
                if (sykmeldingerGCP[it] != sykmeldingerOnPrem[it]) {
                    log.info("GCP sykmelding ${sykmeldingerGCP[it].id} != OnPrem sykmelding ${sykmeldingerOnPrem[it].id}")
                }
            }
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
