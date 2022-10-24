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
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisModel
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisService
import java.util.UUID
import no.nav.syfo.sykmelding.db.SykmeldingDb

class SykmeldingService(
    private val sykmeldingStatusRedisService: SykmeldingStatusRedisService,
    private val pdlPersonService: PdlPersonService,
    private val sykmeldingDb: SykmeldingDb
) {
    suspend fun hentSykmelding(fnr: String, sykmeldingid: String): SykmeldingDTO? = withContext(Dispatchers.IO) {
        sykmeldingDb.getSykmelding(sykmeldingId = sykmeldingid, fnr = fnr)
    }

    suspend fun hentSykmeldinger(fnr: String): List<SykmeldingDTO> {
        return sykmeldingDb.getSykmeldinger(fnr)
    }
}