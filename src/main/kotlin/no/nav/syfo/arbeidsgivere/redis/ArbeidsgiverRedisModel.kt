package no.nav.syfo.arbeidsgivere.redis

import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.model.NarmesteLeder
import java.time.LocalDate

data class ArbeidsgiverinfoRedisModel(
    val orgnummer: String,
    val juridiskOrgnummer: String,
    val navn: String,
    val stilling: String,
    val aktivtArbeidsforhold: Boolean,
    val naermesteLeder: NarmesteLederRedisModel?
)

data class NarmesteLederRedisModel(
    val id: Long,
    val aktoerId: String,
    val navn: String,
    val epost: String?,
    val mobil: String?,
    val orgnummer: String,
    val organisasjonsnavn: String,
    val aktivTom: LocalDate?,
    val arbeidsgiverForskuttererLoenn: Boolean?
)

fun Arbeidsgiverinfo.toArbeidsgiverinfoRedisModel(): ArbeidsgiverinfoRedisModel {
    return ArbeidsgiverinfoRedisModel(
        orgnummer = orgnummer,
        juridiskOrgnummer = juridiskOrgnummer,
        navn = navn,
        stilling = stilling,
        aktivtArbeidsforhold = aktivtArbeidsforhold,
        naermesteLeder = naermesteLeder?.toNarmesteLederRedisModel()
    )
}

fun NarmesteLeder.toNarmesteLederRedisModel(): NarmesteLederRedisModel {
    return NarmesteLederRedisModel(
        id = id,
        aktoerId = aktoerId,
        navn = navn,
        epost = epost,
        mobil = mobil,
        orgnummer = orgnummer,
        organisasjonsnavn = organisasjonsnavn,
        aktivTom = aktivTom,
        arbeidsgiverForskuttererLoenn = arbeidsgiverForskuttererLoenn
    )
}

fun ArbeidsgiverinfoRedisModel.toArbeidsgiverinfo(): Arbeidsgiverinfo {
    return Arbeidsgiverinfo(
        orgnummer = orgnummer,
        juridiskOrgnummer = juridiskOrgnummer,
        navn = navn,
        stilling = stilling,
        aktivtArbeidsforhold = aktivtArbeidsforhold,
        naermesteLeder = naermesteLeder?.toNarmesteLeder()
    )
}

fun NarmesteLederRedisModel.toNarmesteLeder(): NarmesteLeder {
    return NarmesteLeder(
        id = id,
        aktoerId = aktoerId,
        navn = navn,
        epost = epost,
        mobil = mobil,
        orgnummer = orgnummer,
        organisasjonsnavn = organisasjonsnavn,
        aktivTom = aktivTom,
        arbeidsgiverForskuttererLoenn = arbeidsgiverForskuttererLoenn
    )
}
