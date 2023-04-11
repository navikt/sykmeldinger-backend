package no.nav.syfo.arbeidsgivere.narmesteleder.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import java.sql.ResultSet
import java.time.ZoneOffset

class NarmestelederDb(
    private val database: DatabaseInterface,
) {
    fun getNarmesteleder(ansattFnr: String): List<NarmestelederDbModel> {
        return database.connection.use { connection ->
            connection.prepareStatement(
                """
                    SELECT * FROM narmesteleder WHERE bruker_fnr = ?;
                """,
            ).use { ps ->
                ps.setString(1, ansattFnr)
                ps.executeQuery().toList { toNarmestelederDbModel() }
            }
        }
    }
}

fun ResultSet.toNarmestelederDbModel(): NarmestelederDbModel =
    NarmestelederDbModel(
        narmestelederId = getString("narmeste_leder_id"),
        orgnummer = getString("orgnummer"),
        brukerFnr = getString("bruker_fnr"),
        lederFnr = getString("narmeste_leder_fnr"),
        navn = getString("navn"),
        timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
    )
