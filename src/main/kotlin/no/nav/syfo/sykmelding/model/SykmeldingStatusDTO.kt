package no.nav.syfo.sykmelding.model

import no.nav.syfo.sykmeldingstatus.api.v1.ArbeidsgiverStatusDTO
import java.time.OffsetDateTime

data class SykmeldingStatusDTO(
    val statusEvent: String,
    val timestamp: OffsetDateTime,
    val arbeidsgiver: ArbeidsgiverStatusDTO?,
    val sporsmalOgSvarListe: List<SporsmalDTO>
)
