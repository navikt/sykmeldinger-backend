package no.nav.syfo.sykmelding

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nav.syfo.log
import no.nav.syfo.metrics.MISSING_DATA_COUNTER
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmelding.model.SykmeldingDTO

class SykmeldingService(
    private val sykmeldingDb: SykmeldingDb,
) {

    suspend fun hentSykmelding(fnr: String, sykmeldingid: String): SykmeldingDTO? = withContext(Dispatchers.IO) {
        sykmeldingDb.getSykmelding(sykmeldingId = sykmeldingid, fnr = fnr)
    }

    suspend fun hentSykmeldinger(fnr: String): List<SykmeldingDTO> {
        return sykmeldingDb.getSykmeldinger(fnr)
    }

    fun logInfo(sykmeldingId: String, fnr: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val sykmeldingExists: Boolean = sykmeldingDb.sykmeldingExists(sykmeldingId)
            if (!sykmeldingExists) {
                MISSING_DATA_COUNTER.labels("sykmelding").inc()
            }
            val behandlingsutfallExsists: Boolean = sykmeldingDb.behandlingsutfallExists(sykmeldingId)
            if (!behandlingsutfallExsists) {
                MISSING_DATA_COUNTER.labels("behandlingsutfall").inc()
            }
            val sykmeldingStatusExists: Boolean = sykmeldingDb.sykmeldingStatusExists(sykmeldingId)
            if (!sykmeldingStatusExists) {
                MISSING_DATA_COUNTER.labels("status").inc()
            }
            val sykmeldtExists: Boolean = sykmeldingDb.sykmeldtExists(fnr)
            if (!sykmeldtExists) {
                MISSING_DATA_COUNTER.labels("sykmeldt").inc()
            }

            val allDataExists: Boolean = sykmeldingDb.getSykmelding(sykmeldingId, fnr) != null
            log.info("404 and Sykmelding: $sykmeldingExists, behandligsutfall: $behandlingsutfallExsists, status: $sykmeldingStatusExists, sykmeldt: $sykmeldtExists, allData: $allDataExists, sykmeldingId: $sykmeldingId")
        }
    }
}
