package no.nav.syfo.sykmeldingstatus.db

import io.kotest.core.spec.style.FunSpec
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertFailsWith
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmelding.model.RegelStatusDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.exception.SykmeldingStatusNotFoundException
import no.nav.syfo.testutils.TestDB
import no.nav.syfo.testutils.getBehandlingsutfall
import no.nav.syfo.testutils.getStatus
import no.nav.syfo.testutils.getSykmelding
import no.nav.syfo.testutils.insertBehandlingsutfall
import no.nav.syfo.testutils.insertStatus
import no.nav.syfo.testutils.insertSymelding
import org.amshove.kluent.shouldBeEqualTo

class SykmeldingStatusDbTest :
    FunSpec({
        val database = SykmeldingStatusDb(TestDB.database)

        beforeTest { TestDB.clearAllData() }
        test("test sykmeldingstatus not found") {
            assertFailsWith<SykmeldingStatusNotFoundException> {
                database.getLatestStatus("1", "fnr")
            }
        }

        test("test sykmeldingstatus with wrong fnr") {
            TestDB.database.insertStatus(
                "1",
                getStatus(StatusEventDTO.APEN.name, OffsetDateTime.now().minusDays(1))
            )
            TestDB.database.insertBehandlingsutfall("1", getBehandlingsutfall(RegelStatusDTO.OK))
            TestDB.database.insertSymelding("1", "fnr2", getSykmelding())
            assertFailsWith<SykmeldingStatusNotFoundException> {
                database.getLatestStatus("1", "fnr")
            }
        }

        test("test get latest sykmeldingstatus") {
            val apenStatus = getStatus(StatusEventDTO.APEN.name, OffsetDateTime.now().minusDays(1))
            TestDB.database.insertStatus(
                "1",
                getStatus(StatusEventDTO.APEN.name, OffsetDateTime.now().minusDays(1))
            )
            TestDB.database.insertBehandlingsutfall("1", getBehandlingsutfall(RegelStatusDTO.OK))
            TestDB.database.insertSymelding("1", "fnr", getSykmelding())

            val status = database.getLatestStatus("1", "fnr")
            status.statusEvent.name shouldBeEqualTo apenStatus.statusEvent
            status.erAvvist shouldBeEqualTo false
            status.erEgenmeldt shouldBeEqualTo false
        }

        test("test get latest avvist sykmeldingstatus") {
            val apenStatus = getStatus(StatusEventDTO.APEN.name, OffsetDateTime.now().minusDays(1))
            TestDB.database.insertStatus(
                "1",
                getStatus(StatusEventDTO.APEN.name, OffsetDateTime.now().minusDays(1))
            )
            TestDB.database.insertBehandlingsutfall(
                "1",
                getBehandlingsutfall(RegelStatusDTO.INVALID)
            )
            TestDB.database.insertSymelding("1", "fnr", getSykmelding())

            val status = database.getLatestStatus("1", "fnr")
            status.statusEvent.name shouldBeEqualTo apenStatus.statusEvent
            status.erAvvist shouldBeEqualTo true
            status.erEgenmeldt shouldBeEqualTo false
        }

        test("test get latest egenmeldt sykmeldingstatus") {
            val apenStatus = getStatus(StatusEventDTO.APEN.name, OffsetDateTime.now().minusDays(1))
            TestDB.database.insertStatus(
                "1",
                getStatus(StatusEventDTO.APEN.name, OffsetDateTime.now().minusDays(1))
            )
            TestDB.database.insertBehandlingsutfall("1", getBehandlingsutfall(RegelStatusDTO.OK))
            TestDB.database.insertSymelding("1", "fnr", getSykmelding().copy(egenmeldt = true))

            val status = database.getLatestStatus("1", "fnr")
            status.statusEvent.name shouldBeEqualTo apenStatus.statusEvent
            status.erAvvist shouldBeEqualTo false
            status.erEgenmeldt shouldBeEqualTo true
        }

        test("update and get latest sykmeldingstatus SENDT") {
            TestDB.database.insertStatus(
                "1",
                getStatus(StatusEventDTO.APEN.name, OffsetDateTime.now().minusDays(1))
            )
            TestDB.database.insertBehandlingsutfall("1", getBehandlingsutfall(RegelStatusDTO.OK))
            TestDB.database.insertSymelding("1", "fnr", getSykmelding())
            TestDB.database.insertStatus(
                "1",
                getStatus(StatusEventDTO.SENDT.name, OffsetDateTime.now())
            )
            val status = database.getLatestStatus("1", "fnr")
            status.statusEvent.name shouldBeEqualTo "SENDT"
            status.erAvvist shouldBeEqualTo false
            status.erEgenmeldt shouldBeEqualTo false
        }
        test("update and get latest sykmeldingstatus BEKREFTET") {
            TestDB.database.insertStatus(
                "1",
                getStatus(StatusEventDTO.APEN.name, OffsetDateTime.now().minusDays(1))
            )
            TestDB.database.insertBehandlingsutfall("1", getBehandlingsutfall(RegelStatusDTO.OK))
            TestDB.database.insertSymelding("1", "fnr", getSykmelding())
            TestDB.database.insertStatus(
                "1",
                getStatus(StatusEventDTO.BEKREFTET.name, OffsetDateTime.now().minusHours(2))
            )
            val status = database.getLatestStatus("1", "fnr")
            status.statusEvent.name shouldBeEqualTo "BEKREFTET"
            status.erAvvist shouldBeEqualTo false
            status.erEgenmeldt shouldBeEqualTo false
        }
    })

private fun getSykmeldingStatusEvent() =
    SykmeldingStatusKafkaEventDTO(
        sykmeldingId = "1",
        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
        statusEvent = StatusEventDTO.SENDT.name,
        arbeidsgiver = null,
        sporsmals = null,
    )
