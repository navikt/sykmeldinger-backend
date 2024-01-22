package no.nav.syfo.sykmeldingstatus.kafka

import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.sykmeldingstatus.api.v1.ArbeidsgiverStatusDTO

data class SykmeldingWithArbeidsgiverStatus(
    val sykmeldingsperioder: List<SykmeldingsperiodeDTO>,
    val arbeidsgiver: ArbeidsgiverStatusDTO?,
    val tidligereArbeidsgiver: TidligereArbeidsgiverDTO?,
    val statusEvent: String,
    val sykmeldingId: String,
)
