package no.nav.syfo.hentsykmelding

import no.nav.syfo.hentsykmelding.api.SykmeldingDTO

class SykmeldingService() {
    fun hentSykmeldinger(fnr: String): List<SykmeldingDTO> {
        // sjekk på skjerming gjøres i registeret
        // tilgangskontroll?
        return emptyList()
    }

    fun erEier(sykmeldingsid: String, fnr: String): Boolean {
        // må implementeres
        return false
    }
}
