package no.nav.syfo.pdl.service

import no.nav.syfo.pdl.client.model.Adressebeskyttelse
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.Gradering
import no.nav.syfo.pdl.client.model.Ident
import no.nav.syfo.pdl.client.model.IdentResponse
import no.nav.syfo.pdl.client.model.Navn
import no.nav.syfo.pdl.client.model.PersonResponse
import no.nav.syfo.pdl.client.model.ResponseData

fun getPdlResponse(gradering: Gradering = Gradering.UGRADERT): GetPersonResponse {
    return GetPersonResponse(
        ResponseData(
            person = PersonResponse(
                listOf(Navn("fornavn", null, "etternavn")),
                adressebeskyttelse = listOf(
                    Adressebeskyttelse(gradering)
                )
            ),
            identer = IdentResponse(listOf(Ident("aktorId", AKTORID_GRUPPE)))
        ),
        errors = null
    )
}
