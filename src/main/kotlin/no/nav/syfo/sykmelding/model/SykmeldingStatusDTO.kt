package no.nav.syfo.sykmelding.model

import java.time.OffsetDateTime
import no.nav.syfo.sykmeldingstatus.api.v1.ArbeidsgiverStatusDTO
import no.nav.syfo.sykmeldingstatus.api.v2.SykmeldingFormResponse

data class SykmeldingStatusDTO(
    val statusEvent: String,
    val timestamp: OffsetDateTime,
    val arbeidsgiver: ArbeidsgiverStatusDTO?,
    val sporsmalOgSvarListe: List<SporsmalDTO>,
    // TODO: This is nullable because older sykmeldinger are not migrated to the new format
    val brukerSvar: SykmeldingFormResponse?,
    val tidligereArbeidsgiver: TidligereArbeidsgiverDTO? = null
)
