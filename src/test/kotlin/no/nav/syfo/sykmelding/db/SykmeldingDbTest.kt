package no.nav.syfo.sykmelding.db

import io.kotest.core.spec.style.FunSpec
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
import no.nav.syfo.sykmelding.model.SporsmalDTO
import no.nav.syfo.sykmelding.model.SykmeldingStatusDTO
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmeldingstatus.api.v1.ArbeidsgiverStatusDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.testutils.TestDB
import no.nav.syfo.testutils.toPGObject
import org.amshove.kluent.shouldBeEqualTo
import java.sql.Timestamp
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class SykmeldingDbTest : FunSpec({

    val testDb = TestDB.database
    val fnr = "12345678901"
    val sykmeldingId = UUID.randomUUID().toString()
    val sykmeldingDb = SykmeldingDb(testDb)
    context("Leser sykmeldinger fra DB") {
        test("Les sykmelding med status og behandlingsutfall") {
            testDb.insertSymelding(sykmeldingId, fnr, getSykmelding())
            testDb.insertStatus(sykmeldingId, getStatus(StatusEventDTO.SENDT.name, OffsetDateTime.now()))
            testDb.insertStatus(sykmeldingId, getStatus(StatusEventDTO.APEN.name, OffsetDateTime.now().minusDays(1)))
            testDb.insertBehandlingsutfall(sykmeldingId, getBehandlingsutfall(RegelStatusDTO.OK))
            testDb.insertSykmeldt(fnr)
            val sykmeldinger = sykmeldingDb.getSykmeldinger(fnr)
            sykmeldinger.size shouldBeEqualTo 1
            val sykmelding = sykmeldinger.first()
            sykmelding.sykmeldingStatus.statusEvent shouldBeEqualTo StatusEventDTO.SENDT.name
            sykmelding.pasient.fornavn shouldBeEqualTo "fornavn"
            sykmelding.pasient.etternavn shouldBeEqualTo "etternavn"
            sykmelding.pasient.fnr shouldBeEqualTo fnr
            sykmelding.pasient.mellomnavn shouldBeEqualTo "mellomnavn"
        }
    }
})

fun getBehandlingsutfall(status: RegelStatusDTO): BehandlingsutfallDTO {
    return BehandlingsutfallDTO(
        status = status,
        ruleHits = emptyList(),
    )
}

fun getStatus(status: String, timestamp: OffsetDateTime, arbeidsgiverStatusDTO: ArbeidsgiverStatusDTO? = null, sporsmals: List<SporsmalDTO> = emptyList()): SykmeldingStatusDTO {
    return SykmeldingStatusDTO(
        statusEvent = status,
        timestamp = timestamp,
        arbeidsgiver = null,
        sporsmalOgSvarListe = sporsmals
    )
}

private fun DatabaseInterface.insertSykmeldt(fnr: String) {
    connection.use { connection ->
        connection.prepareStatement("INSERT INTO sykmeldt (fnr, fornavn, mellomnavn, etternavn) VALUES (?, ?, ?, ?)").use {
            it.setString(1, fnr)
            it.setString(2, "fornavn")
            it.setString(3, "mellomnavn")
            it.setString(4, "etternavn")
            it.executeUpdate()
        }
        connection.commit()
    }
}

private fun DatabaseInterface.insertBehandlingsutfall(sykmeldingId: String, behandlingsutfall: BehandlingsutfallDTO) {
    connection.use { connection ->
        connection.prepareStatement("""insert into behandlingsutfall (sykmelding_id, behandlingsutfall, rule_hits) values (?, ?, ?);""").use {
            it.setString(1, sykmeldingId)
            it.setString(2, behandlingsutfall.status.name)
            it.setObject(3, behandlingsutfall.ruleHits.toPGObject())
            it.executeUpdate()
        }
        connection.commit()
    }
}

private fun DatabaseInterface.insertStatus(sykmeldingId: String, status: SykmeldingStatusDTO) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            insert into sykmeldingstatus (sykmelding_id, event, timestamp, arbeidsgiver, sporsmal) values (?, ?, ?, ?, ?)
            """.trimIndent()
        ).use {
            it.setString(1, sykmeldingId)
            it.setString(2, status.statusEvent)
            it.setTimestamp(3, Timestamp.from(status.timestamp.toInstant()))
            it.setObject(4, status.arbeidsgiver?.toPGObject())
            it.setObject(5, status.sporsmalOgSvarListe.toPGObject())
            it.executeUpdate()
        }
        connection.commit()
    }
}

private fun DatabaseInterface.insertSymelding(sykmeldingId: String, fnr: String, sykmelding: SykmeldingDbModel) {
    connection.use { connection ->
        connection.prepareStatement(
            """ 
                    insert into sykmelding(sykmelding_id, fnr, sykmelding) 
                    values (?, ?, ?)
            """.trimIndent()
        ).use { preparedStatement ->
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
        sykmeldingsperioder = listOf(
            SykmeldingsperiodeDTO(
                fom = LocalDate.now(),
                tom = LocalDate.now().plusDays(1),
                gradert = null,
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                aktivitetIkkeMulig = AktivitetIkkeMuligDTO(
                    medisinskArsak = MedisinskArsakDTO("Test", emptyList()),
                    arbeidsrelatertArsak = null
                ),
                reisetilskudd = false,
            )
        ),
        medisinskVurdering = MedisinskVurderingDTO(
            hovedDiagnose = DiagnoseDTO("ABC", "syk", "syk"),
            biDiagnoser = emptyList(),
            annenFraversArsak = null,
            svangerskap = false,
            yrkesskade = false,
            yrkesskadeDato = null
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
        behandler = BehandlerDTO("", "", "", adresse = AdresseDTO(null, null, null, null, null), "tlf"),
        syketilfelleStartDato = null,
        navnFastlege = null,
        egenmeldt = false,
        papirsykmelding = false,
        harRedusertArbeidsgiverperiode = null,
        merknader = null
    )
}
