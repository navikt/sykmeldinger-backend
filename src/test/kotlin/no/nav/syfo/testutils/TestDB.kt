package no.nav.syfo.testutils

import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.application.database.Database
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.sykmelding.db.model.SykmeldingDbModel
import no.nav.syfo.sykmelding.model.AdresseDTO
import no.nav.syfo.sykmelding.model.AktivitetIkkeMuligDTO
import no.nav.syfo.sykmelding.model.BehandlerDTO
import no.nav.syfo.sykmelding.model.BehandlingsutfallDTO
import no.nav.syfo.sykmelding.model.DiagnoseDTO
import no.nav.syfo.sykmelding.model.KontaktMedPasientDTO
import no.nav.syfo.sykmelding.model.MedisinskArsakDTO
import no.nav.syfo.sykmelding.model.MedisinskVurderingDTO
import no.nav.syfo.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.model.RegelStatusDTO
import no.nav.syfo.sykmelding.model.ShortNameDTO
import no.nav.syfo.sykmelding.model.SporsmalDTO
import no.nav.syfo.sykmelding.model.SporsmalOgSvarDTO
import no.nav.syfo.sykmelding.model.SvartypeDTO
import no.nav.syfo.sykmelding.model.SykmeldingStatusDTO
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.sykmeldingstatus.TestHelper.Companion.januar
import no.nav.syfo.sykmeldingstatus.api.v1.ArbeidsgiverStatusDTO
import no.nav.syfo.sykmeldingstatus.api.v2.Arbeidssituasjon
import no.nav.syfo.sykmeldingstatus.api.v2.JaEllerNei
import no.nav.syfo.sykmeldingstatus.api.v2.SporsmalSvar
import no.nav.syfo.sykmeldingstatus.api.v2.SykmeldingFormResponse
import no.nav.syfo.utils.Environment
import no.nav.syfo.utils.objectMapper
import org.postgresql.util.PGobject
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.OffsetDateTime

class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:14")

class TestDB private constructor() {
    companion object {
        var database: DatabaseInterface
        private val psqlContainer: PsqlContainer =
            PsqlContainer()
                .withExposedPorts(5432)
                .withUsername("username")
                .withPassword("password")
                .withDatabaseName("database")

        init {
            psqlContainer.start()
            val mockEnv = mockk<Environment>(relaxed = true)
            every { mockEnv.databaseUsername } returns "username"
            every { mockEnv.databasePassword } returns "password"
            every { mockEnv.dbName } returns "database"
            every { mockEnv.jdbcUrl() } returns psqlContainer.jdbcUrl
            database = Database(mockEnv).initializeDatasource().runFlywayMigrations()
        }

        fun clearAllData() {
            return database.connection.use {
                it.prepareStatement(
                        """
                    DELETE FROM narmesteleder;
                    DELETE FROM sykmeldingstatus;
                    DELETE FROM arbeidsforhold;
                    DELETE FROM sykmelding;
                    DELETE FROM sykmeldt;
                    DELETE FROM behandlingsutfall;
                """,
                    )
                    .use { ps -> ps.executeUpdate() }
                it.commit()
            }
        }
    }
}

fun Any.toPGObject() =
    PGobject().also {
        it.type = "json"
        it.value = objectMapper.writeValueAsString(this)
    }

fun getBehandlingsutfall(status: RegelStatusDTO): BehandlingsutfallDTO {
    return BehandlingsutfallDTO(
        status = status,
        ruleHits = emptyList(),
    )
}

fun getStatus(
    status: String,
    timestamp: OffsetDateTime,
    arbeidsgiverStatusDTO: ArbeidsgiverStatusDTO? = null,
    sporsmals: List<SporsmalDTO> = emptyList(),
    brukerSvar: SykmeldingFormResponse? = null,
): SykmeldingStatusDTO {
    return SykmeldingStatusDTO(
        statusEvent = status,
        timestamp = timestamp,
        arbeidsgiver = arbeidsgiverStatusDTO,
        sporsmalOgSvarListe = sporsmals,
        brukerSvar = brukerSvar,
    )
}

fun DatabaseInterface.insertSykmeldt(fnr: String, foedselsdato: LocalDate = 1.januar(1985)) {
    connection.use { connection ->
        connection
            .prepareStatement(
                "INSERT INTO sykmeldt (fnr, fornavn, mellomnavn, etternavn, foedselsdato) VALUES (?, ?, ?, ?, ?)",
            )
            .use {
                it.setString(1, fnr)
                it.setString(2, "fornavn")
                it.setString(3, "mellomnavn")
                it.setString(4, "etternavn")
                it.setDate(5, Date.valueOf(foedselsdato))
                it.executeUpdate()
            }
        connection.commit()
    }
}

fun DatabaseInterface.insertBehandlingsutfall(
    sykmeldingId: String,
    behandlingsutfall: BehandlingsutfallDTO
) {
    connection.use { connection ->
        connection
            .prepareStatement(
                """insert into behandlingsutfall (sykmelding_id, behandlingsutfall, rule_hits) values (?, ?, ?);""",
            )
            .use {
                it.setString(1, sykmeldingId)
                it.setString(2, behandlingsutfall.status.name)
                it.setObject(3, behandlingsutfall.ruleHits.toPGObject())
                it.executeUpdate()
            }
        connection.commit()
    }
}

fun DatabaseInterface.insertStatus(
    sykmeldingId: String,
    status: SykmeldingStatusDTO,
    tidligereArbeidsgiver: TidligereArbeidsgiverDTO? = null
) {
    connection.use { connection ->
        connection
            .prepareStatement(
                """
            insert into sykmeldingstatus (sykmelding_id, event, timestamp, arbeidsgiver, sporsmal, alle_sporsmal, tidligere_arbeidsgiver) values (?, ?, ?, ?, ?, ?, ?)
            """
                    .trimIndent(),
            )
            .use {
                it.setString(1, sykmeldingId)
                it.setString(2, status.statusEvent)
                it.setTimestamp(3, Timestamp.from(status.timestamp.toInstant()))
                it.setObject(4, status.arbeidsgiver?.toPGObject())
                it.setObject(
                    5,
                    status.sporsmalOgSvarListe
                        .map { spm ->
                            SporsmalOgSvarDTO(
                                tekst = spm.tekst,
                                shortName = ShortNameDTO.valueOf(spm.shortName.name),
                                svartype = SvartypeDTO.valueOf(spm.svar.svarType.name),
                                svar = spm.svar.svar,
                            )
                        }
                        .toPGObject(),
                )
                it.setObject(
                    6,
                    SykmeldingFormResponse(
                            erOpplysningeneRiktige =
                                SporsmalSvar(
                                    sporsmaltekst = "Er opplysningene riktige?",
                                    svar = JaEllerNei.JA,
                                ),
                            arbeidssituasjon =
                                SporsmalSvar(
                                    sporsmaltekst = "Hva er din arbeidssituasjon?",
                                    Arbeidssituasjon.ANNET,
                                ),
                            uriktigeOpplysninger = null,
                            arbeidsgiverOrgnummer = null,
                            riktigNarmesteLeder = null,
                            harBruktEgenmelding = null,
                            egenmeldingsperioder = null,
                            harForsikring = null,
                            egenmeldingsdager = null,
                            harBruktEgenmeldingsdager = null,
                            fisker = null,
                            arbeidsledig = null,
                        )
                        .toPGObject(),
                )
                it.setObject(7, tidligereArbeidsgiver?.toPGObject())
                it.executeUpdate()
            }
        connection.commit()
    }
}

fun DatabaseInterface.insertSymelding(
    sykmeldingId: String,
    fnr: String,
    sykmelding: SykmeldingDbModel
) {
    connection.use { connection ->
        connection
            .prepareStatement(
                """ 
                    insert into sykmelding(sykmelding_id, fnr, sykmelding) 
                    values (?, ?, ?)
            """
                    .trimIndent(),
            )
            .use { preparedStatement ->
                preparedStatement.setString(1, sykmeldingId)
                preparedStatement.setString(2, fnr)
                preparedStatement.setObject(3, sykmelding.toPGObject())
                preparedStatement.executeUpdate()
            }
        connection.commit()
    }
}

fun getSykmelding(): SykmeldingDbModel {
    return SykmeldingDbModel(
        mottattTidspunkt = OffsetDateTime.now(),
        legekontorOrgnummer = null,
        arbeidsgiver = null,
        sykmeldingsperioder =
            listOf(
                SykmeldingsperiodeDTO(
                    fom = LocalDate.now(),
                    tom = LocalDate.now().plusDays(1),
                    gradert = null,
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    aktivitetIkkeMulig =
                        AktivitetIkkeMuligDTO(
                            medisinskArsak = MedisinskArsakDTO("Test", emptyList()),
                            arbeidsrelatertArsak = null,
                        ),
                    reisetilskudd = false,
                ),
            ),
        medisinskVurdering =
            MedisinskVurderingDTO(
                hovedDiagnose = DiagnoseDTO("ABC", "syk", "syk"),
                biDiagnoser = emptyList(),
                annenFraversArsak = null,
                svangerskap = false,
                yrkesskade = false,
                yrkesskadeDato = null,
            ),
        prognose = null,
        utdypendeOpplysninger = emptyMap(),
        tiltakArbeidsplassen = null,
        tiltakNAV = null,
        andreTiltak = null,
        meldingTilNAV = null,
        meldingTilArbeidsgiver = null,
        kontaktMedPasient = KontaktMedPasientDTO(kontaktDato = null, begrunnelseIkkeKontakt = null),
        behandletTidspunkt = OffsetDateTime.now(),
        behandler =
            BehandlerDTO("", "", "", adresse = AdresseDTO(null, null, null, null, null), "tlf"),
        syketilfelleStartDato = null,
        navnFastlege = null,
        egenmeldt = false,
        papirsykmelding = false,
        harRedusertArbeidsgiverperiode = null,
        merknader = null,
        rulesetVersion = null,
        utenlandskSykmelding = null,
    )
}
