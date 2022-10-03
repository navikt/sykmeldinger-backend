package no.nav.syfo.sykmeldingstatus.db

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import org.postgresql.util.PGobject
import java.sql.Timestamp
import java.time.ZoneOffset

private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
}
fun toPGObject(jsonObject: Any) = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(jsonObject)
}

class SykmeldingStatusDb(private val databaseInterface: DatabaseInterface) {
    suspend fun updateSykmeldingStatus(statusEvent: SykmeldingStatusKafkaEventDTO) = withContext(Dispatchers.IO) {
        databaseInterface.connection.use { connection ->
            connection.prepareStatement(
                """
                    insert into sykmeldingstatus(sykmelding_id, event, timestamp, arbeidsgiver, sporsmal) 
                    values(?, ?, ?, ?, ?) on conflict do nothing;
                """
            ).use { ps ->
                var index = 1
                ps.setString(index++, statusEvent.sykmeldingId)
                ps.setString(index++, statusEvent.statusEvent)
                ps.setTimestamp(index++, Timestamp.from(statusEvent.timestamp.toInstant()))
                ps.setObject(index++, statusEvent.arbeidsgiver?.let { toPGObject(it) })
                ps.setObject(index, statusEvent.sporsmals?.let { toPGObject(it) })
                ps.executeUpdate().also { connection.commit() }
            }
        }
    }

    suspend fun getLatesSykmeldingStatus(sykmeldingId: String): SykmeldingStatusDbModel? = withContext(Dispatchers.IO) {
        databaseInterface.connection.use { connection ->
            connection.prepareStatement(
                """
                    select * from sykmeldingstatus where sykmelding_id = ? order by timestamp desc limit 1;
                """
            ).use { ps ->
                ps.setString(1, sykmeldingId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        SykmeldingStatusDbModel(
                            statusEvent = rs.getString("event"),
                            timestamp = rs.getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
                            arbeidsgiver = rs.getString("arbeidsgiver")?.let { arbeidsgiver -> objectMapper.readValue(arbeidsgiver) },
                            sporsmals = rs.getString("sporsmal")?.let { sporsmal -> objectMapper.readValue(sporsmal) }
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }
}
