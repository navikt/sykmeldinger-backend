package no.nav.syfo.sykmelding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmelding.model.SykmeldingDTO

class SykmeldingService(
    private val sykmeldingDb: SykmeldingDb
) {
    suspend fun hentSykmelding(fnr: String, sykmeldingid: String): SykmeldingDTO? = withContext(Dispatchers.IO) {
        sykmeldingDb.getSykmelding(sykmeldingId = sykmeldingid, fnr = fnr)
    }

    suspend fun hentSykmeldinger(fnr: String): List<SykmeldingDTO> {
        return sykmeldingDb.getSykmeldinger(fnr)
    }
}
