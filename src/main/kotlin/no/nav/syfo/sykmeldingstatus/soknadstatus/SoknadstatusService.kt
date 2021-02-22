package no.nav.syfo.sykmeldingstatus.soknadstatus

import no.nav.syfo.sykmeldingstatus.soknadstatus.client.RSSoknadstatus
import no.nav.syfo.sykmeldingstatus.soknadstatus.client.SyfosoknadClient

class SoknadstatusService(private val syfosoknadClient: SyfosoknadClient) {

    suspend fun finnesSendtSoknadForSykmelding(token: String, sykmeldingId: String): Boolean {
        val sykepengesoknader = syfosoknadClient.getSoknader(token = token, sykmeldingId = sykmeldingId)

        return sykepengesoknader.find { it.sykmeldingId == sykmeldingId && it.status == RSSoknadstatus.SENDT } != null
    }
}
