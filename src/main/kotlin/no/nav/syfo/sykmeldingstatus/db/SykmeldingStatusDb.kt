package no.nav.syfo.sykmeldingstatus.db

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.exception.SykmeldingStatusNotFoundException
import org.postgresql.util.PGobject
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.ZoneOffset

private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
}

private fun toPGObject(jsonObject: Any) = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(jsonObject)
}

class SykmeldingStatusDb(private val databaseInterface: DatabaseInterface) {

    suspend fun insertStatus(event: SykmeldingStatusKafkaEventDTO) = withContext(Dispatchers.IO) {
        databaseInterface.connection.use { connection ->
            connection.prepareStatement(
                """
            insert into sykmeldingstatus(sykmelding_id, event, timestamp, arbeidsgiver, sporsmal) values(?, ?, ?, ?, ?) on conflict do nothing;
        """
            ).use { ps ->
                var index = 1
                ps.setString(index++, event.sykmeldingId)
                ps.setString(index++, event.statusEvent)
                ps.setTimestamp(index++, Timestamp.from(event.timestamp.toInstant()))
                ps.setObject(index++, event.arbeidsgiver?.let { toPGObject(it) })
                ps.setObject(index, event.sporsmals?.let { toPGObject(it) })
                ps.executeUpdate()
            }
            connection.commit()
        }
    }

    suspend fun getLatestStatus(sykmeldingId: String, fnr: String): SykmeldingStatusEventDTO = withContext(Dispatchers.IO) {
        databaseInterface.connection.use { connection ->
            connection.prepareStatement(
                """
                select
                    ss.event,
                    ss.timestamp,
                    syk.sykmelding['egenmeldt'] as egenmeldt,
                    beh.behandlingsutfall
                    from sykmeldingstatus ss
                inner join sykmelding syk on syk.sykmelding_id = ss.sykmelding_id and syk.fnr = ?
                inner join behandlingsutfall beh on ss.sykmelding_id = beh.sykmelding_id
                    where ss.sykmelding_id = ?
                        and timestamp = (select max(timestamp) from sykmeldingstatus where sykmelding_id = ss.sykmelding_id)
                """
            ).use { ps ->
                ps.setString(1, sykmeldingId)
                ps.setString(2, fnr)
                ps.executeQuery().toStatusEventDTO(sykmeldingId)
            }
        }
    }
}

private fun ResultSet.toStatusEventDTO(sykmeldingId: String): SykmeldingStatusEventDTO {
    return if (next()) {
        SykmeldingStatusEventDTO(
            statusEvent = StatusEventDTO.valueOf(getString("event")),
            timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
            erEgenmeldt = getBoolean("egenmeldt"),
            erAvvist = getString("behandlingsutfall").let { it == "INVALID" }
        )
    } else {
        throw SykmeldingStatusNotFoundException("Fant ikke status for sykmelding $sykmeldingId")
    }
}
