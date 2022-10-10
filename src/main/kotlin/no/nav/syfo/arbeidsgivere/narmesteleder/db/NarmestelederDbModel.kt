package no.nav.syfo.arbeidsgivere.narmesteleder.db

import java.time.OffsetDateTime

data class NarmestelederDbModel(
    val narmestelederId: String,
    val orgnummer: String,
    val brukerFnr: String,
    val lederFnr: String,
    val navn: String,
    val timestamp: OffsetDateTime
)
