package no.nav.syfo.sykmelding

import no.nav.syfo.sykmelding.api.SykmeldingDTO

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
