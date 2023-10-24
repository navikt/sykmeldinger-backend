package no.nav.syfo.sykmeldingstatus.api.v2

class ValidationException(message: String) : Exception(message)

fun SykmeldingFormResponse.validate() {
    if (erOpplysningeneRiktige.svar == JaEllerNei.NEI) {
        requireNotNull(
            uriktigeOpplysninger,
            "Uriktige opplysninger må være definer når opplysningene ikke stemmer."
        )
    }
    if (arbeidssituasjon.svar == ArbeidssituasjonDTO.ARBEIDSTAKER) {
        requireNotNull(
            arbeidsgiverOrgnummer,
            "Arbeidsgiver må være valgt når arbeidssituasjon er arbeidstaker"
        )
    } else {
        require(
            arbeidsgiverOrgnummer == null,
            "Arbeidsgiver kan ikke være valgt når arbeidssituasjon ikke er arbeidstaker"
        )
        require(
            riktigNarmesteLeder == null,
            "Spørsmål om ny nærmeste leder kan ikke være besvart når arbeidssituasjon ikke er arbeidstaker"
        )
    }
    if (
        arbeidssituasjon.svar == ArbeidssituasjonDTO.FRILANSER ||
            arbeidssituasjon.svar == ArbeidssituasjonDTO.NAERINGSDRIVENDE
    ) {
        if (harBruktEgenmelding != null) {
            requireNotNull(
                harForsikring,
                "Spørsmål om forsikring må være besvart hvis spørsmål om egenmelding er besvart"
            )
            if (harBruktEgenmelding.svar == JaEllerNei.JA) {
                requireNotNull(
                    egenmeldingsperioder,
                    "Egenmeldingsperioder må være definert hvis egenmelding er brukt"
                )
            }
        }
        if (harForsikring != null) {
            requireNotNull(
                harBruktEgenmelding,
                "Spørsmål om egenmelding må være besvart hvis spørsmål om forsikring er besvart"
            )
        }
    } else {
        require(
            harBruktEgenmelding == null,
            "Spørsmål om egenmelding kan ikke være besvart hvis arbeidssituasjon ikke er frilanser eller selvstendig næringsdrivende"
        )
        require(
            egenmeldingsperioder == null,
            "Egenmeldingsperioder må være null hvis arbeidssituasjon ikke er frilanser eller selvstendig næringsdrivende"
        )
        require(
            harForsikring == null,
            "Spørsmål om forsikring kan ikke være besvart hvis arbeidssituasjon ikke er frilanser eller selvstendig næringsdrivende"
        )
    }
    if (harBruktEgenmeldingsdager?.svar == JaEllerNei.JA) {
        require(
            egenmeldingsdager?.svar != null && egenmeldingsdager.svar.isNotEmpty(),
            "Spørsmål om egenmeldimngsdager må minst ha 1 dag, når harBruktEgenmeldingsdager er JA"
        )
    }
}

fun requireNotNull(obj: Any?, message: String) {
    if (obj == null) {
        throw ValidationException(message)
    }
}

fun require(predicate: Boolean, message: String) {
    if (!predicate) {
        throw ValidationException(message)
    }
}
