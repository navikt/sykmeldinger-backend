package no.nav.syfo.sykmeldingstatus.db

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.sykmeldingstatus.api.v1.ArbeidsgiverStatusDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v2.SykmeldingFormResponse
import no.nav.syfo.sykmeldingstatus.exception.SykmeldingStatusNotFoundException
import no.nav.syfo.sykmeldingstatus.kafka.SykmeldingWithArbeidsgiverStatus
import no.nav.syfo.sykmeldingstatus.kafka.model.ArbeidsgiverStatusKafkaDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.SporsmalOgSvarKafkaDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.TidligereArbeidsgiverKafkaDTO
import no.nav.syfo.utils.logger
import org.postgresql.util.PGobject
import org.postgresql.util.PSQLException

private val objectMapper: ObjectMapper =
    jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
    }

private fun toPGObject(jsonObject: Any) =
    PGobject().also {
        it.type = "json"
        it.value = objectMapper.writeValueAsString(jsonObject)
    }

class SykmeldingStatusDb(private val databaseInterface: DatabaseInterface) {
    private val logger = logger()

    suspend fun insertStatus(
        event: SykmeldingStatusKafkaEventDTO,
        response: SykmeldingFormResponse?,
        beforeCommit: suspend () -> Unit
    ) =
        withContext(Dispatchers.IO) {
            try {
                logger.info(
                    "Inserting status for sykmelding {}, formResponse is: {}",
                    event.sykmeldingId,
                    response == null,
                )

                databaseInterface.connection.use { connection ->
                    connection
                        .prepareStatement(
                            """insert into sykmeldingstatus(sykmelding_id, event, timestamp, arbeidsgiver, sporsmal, alle_sporsmal, tidligere_arbeidsgiver)
                                   values (?, ?, ?, ?, ?, ?, ?);""",
                        )
                        .use { ps ->
                            var index = 1
                            ps.setString(index++, event.sykmeldingId)
                            ps.setString(index++, event.statusEvent)
                            ps.setTimestamp(index++, Timestamp.from(event.timestamp.toInstant()))
                            ps.setObject(index++, event.arbeidsgiver?.let { toPGObject(it) })
                            ps.setObject(index++, event.sporsmals?.let { toPGObject(it) })
                            ps.setObject(index++, response?.let { toPGObject(it) })
                            ps.setObject(index, event.tidligereArbeidsgiver?.let { toPGObject(it) })
                            ps.executeUpdate()
                        }
                    try {
                        beforeCommit()
                        connection.commit()
                    } catch (ex: Exception) {
                        logger.error("Status inserction failed on block receiver, rolling back")
                        connection.rollback()
                    }
                }
            } catch (ex: PSQLException) {
                logger.warn(ex.message)
            }
        }

    suspend fun getLatestStatus(sykmeldingId: String, fnr: String): SykmeldingStatusEventDTO =
        withContext(Dispatchers.IO) {
            databaseInterface.connection.use { connection ->
                connection
                    .prepareStatement(
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
                """,
                    )
                    .use { ps ->
                        ps.setString(1, fnr)
                        ps.setString(2, sykmeldingId)
                        ps.executeQuery().toStatusEventDTO(sykmeldingId)
                    }
            }
        }

    suspend fun getSykmeldingStatus(
        sykmeldingId: String,
        fnr: String
    ): Pair<SykmeldingStatusKafkaEventDTO, SykmeldingFormResponse?> =
        withContext(Dispatchers.IO) {
            databaseInterface.connection.use { connection ->
                connection
                    .prepareStatement(
                        """
                select
                    ss.event,
                    ss.timestamp,
                    ss.arbeidsgiver,
                    ss.sporsmal,
                    ss.alle_sporsmal,
                    ss.tidligere_arbeidsgiver
                    from sykmeldingstatus ss
                inner join sykmelding syk on syk.sykmelding_id = ss.sykmelding_id and syk.fnr = ?
                    where ss.sykmelding_id = ?
                        and timestamp = (select max(timestamp) from sykmeldingstatus where sykmelding_id = ss.sykmelding_id)
                """,
                    )
                    .use { ps ->
                        ps.setString(1, fnr)
                        ps.setString(2, sykmeldingId)
                        ps.executeQuery().toSykmeldingStatusEvent(sykmeldingId)
                    }
            }
        }

    suspend fun getSykmeldingWithStatus(fnr: String): List<SykmeldingWithArbeidsgiverStatus> =
        withContext(Dispatchers.IO) {
            databaseInterface.connection.use { connection: Connection ->
                connection
                    .prepareStatement(
                        """
                select
                sykmelding.sykmelding_id,
                sykmelding.sykmelding -> 'sykmeldingsperioder' as sykmeldingsperioder,
                ss.event,
                ss.arbeidsgiver,
                ss.tidligere_arbeidsgiver
                from sykmelding
                inner join sykmeldingstatus ss on ss.sykmelding_id = sykmelding.sykmelding_id and ss.timestamp = (select max(timestamp) from sykmeldingstatus where sykmelding_id = sykmelding.sykmelding_id)
                where sykmelding.fnr = ?;
              """
                            .trimIndent()
                    )
                    .use { ps ->
                        ps.setString(1, fnr)
                        ps.executeQuery().toList { toSykmeldingWithArbeidsgiverStatus() }
                    }
            }
        }

    suspend fun getArbeidsledigOrgNavnFromOrgnummer(fnr: String, arbeidsledigOrgnummer: String?) =
        withContext(Dispatchers.IO) {
            databaseInterface.connection.use { connection: Connection ->
                connection
                    .prepareStatement(
                        """
                select
                ss.arbeidsgiver
                from sykmelding s 
                inner join sykmeldingstatus ss on ss.sykmelding_id = s.sykmelding_id and ss.timestamp = (select max(timestamp) from sykmeldingstatus where sykmelding_id = s.sykmelding_id)
                where s.fnr = ?;
              """
                            .trimIndent()
                    )
                    .use { ps ->
                        ps.setString(1, fnr)
                        ps.executeQuery()
                            .toList {
                                getObject("arbeidsgiver")?.let {
                                    objectMapper.readValue<ArbeidsgiverStatusDTO>(it.toString())
                                }
                            }
                            .find { it?.orgnummer == arbeidsledigOrgnummer }
                            ?.orgNavn
                    }
            }
        }

    private fun ResultSet.toStatusEventDTO(sykmeldingId: String): SykmeldingStatusEventDTO {
        return if (next()) {
            SykmeldingStatusEventDTO(
                statusEvent = StatusEventDTO.valueOf(getString("event")),
                timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
                erEgenmeldt = getBoolean("egenmeldt"),
                erAvvist = getString("behandlingsutfall").let { it == "INVALID" },
            )
        } else {
            throw SykmeldingStatusNotFoundException("Fant ikke status for sykmelding $sykmeldingId")
        }
    }

    private fun ResultSet.toSykmeldingStatusEvent(
        sykmeldingId: String
    ): Pair<SykmeldingStatusKafkaEventDTO, SykmeldingFormResponse?> {
        return if (next()) {
            SykmeldingStatusKafkaEventDTO(
                sykmeldingId = sykmeldingId,
                timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
                arbeidsgiver =
                    getObject("arbeidsgiver")?.let {
                        objectMapper.readValue<ArbeidsgiverStatusKafkaDTO>(it.toString())
                    },
                sporsmals =
                    // TODO: These could just be mapped directly from alle_sporsmal when all data is
                    // migrated
                    getString("sporsmal")?.let {
                        objectMapper.readValue<List<SporsmalOgSvarKafkaDTO>>(it)
                    }
                        ?: emptyList(),
                statusEvent = getString("event"),
                tidligereArbeidsgiver =
                    getObject("tidligere_arbeidsgiver")?.let {
                        objectMapper.readValue<TidligereArbeidsgiverKafkaDTO>(it.toString())
                    },
            ) to
                getObject("alle_sporsmal")?.let {
                    objectMapper.readValue<SykmeldingFormResponse>(it.toString())
                }
        } else {
            throw SykmeldingStatusNotFoundException("Fant ikke status for sykmelding $sykmeldingId")
        }
    }
}

private fun ResultSet.toSykmeldingWithArbeidsgiverStatus() =
    SykmeldingWithArbeidsgiverStatus(
        sykmeldingId = getString("sykmelding_id"),
        sykmeldingsperioder = objectMapper.readValue(getString("sykmeldingsperioder")),
        arbeidsgiver =
            getObject("arbeidsgiver")?.let {
                objectMapper.readValue<ArbeidsgiverStatusDTO>(it.toString())
            },
        tidligereArbeidsgiver =
            getObject("tidligere_arbeidsgiver")?.let {
                objectMapper.readValue<TidligereArbeidsgiverDTO>(it.toString())
            },
        statusEvent = getString("event")
    )
