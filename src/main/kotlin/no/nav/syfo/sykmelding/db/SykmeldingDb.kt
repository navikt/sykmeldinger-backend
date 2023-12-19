package no.nav.syfo.sykmelding.db

import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.db.model.SykmeldingDbModel
import no.nav.syfo.sykmelding.model.BehandlingsutfallDTO
import no.nav.syfo.sykmelding.model.PasientDTO
import no.nav.syfo.sykmelding.model.RegelStatusDTO
import no.nav.syfo.sykmelding.model.ShortNameDTO
import no.nav.syfo.sykmelding.model.SporsmalDTO
import no.nav.syfo.sykmelding.model.SporsmalOgSvarDTO
import no.nav.syfo.sykmelding.model.SvarDTO
import no.nav.syfo.sykmelding.model.SvartypeDTO
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.model.SykmeldingStatusDTO
import no.nav.syfo.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.sykmelding.model.UtenlandskSykmelding
import no.nav.syfo.sykmeldingstatus.api.v1.ArbeidsgiverStatusDTO

class SykmeldingDb(private val database: DatabaseInterface) {

    suspend fun getSykmelding(sykmeldingId: String, fnr: String): SykmeldingDTO? =
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection
                    .prepareStatement(
                        """
                select
                b.sykmelding_id,
                sykmelding,
                ss.event,
                ss.arbeidsgiver,
                ss.sporsmal,
                ss.timestamp,
                b.behandlingsutfall,
                b.rule_hits,
                s.fornavn,
                s.etternavn,
                s.mellomnavn,
                s.fnr, 
                ss.tidligere_arbeidsgiver
                from sykmelding sykmelding
                inner join sykmeldingstatus ss on ss.sykmelding_id = sykmelding.sykmelding_id and ss.timestamp = (select max(timestamp) from sykmeldingstatus where sykmelding_id = sykmelding.sykmelding_id)
                inner join behandlingsutfall b on sykmelding.sykmelding_id = b.sykmelding_id
                inner join sykmeldt s on sykmelding.fnr = s.fnr
                where sykmelding.sykmelding_id = ? and sykmelding.fnr = ?;
                """,
                    )
                    .use { preparedStatement ->
                        preparedStatement.setString(1, sykmeldingId)
                        preparedStatement.setString(2, fnr)
                        preparedStatement.executeQuery().toList { toSykmelding() }.firstOrNull()
                    }
            }
        }

    suspend fun getSykmeldinger(fnr: String): List<SykmeldingDTO> =
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection
                    .prepareStatement(
                        """
                select
                b.sykmelding_id,
                sykmelding,
                ss.event,
                ss.arbeidsgiver,
                ss.sporsmal,
                ss.timestamp,
                b.behandlingsutfall,
                b.rule_hits,
                s.fornavn,
                s.etternavn,
                s.mellomnavn,
                s.fnr,
                ss.tidligere_arbeidsgiver
                from sykmelding sykmelding
                inner join sykmeldingstatus ss on ss.sykmelding_id = sykmelding.sykmelding_id and ss.timestamp = (select max(timestamp) from sykmeldingstatus where sykmelding_id = sykmelding.sykmelding_id)
                inner join behandlingsutfall b on sykmelding.sykmelding_id = b.sykmelding_id
                inner join sykmeldt s on sykmelding.fnr = s.fnr
                where sykmelding.fnr = ?;
                """,
                    )
                    .use { preparedStatement ->
                        preparedStatement.setString(1, fnr)
                        preparedStatement.executeQuery().toList { toSykmelding() }
                    }
            }
        }

    suspend fun getOlderSykmeldinger(fnr: String) =
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection
                    .prepareStatement(
                        """
                select b.sykmelding_id,
                   sykmelding,
                   ss.event,
                   ss.arbeidsgiver,
                   ss.timestamp,
                   b.behandlingsutfall,
                   b.rule_hits
                from sykmelding sykmelding
                     inner join sykmeldingstatus ss
                                on ss.sykmelding_id = sykmelding.sykmelding_id and ss.timestamp =
                                                                                   (select max(timestamp)
                                                                                    from sykmeldingstatus
                                                                                    where sykmelding_id = sykmelding.sykmelding_id)
                     inner join behandlingsutfall b on sykmelding.sykmelding_id = b.sykmelding_id
                     inner join sykmeldt s on sykmelding.fnr = s.fnr
            where sykmelding.fnr = ?
              AND NOT (ss.event = 'APEN')
              AND NOT sykmelding @> '{"merknader": [{"type": "UNDER_BEHANDLING"}]}'

            """
                            .trimIndent(),
                    )
                    .use {
                        it.setString(1, fnr)
                        it.executeQuery().toList { toMinimalSykmelding() }
                    }
            }
        }

    suspend fun getProcessingSykmeldinger(fnr: String) =
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection
                    .prepareStatement(
                        """
                select b.sykmelding_id,
                   sykmelding,
                   ss.event,
                   ss.arbeidsgiver,
                   ss.timestamp,
                   b.behandlingsutfall,
                   b.rule_hits
            from sykmelding sykmelding
                     inner join sykmeldingstatus ss
                                on ss.sykmelding_id = sykmelding.sykmelding_id and ss.timestamp =
                                                                                   (select max(timestamp)
                                                                                    from sykmeldingstatus
                                                                                    where sykmelding_id = sykmelding.sykmelding_id)
                     inner join behandlingsutfall b on sykmelding.sykmelding_id = b.sykmelding_id
                     inner join sykmeldt s on sykmelding.fnr = s.fnr
            where sykmelding.fnr = ?
              AND (ss.event = 'SENDT')
              AND sykmelding @> '{"merknader": [{"type": "UNDER_BEHANDLING"}]}'
            """
                            .trimIndent(),
                    )
                    .use {
                        it.setString(1, fnr)
                        it.executeQuery().toList { toMinimalSykmelding() }
                    }
            }
        }

    suspend fun getUnsentSykmeldinger(fnr: String) =
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection
                    .prepareStatement(
                        """
            select b.sykmelding_id,
                   sykmelding,
                   ss.event,
                   ss.arbeidsgiver,
                   ss.timestamp,
                   b.behandlingsutfall,
                   b.rule_hits
            from sykmelding sykmelding
                     inner join sykmeldingstatus ss
                                on ss.sykmelding_id = sykmelding.sykmelding_id and ss.timestamp =
                                                                                   (select max(timestamp)
                                                                                    from sykmeldingstatus
                                                                                    where sykmelding_id = sykmelding.sykmelding_id)
                     inner join behandlingsutfall b on sykmelding.sykmelding_id = b.sykmelding_id
                     inner join sykmeldt s on sykmelding.fnr = s.fnr
            where sykmelding.fnr = ?
              AND ss.event = 'APEN';
            """
                            .trimIndent(),
                    )
                    .use {
                        it.setString(1, fnr)
                        it.executeQuery().toList { toMinimalSykmelding() }
                    }
            }
        }

    suspend fun sykmeldingExists(sykmeldingId: String): Boolean =
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection
                    .prepareStatement(
                        """select true from sykmelding where sykmelding_id = ? limit 1""",
                    )
                    .use {
                        it.setString(1, sykmeldingId)
                        it.executeQuery().next()
                    }
            }
        }

    suspend fun behandlingsutfallExists(sykmeldingId: String): Boolean =
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection
                    .prepareStatement(
                        """select true from behandlingsutfall where sykmelding_id = ?""",
                    )
                    .use {
                        it.setString(1, sykmeldingId)
                        it.executeQuery().next()
                    }
            }
        }

    suspend fun sykmeldingStatusExists(sykmeldingId: String): Boolean =
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection
                    .prepareStatement(
                        """select true from sykmeldingstatus where sykmelding_id = ?""",
                    )
                    .use {
                        it.setString(1, sykmeldingId)
                        it.executeQuery().next()
                    }
            }
        }

    suspend fun sykmeldtExists(fnr: String): Boolean =
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection.prepareStatement("""select true from sykmeldt where fnr = ?""").use {
                    it.setString(1, fnr)
                    it.executeQuery().next()
                }
            }
        }
}

data class ArbeidsgiverMinimal(
    val orgNavn: String,
    val orgnummer: String,
    val juridiskOrgnummer: String,
)

data class RuleHitsMinimal(
    val ruleName: String,
    val ruleStatus: String,
    val messageForUser: String,
    val messageForSender: String,
)

data class GradertMinimal(
    val grad: Int,
)

data class MinimalPeriod(
    val fom: String,
    val tom: String,
    val type: String,
    val gradert: GradertMinimal?,
    val behandlingsdager: Int?,
)

data class SykmeldingMinimal(
    val papirsykmelding: Boolean,
    val egenmeldt: Boolean?,
    val utenlandskSykmelding: UtenlandskSykmelding?,
    val sykmeldingsperioder: List<MinimalPeriod>,
)

data class MinimalSykmelding(
    val sykmelding_id: String,
    val event: String,
    val arbeidsgiver: ArbeidsgiverMinimal?,
    val rule_hits: List<RuleHitsMinimal>,
    val timestamp: OffsetDateTime,
    val behandlingsutfall: String,
    val sykmelding: SykmeldingMinimal,
)

private fun ResultSet.toMinimalSykmelding(): MinimalSykmelding {
    return MinimalSykmelding(
        sykmelding_id = getString("sykmelding_id"),
        event = getString("event"),
        arbeidsgiver = getObject("arbeidsgiver")?.let { objectMapper.readValue(it.toString()) },
        rule_hits = objectMapper.readValue(getString("rule_hits")),
        timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
        behandlingsutfall = getString("behandlingsutfall"),
        sykmelding = objectMapper.readValue(getString("sykmelding")),
    )
}

private fun ResultSet.toSykmelding(): SykmeldingDTO {
    val sykmelding: SykmeldingDbModel = objectMapper.readValue(getString("sykmelding"))
    return SykmeldingDTO(
        pasient =
            PasientDTO(
                fnr = getString("fnr"),
                fornavn = getString("fornavn"),
                mellomnavn = getString("mellomnavn"),
                etternavn = getString("etternavn"),
            ),
        id = getString("sykmelding_id"),
        mottattTidspunkt = sykmelding.mottattTidspunkt,
        behandlingsutfall =
            BehandlingsutfallDTO(
                status = RegelStatusDTO.valueOf(getString("behandlingsutfall")),
                ruleHits = objectMapper.readValue(getString("rule_hits")),
            ),
        arbeidsgiver = sykmelding.arbeidsgiver,
        legekontorOrgnummer = sykmelding.legekontorOrgnummer,
        sykmeldingsperioder = sykmelding.sykmeldingsperioder,
        sykmeldingStatus =
            SykmeldingStatusDTO(
                statusEvent = getString("event"),
                timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
                arbeidsgiver =
                    getObject("arbeidsgiver")?.let {
                        objectMapper.readValue<ArbeidsgiverStatusDTO>(it.toString())
                    },
                sporsmalOgSvarListe =
                    // TODO: These could just be mapped directly from alle_sporsmal when all data is
                    // migrated
                    getString("sporsmal")?.let {
                        objectMapper.readValue<List<SporsmalOgSvarDTO>>(it).map { sporsmalOgSvarDTO
                            ->
                            SporsmalDTO(
                                tekst = sporsmalOgSvarDTO.tekst,
                                shortName = ShortNameDTO.valueOf(sporsmalOgSvarDTO.shortName.name),
                                svar =
                                    SvarDTO(
                                        svar = sporsmalOgSvarDTO.svar,
                                        svarType =
                                            SvartypeDTO.valueOf(sporsmalOgSvarDTO.svartype.name),
                                    ),
                            )
                        }
                    }
                        ?: emptyList(),
                tidligereArbeidsgiver =
                    getObject("tidligere_arbeidsgiver")?.let {
                        objectMapper.readValue<TidligereArbeidsgiverDTO>(it.toString())
                    },
            ),
        medisinskVurdering = sykmelding.medisinskVurdering,
        prognose = sykmelding.prognose,
        utdypendeOpplysninger = sykmelding.utdypendeOpplysninger,
        meldingTilNAV = sykmelding.meldingTilNAV,
        kontaktMedPasient = sykmelding.kontaktMedPasient,
        meldingTilArbeidsgiver = sykmelding.meldingTilArbeidsgiver,
        andreTiltak = sykmelding.andreTiltak,
        tiltakNAV = sykmelding.tiltakNAV,
        tiltakArbeidsplassen = sykmelding.tiltakArbeidsplassen,
        behandletTidspunkt = sykmelding.behandletTidspunkt,
        behandler = sykmelding.behandler,
        syketilfelleStartDato = sykmelding.syketilfelleStartDato,
        navnFastlege = sykmelding.navnFastlege,
        egenmeldt = sykmelding.egenmeldt,
        papirsykmelding = sykmelding.papirsykmelding,
        harRedusertArbeidsgiverperiode = sykmelding.harRedusertArbeidsgiverperiode,
        merknader = sykmelding.merknader,
        skjermesForPasient = false,
        rulesetVersion = sykmelding.rulesetVersion,
        utenlandskSykmelding = sykmelding.utenlandskSykmelding
    )
}
