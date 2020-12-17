package no.nav.syfo.pdl.service

import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.HentPerson
import no.nav.syfo.pdl.client.model.Navn
import no.nav.syfo.pdl.client.model.ResponseData

fun getPdlResponse(): GetPersonResponse {
    return GetPersonResponse(ResponseData(
        hentPerson = HentPerson(listOf(Navn("fornavn", null, "etternavn")))),
        errors = null)
}
