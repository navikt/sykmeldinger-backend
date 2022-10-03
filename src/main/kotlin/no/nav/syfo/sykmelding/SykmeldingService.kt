package no.nav.syfo.sykmelding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import no.nav.syfo.sykmeldingstatus.db.SykmeldingStatusDb
import no.nav.syfo.sykmeldingstatus.db.SykmeldingStatusDbModel
import java.util.UUID

class SykmeldingService(
    private val pdlPersonService: PdlPersonService,
    private val smregisterSykmeldingClient: SyfosmregisterSykmeldingClient,
    private val sykmeldingStatusDb: SykmeldingStatusDb,
) {
    suspend fun hentSykmelding(fnr: String, token: String, sykmeldingid: String): SykmeldingDTO? = withContext(Dispatchers.IO) {
        val callId = UUID.randomUUID().toString()
        smregisterSykmeldingClient.getSykmeldingTokenX(subjectToken = token, sykmeldingid = sykmeldingid)
            ?.run { getSykmeldingWithLatestStatus(this) }
            ?.toSykmeldingDTO(fnr, pdlPersonService.getPerson(fnr, token, callId))
    }

    suspend fun hentSykmeldinger(fnr: String, token: String, apiFilter: ApiFilter?): List<SykmeldingDTO> {
        val callId = UUID.randomUUID().toString()
        val person = pdlPersonService.getPerson(fnr, token, callId)

        return smregisterSykmeldingClient.getSykmeldingerTokenX(subjectToken = token, apiFilter = apiFilter)
            .map { getSykmeldingWithLatestStatus(it) }
            .map { it.toSykmeldingDTO(fnr, person) }
            .sortedBy { it.id }
    }

    private suspend fun getSykmeldingWithLatestStatus(sykmelding: Sykmelding): Sykmelding {
        val sykmeldingStatus = sykmeldingStatusDb.getLatesSykmeldingStatus(sykmelding.id)
        return when {
            sykmeldingStatus != null && sykmeldingStatus.timestamp.isAfter(sykmelding.sykmeldingStatus.timestamp) ->
                sykmelding.copy(sykmeldingStatus = sykmeldingStatus.toSykmeldingStatusDto())
            else -> sykmelding
        }
    }
}

private fun SykmeldingStatusDbModel.toSykmeldingStatusDto(): SykmeldingStatusDTO {
    return SykmeldingStatusDTO(
        timestamp = timestamp,
        statusEvent = statusEvent,
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
