package no.nav.syfo.sykmeldingstatus.db

import no.nav.syfo.sykmeldingstatus.api.v1.ArbeidsgiverStatusDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SporsmalOgSvarDTO
import java.time.OffsetDateTime

data class SykmeldingStatusDbModel(
    val timestamp: OffsetDateTime,
    val statusEvent: String,
    val arbeidsgiver: ArbeidsgiverStatusDTO?,
    val sporsmals: List<SporsmalOgSvarDTO>?,
)
