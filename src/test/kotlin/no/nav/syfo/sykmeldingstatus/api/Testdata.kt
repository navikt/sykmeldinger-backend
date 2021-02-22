package no.nav.syfo.sykmeldingstatus.api

import java.time.OffsetDateTime
import java.time.ZoneOffset

fun opprettSykmeldingBekreftEventDTO(): SykmeldingBekreftEventDTO =
    SykmeldingBekreftEventDTO(
        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
        sporsmalOgSvarListe = opprettSporsmalOgSvarListe()
    )

fun opprettSykmeldingBekreftEventUserDTO(): SykmeldingBekreftEventUserDTO =
    SykmeldingBekreftEventUserDTO(
        sporsmalOgSvarListe = opprettSporsmalOgSvarListe()
    )

private fun opprettSporsmalOgSvarListe() =
    listOf(
        SporsmalOgSvarDTO("Sykmeldt fra ", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "Frilanser"),
        SporsmalOgSvarDTO("Har forsikring?", ShortNameDTO.FORSIKRING, SvartypeDTO.JA_NEI, "Ja"),
        SporsmalOgSvarDTO("Hatt fravær?", ShortNameDTO.FRAVAER, SvartypeDTO.JA_NEI, "Ja"),
        SporsmalOgSvarDTO("Når hadde du fravær?", ShortNameDTO.PERIODE, SvartypeDTO.PERIODER, "{[{\"fom\": \"2019-8-1\", \"tom\": \"2019-8-15\"}, {\"fom\": \"2019-9-1\", \"tom\": \"2019-9-3\"}]}")
    )
