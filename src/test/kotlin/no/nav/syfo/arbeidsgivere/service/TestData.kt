package no.nav.syfo.arbeidsgivere.service

import no.nav.syfo.arbeidsgivere.model.Arbeidsforhold
import no.nav.syfo.arbeidsgivere.narmesteleder.db.NarmestelederDbModel
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

fun getArbeidsgiverforhold(
    fom: LocalDate = LocalDate.of(2020, 6, 1),
    tom: LocalDate? = null
): List<Arbeidsforhold> {
    return listOf(
        Arbeidsforhold(
            id = 1,
            fnr = "12345678901",
            orgnummer = "123456789",
            juridiskOrgnummer = "987654321",
            orgNavn = "Navn 1",
            fom = fom,
            tom = tom
        )
    )
}

fun getNarmesteledere(): List<NarmestelederDbModel> {
    return listOf(
        NarmestelederDbModel(
            narmestelederId = UUID.randomUUID().toString(),
            orgnummer = "123456789",
            brukerFnr = "12345678901",
            lederFnr = "01987654321",
            navn = "Leder Ledersen",
            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1)
        ),
        NarmestelederDbModel(
            narmestelederId = UUID.randomUUID().toString(),
            orgnummer = "123456777",
            brukerFnr = "12345678901",
            lederFnr = "01987654321",
            navn = "Annen Ledersen",
            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusYears(2)
        )
    )
}
