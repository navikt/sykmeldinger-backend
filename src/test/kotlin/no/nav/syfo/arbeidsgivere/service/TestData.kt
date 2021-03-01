package no.nav.syfo.arbeidsgivere.service

import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsavtale
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsforhold
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsgiver
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Gyldighetsperiode
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Opplysningspliktig
import no.nav.syfo.arbeidsgivere.client.narmesteleder.NarmesteLederRelasjon
import no.nav.syfo.arbeidsgivere.client.organisasjon.model.Navn
import no.nav.syfo.arbeidsgivere.client.organisasjon.model.Organisasjonsinfo
import java.time.LocalDate

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

fun getNarmestelederRelasjoner(): List<NarmesteLederRelasjon> {
    return listOf(
        NarmesteLederRelasjon(
            aktorId = "aktorId",
            orgnummer = "123456789",
            narmesteLederAktorId = "nlAktorId",
            narmesteLederTelefonnummer = null,
            narmesteLederEpost = "epost@nav.no",
            aktivFom = LocalDate.now().minusYears(1),
            aktivTom = null,
            arbeidsgiverForskutterer = true,
            skrivetilgang = false,
            tilganger = emptyList(),
            navn = "Leder Ledersen"
        ),
        NarmesteLederRelasjon(
            aktorId = "aktorId",
            orgnummer = "123456789",
            narmesteLederAktorId = "nlAktorId2",
            narmesteLederTelefonnummer = null,
            narmesteLederEpost = "epost2@nav.no",
            aktivFom = LocalDate.now().minusYears(2),
            aktivTom = LocalDate.now().minusYears(1),
            arbeidsgiverForskutterer = true,
            skrivetilgang = false,
            tilganger = emptyList(),
            navn = "Forrige Ledersen"
        ),
        NarmesteLederRelasjon(
            aktorId = "aktorId",
            orgnummer = "123456777",
            narmesteLederAktorId = "nlAktorId3",
            narmesteLederTelefonnummer = null,
            narmesteLederEpost = "epost3@nav.no",
            aktivFom = LocalDate.now().minusYears(2),
            aktivTom = null,
            arbeidsgiverForskutterer = true,
            skrivetilgang = false,
            tilganger = emptyList(),
            navn = "Annen Ledersen"
        )
    )
}
