package no.nav.syfo.sykmelding.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.db.model.SykmeldingDbModel
import java.sql.ResultSet

class SykmeldingDb(private val database: DatabaseInterface) {
    fun getSykmeldinger(fnr: String) {
        return database.connection.use { connection ->
            connection.prepareStatement(
                """
                select
                b.sykmelding_id,
                sykmelding,
                ss.event,
                ss.arbeidsgiver,
                ss.sporsmal,
                b.behandlingsutfall,
                b.rule_hits
                from sykmelding sykmelding
                inner join sykmeldingstatus ss on ss.sykmelding_id = sykmelding.sykmelding_id and ss.timestamp = (select timestamp from sykmeldingstatus where sykmelding_id = sykmelding.sykmelding_id order by timestamp desc limit 1)
                inner join behandlingsutfall b on sykmelding.sykmelding_id = b.sykmelding_id
                where fnr = ?;
                """
            ).use { preparedStatement ->
                preparedStatement.setString(1, fnr)
                preparedStatement.executeQuery().toList { toSykmelding() }
            }
        }
    }
}

private fun ResultSet.toSykmelding(): SykmeldingDbModel {
    return objectMapper.readValue(getString("sykmelding"))
}
