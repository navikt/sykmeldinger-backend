package no.nav.syfo.arbeidsgivere.service

import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsavtale
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsforhold
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsgiver
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Gyldighetsperiode
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Opplysningspliktig
import no.nav.syfo.arbeidsgivere.client.narmesteleder.NarmesteLeder
import no.nav.syfo.arbeidsgivere.client.organisasjon.model.Navn
import no.nav.syfo.arbeidsgivere.client.organisasjon.model.Organisasjonsinfo
import no.nav.syfo.arbeidsgivere.redis.ArbeidsgiverinfoRedisModel
import no.nav.syfo.arbeidsgivere.redis.NarmesteLederRedisModel
import no.nav.syfo.pdl.model.PdlPerson
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun getArbeidsgiverforhold(
    gyldighetsperiode: Gyldighetsperiode = Gyldighetsperiode(
        LocalDate.now(),
        LocalDate.now()
    )
): List<Arbeidsforhold> {
    return listOf(
        Arbeidsforhold(
            Arbeidsgiver("Organisasjon", "123456789"),
            Opplysningspliktig("Organisasjon", "987654321"),
            listOf(
                Arbeidsavtale(gyldighetsperiode = gyldighetsperiode, stillingsprosent = 100.0)
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

fun getNarmesteledere(): List<NarmesteLeder> {
    return listOf(
        NarmesteLeder(
            orgnummer = "123456789",
            narmesteLederTelefonnummer = "90909090",
            narmesteLederEpost = "epost@nav.no",
            aktivFom = LocalDate.now().minusYears(1),
            aktivTom = null,
            arbeidsgiverForskutterer = true,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1),
            navn = "Leder Ledersen"
        ),
        NarmesteLeder(
            orgnummer = "123456789",
            narmesteLederTelefonnummer = "99999999",
            narmesteLederEpost = "epost2@nav.no",
            aktivFom = LocalDate.now().minusYears(2),
            aktivTom = LocalDate.now().minusYears(1),
            arbeidsgiverForskutterer = true,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1),
            navn = "Forrige Ledersen"
        ),
        NarmesteLeder(
            orgnummer = "123456777",
            narmesteLederTelefonnummer = "40404040",
            narmesteLederEpost = "epost3@nav.no",
            aktivFom = LocalDate.now().minusYears(2),
            aktivTom = null,
            arbeidsgiverForskutterer = true,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusYears(2),
            navn = "Annen Ledersen"
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
