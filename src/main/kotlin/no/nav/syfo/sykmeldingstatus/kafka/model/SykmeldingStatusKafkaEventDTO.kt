package no.nav.syfo.sykmeldingstatus.kafka.model

import java.time.OffsetDateTime

data class SykmeldingStatusKafkaEventDTO(
    val sykmeldingId: String,
    val timestamp: OffsetDateTime,
    val statusEvent: String,
    val arbeidsgiver: ArbeidsgiverStatusKafkaDTO? = null,
    val sporsmals: List<SporsmalOgSvarKafkaDTO>? = null,
    val brukerSvar: KomplettInnsendtSkjemaSvar? = null,
    val erSvarOppdatering: Boolean? = null,
    val tidligereArbeidsgiver: TidligereArbeidsgiverKafkaDTO? = null,
)
