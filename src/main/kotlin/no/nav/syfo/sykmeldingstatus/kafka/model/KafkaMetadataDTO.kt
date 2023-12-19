package no.nav.syfo.sykmeldingstatus.kafka.model

import java.time.OffsetDateTime

data class KafkaMetadataDTO(
    val sykmeldingId: String,
    val timestamp: OffsetDateTime,
    val fnr: String,
    val source: String
)
