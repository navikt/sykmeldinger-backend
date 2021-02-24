package no.nav.syfo.arbeidsgivere.service

import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsavtale
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsforhold
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsgiver
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Gyldighetsperiode
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Opplysningspliktig
import no.nav.syfo.arbeidsgivere.client.organisasjon.model.Navn
import no.nav.syfo.arbeidsgivere.client.organisasjon.model.Organisasjonsinfo
import java.time.LocalDate

fun getArbeidsgiverforhold(gyldighetsperiode: Gyldighetsperiode = Gyldighetsperiode(LocalDate.now(), LocalDate.now())): List<Arbeidsforhold> {
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
