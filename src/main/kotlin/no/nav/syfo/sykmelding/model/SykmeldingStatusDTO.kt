package no.nav.syfo.sykmelding.model

import java.time.OffsetDateTime
import no.nav.syfo.model.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.sykmeldingstatus.api.v1.ArbeidsgiverStatusDTO

data class SykmeldingStatusDTO(
    val statusEvent: String,
    val timestamp: OffsetDateTime,
    val arbeidsgiver: ArbeidsgiverStatusDTO?,
    val sporsmalOgSvarListe: List<SporsmalDTO>,
    val tidligereArbeidsgiver: TidligereArbeidsgiverDTO? = null
)
