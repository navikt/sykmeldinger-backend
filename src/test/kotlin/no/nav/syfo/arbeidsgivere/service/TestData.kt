package no.nav.syfo.arbeidsgivere.service

import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Ansettelsesperiode
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsavtale
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsforhold
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsgiver
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Gyldighetsperiode
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Opplysningspliktig
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Periode
import no.nav.syfo.arbeidsgivere.client.organisasjon.model.Navn
import no.nav.syfo.arbeidsgivere.client.organisasjon.model.Organisasjonsinfo
import no.nav.syfo.arbeidsgivere.narmesteleder.db.NarmestelederDbModel
import no.nav.syfo.arbeidsgivere.redis.ArbeidsgiverinfoRedisModel
import no.nav.syfo.arbeidsgivere.redis.NarmesteLederRedisModel
import no.nav.syfo.pdl.model.PdlPerson
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

fun getArbeidsgiverforhold(
    ansettelsesperiode: Ansettelsesperiode = Ansettelsesperiode(
        Periode(fom = LocalDate.of(2020, 6, 1), tom = null)
    )
): List<Arbeidsforhold> {
    return listOf(
        Arbeidsforhold(
            Arbeidsgiver("Organisasjon", "123456789"),
            Opplysningspliktig("Organisasjon", "987654321"),
            ansettelsesperiode,
            listOf(
                Arbeidsavtale(gyldighetsperiode = Gyldighetsperiode(LocalDate.now(), null), stillingsprosent = 100.0)
            )
        )
    )
}

fun getOrganisasjonsinfo(): Organisasjonsinfo {
    return Organisasjonsinfo(
        "123456789",
        Navn(
            "Navn 1",
            null,
            null,
            null,
            null,
            null
        )
    )
}

fun getPdlPerson(): PdlPerson {
    return PdlPerson(
        navn = no.nav.syfo.pdl.model.Navn("fornavn", null, "etternavn"),
        aktorId = "aktorId",
        diskresjonskode = false
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

fun getArbeidsgiverInfoRedisModel(): ArbeidsgiverinfoRedisModel {
    return ArbeidsgiverinfoRedisModel(
        orgnummer = "123456789",
        juridiskOrgnummer = "123456789",
        navn = "Navn 1",
        stillingsprosent = "50",
        stilling = "",
        aktivtArbeidsforhold = true,
        naermesteLeder = NarmesteLederRedisModel(
            aktoerId = "aktorId",
            navn = "Leder Ledersen",
            epost = "epost@nav.no",
            mobil = null,
            orgnummer = "123456789",
            organisasjonsnavn = "OrgNavn 1",
            aktivTom = null,
            arbeidsgiverForskuttererLoenn = true
        )
    )
}
