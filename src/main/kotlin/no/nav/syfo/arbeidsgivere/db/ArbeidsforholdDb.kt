package no.nav.syfo.arbeidsgivere.db

import java.sql.ResultSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.arbeidsgivere.model.Arbeidsforhold
import no.nav.syfo.arbeidsgivere.model.ArbeidsforholdType

class ArbeidsforholdDb(private val database: DatabaseInterface) {
    suspend fun getArbeidsforhold(fnr: String): List<Arbeidsforhold> =
        withContext(Dispatchers.IO) {
            database.connection.use {
                it.prepareStatement(
                        """
                    SELECT * FROM arbeidsforhold WHERE fnr = ?;
                """,
                    )
                    .use { ps ->
                        ps.setString(1, fnr)
                        ps.executeQuery().toList { toArbeidsforhold() }
                    }
            }
        }
}

fun ResultSet.toArbeidsforhold(): Arbeidsforhold =
    Arbeidsforhold(
        id = getString("id").toInt(),
        fnr = getString("fnr"),
        orgnummer = getString("orgnummer"),
        juridiskOrgnummer = getString("juridisk_orgnummer"),
        orgNavn = getString("orgnavn"),
        fom = getDate("fom").toLocalDate(),
        tom = getDate("tom")?.toLocalDate(),
        type = getString("type")?.let { ArbeidsforholdType.valueOf(it) }
    )
