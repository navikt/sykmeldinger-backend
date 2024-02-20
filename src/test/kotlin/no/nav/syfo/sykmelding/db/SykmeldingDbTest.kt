package no.nav.syfo.sykmelding.db

import io.ktor.server.testing.*
import java.time.OffsetDateTime
import java.util.*
import no.nav.syfo.sykmelding.model.RegelStatusDTO
import no.nav.syfo.sykmelding.model.ShortNameDTO
import no.nav.syfo.sykmelding.model.SporsmalDTO
import no.nav.syfo.sykmelding.model.SvarDTO
import no.nav.syfo.sykmelding.model.SvartypeDTO
import no.nav.syfo.sykmelding.model.UtenlandskSykmelding
import no.nav.syfo.sykmeldingstatus.TestHelper.Companion.januar
import no.nav.syfo.sykmeldingstatus.api.v1.ArbeidsgiverStatusDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.testutils.TestDB
import no.nav.syfo.testutils.getBehandlingsutfall
import no.nav.syfo.testutils.getStatus
import no.nav.syfo.testutils.getSykmelding
import no.nav.syfo.testutils.insertBehandlingsutfall
import no.nav.syfo.testutils.insertStatus
import no.nav.syfo.testutils.insertSykmeldt
import no.nav.syfo.testutils.insertSymelding
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SykmeldingDbTest {
    val testDb = TestDB.database
    val fnr = "12345678901"
    val sykmeldingId = UUID.randomUUID().toString()
    val sykmeldingDb = SykmeldingDb(testDb)

    @BeforeEach
    fun init() {
        TestDB.clearAllData()
    }

    @Test
    fun `Les sykmelding med status og behandlingsutfall`() = testApplication {
        testDb.insertSymelding(sykmeldingId, fnr, getSykmelding())
        testDb.insertStatus(
            sykmeldingId,
            getStatus(StatusEventDTO.SENDT.name, OffsetDateTime.now()),
        )
        testDb.insertStatus(
            sykmeldingId,
            getStatus(
                StatusEventDTO.APEN.name,
                OffsetDateTime.now().minusDays(1),
            ),
        )
        testDb.insertBehandlingsutfall(
            sykmeldingId,
            getBehandlingsutfall(RegelStatusDTO.OK),
        )
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

    @Test
    fun `henter ikke sykmeldinger for pasienter som ikke er registrert i sykmeldt`() =
        testApplication {
            testDb.insertSymelding(sykmeldingId, fnr, getSykmelding())
            testDb.insertStatus(
                sykmeldingId,
                getStatus(StatusEventDTO.SENDT.name, OffsetDateTime.now()),
            )
            testDb.insertStatus(
                sykmeldingId,
                getStatus(
                    StatusEventDTO.APEN.name,
                    OffsetDateTime.now().minusDays(1),
                ),
            )
            testDb.insertBehandlingsutfall(
                sykmeldingId,
                getBehandlingsutfall(RegelStatusDTO.OK),
            )

            val sykmeldinger = sykmeldingDb.getSykmeldinger(fnr)
            sykmeldinger.size shouldBeEqualTo 0
        }

    @Test
    fun `henter ikke sykmeldinger for pasienter som ikke er registrert i med behandlingsutfall`() =
        testApplication {
            testDb.insertSymelding(sykmeldingId, fnr, getSykmelding())
            testDb.insertStatus(
                sykmeldingId,
                getStatus(StatusEventDTO.SENDT.name, OffsetDateTime.now()),
            )
            testDb.insertStatus(
                sykmeldingId,
                getStatus(
                    StatusEventDTO.APEN.name,
                    OffsetDateTime.now().minusDays(1),
                ),
            )
            testDb.insertSykmeldt(fnr)
            val sykmeldinger = sykmeldingDb.getSykmeldinger(fnr)
            sykmeldinger.size shouldBeEqualTo 0
        }

    @Test
    fun `henter ikke sykmeldinger for pasienter som ikke er registrert med sykmeldingstatus`() =
        testApplication {
            testDb.insertSymelding(sykmeldingId, fnr, getSykmelding())
            testDb.insertSykmeldt(fnr)
            testDb.insertBehandlingsutfall(
                sykmeldingId,
                getBehandlingsutfall(RegelStatusDTO.OK),
            )
            val sykmeldinger = sykmeldingDb.getSykmeldinger(fnr)
            sykmeldinger.size shouldBeEqualTo 0
        }

    @Test
    fun `henter flere sykmeldinger`() = testApplication {
        (0..10).forEach {
            val id = UUID.randomUUID().toString()
            testDb.insertSymelding(id, fnr, getSykmelding())
            testDb.insertStatus(
                id,
                getStatus(
                    StatusEventDTO.APEN.name,
                    OffsetDateTime.now().minusDays(1),
                ),
            )
            testDb.insertBehandlingsutfall(
                id,
                getBehandlingsutfall(RegelStatusDTO.OK),
            )
        }
        testDb.insertSykmeldt(fnr)

        val sykmeldinger = sykmeldingDb.getSykmeldinger(fnr)
        sykmeldinger.size shouldBeEqualTo 11
    }

    @Test
    fun `hente nyeste status for sykmelding`() = testApplication {
        val timestamp = OffsetDateTime.now()
        testDb.insertSymelding(sykmeldingId, fnr, getSykmelding())
        testDb.insertStatus(
            sykmeldingId,
            getStatus(StatusEventDTO.APEN.name, timestamp),
        )
        testDb.insertBehandlingsutfall(
            sykmeldingId,
            getBehandlingsutfall(RegelStatusDTO.OK),
        )
        testDb.insertSykmeldt(fnr)

        val status = sykmeldingDb.getSykmeldinger(fnr).first().sykmeldingStatus
        status.statusEvent shouldBeEqualTo StatusEventDTO.APEN.name
        status.arbeidsgiver shouldBeEqualTo null
        status.sporsmalOgSvarListe shouldBeEqualTo emptyList()

        val arbeidsgiver = ArbeidsgiverStatusDTO("orgnummer", "juridiskOrgnummer", "orgNavn")
        val sporsmalOgSvar =
            listOf(
                SporsmalDTO(
                    tekst = "tekst",
                    shortName = ShortNameDTO.ARBEIDSSITUASJON,
                    svar = SvarDTO(SvartypeDTO.ARBEIDSSITUASJON, ""),
                ),
            )

        testDb.insertStatus(
            sykmeldingId,
            getStatus(
                StatusEventDTO.SENDT.name,
                timestamp.plusSeconds(1),
                arbeidsgiver,
                sporsmalOgSvar,
            ),
        )

        val sendtStatus = sykmeldingDb.getSykmeldinger(fnr).first().sykmeldingStatus
        sendtStatus.statusEvent shouldBeEqualTo StatusEventDTO.SENDT.name
        sendtStatus.arbeidsgiver shouldBeEqualTo arbeidsgiver
        sendtStatus.sporsmalOgSvarListe shouldBeEqualTo sporsmalOgSvar
    }

    @Test
    fun `hent sykmelding med sykmeldingId`() = testApplication {
        testDb.insertSymelding(sykmeldingId, fnr, getSykmelding())
        testDb.insertStatus(
            sykmeldingId,
            getStatus(
                StatusEventDTO.APEN.name,
                OffsetDateTime.now().minusDays(1),
            ),
        )
        testDb.insertBehandlingsutfall(
            sykmeldingId,
            getBehandlingsutfall(RegelStatusDTO.OK),
        )
        testDb.insertSykmeldt(fnr)

        val sykmelding = sykmeldingDb.getSykmelding(sykmeldingId, fnr)!!
        sykmelding.sykmeldingStatus.statusEvent shouldBeEqualTo StatusEventDTO.APEN.name
        sykmelding.pasient.fornavn shouldBeEqualTo "fornavn"
        sykmelding.pasient.etternavn shouldBeEqualTo "etternavn"
        sykmelding.pasient.fnr shouldBeEqualTo fnr
        sykmelding.pasient.mellomnavn shouldBeEqualTo "mellomnavn"
    }

    @Test
    fun `henter ikke sykmelding med sykmeldingId men feil fnr`() = testApplication {
        testDb.insertSymelding(sykmeldingId, fnr, getSykmelding())
        testDb.insertStatus(
            sykmeldingId,
            getStatus(
                StatusEventDTO.APEN.name,
                OffsetDateTime.now().minusDays(1),
            ),
        )
        testDb.insertBehandlingsutfall(
            sykmeldingId,
            getBehandlingsutfall(RegelStatusDTO.OK),
        )
        testDb.insertSykmeldt(fnr)
        val sykmelding = sykmeldingDb.getSykmelding(sykmeldingId, "feil")
        sykmelding shouldBeEqualTo null
    }

    @Test
    fun `henter ikke sykmelding med manglende status`() = testApplication {
        testDb.insertSymelding(sykmeldingId, fnr, getSykmelding())
        testDb.insertBehandlingsutfall(
            sykmeldingId,
            getBehandlingsutfall(RegelStatusDTO.OK),
        )
        testDb.insertSykmeldt(fnr)

        sykmeldingDb.getSykmelding(sykmeldingId, fnr) shouldBeEqualTo null
    }

    @Test
    fun `henter ikke sykmelding med manglende behandlingsutfall`() = testApplication {
        testDb.insertSymelding(sykmeldingId, fnr, getSykmelding())
        testDb.insertStatus(
            sykmeldingId,
            getStatus(
                StatusEventDTO.APEN.name,
                OffsetDateTime.now().minusDays(1),
            ),
        )
        testDb.insertSykmeldt(fnr)

        sykmeldingDb.getSykmelding(sykmeldingId, fnr) shouldBeEqualTo null
    }

    @Test
    fun `henter ikke sykmelding med manglende sykmeldt`() = testApplication {
        testDb.insertSymelding(sykmeldingId, fnr, getSykmelding())
        testDb.insertStatus(
            sykmeldingId,
            getStatus(
                StatusEventDTO.APEN.name,
                OffsetDateTime.now().minusDays(1),
            ),
        )
        testDb.insertBehandlingsutfall(
            sykmeldingId,
            getBehandlingsutfall(RegelStatusDTO.OK),
        )

        sykmeldingDb.getSykmelding(sykmeldingId, fnr) shouldBeEqualTo null
    }

    @Test
    fun `UtenlandskSykelding`() = testApplication {
        testDb.insertSymelding(
            sykmeldingId,
            fnr,
            getSykmelding().copy(utenlandskSykmelding = UtenlandskSykmelding("Danmark")),
        )
        testDb.insertStatus(
            sykmeldingId,
            getStatus(
                StatusEventDTO.APEN.name,
                OffsetDateTime.now().minusDays(1),
            ),
        )
        testDb.insertBehandlingsutfall(
            sykmeldingId,
            getBehandlingsutfall(RegelStatusDTO.OK),
        )
        testDb.insertSykmeldt(fnr)

        val sykmelding = sykmeldingDb.getSykmelding(sykmeldingId, fnr)!!
        sykmelding.utenlandskSykmelding shouldBeEqualTo UtenlandskSykmelding("Danmark")
    }

    @Test
    fun `Sykmeldt over 70`() = testApplication {
        testDb.insertSymelding(
            sykmeldingId,
            fnr,
            getSykmelding(),
        )
        testDb.insertStatus(
            sykmeldingId,
            getStatus(
                StatusEventDTO.APEN.name,
                OffsetDateTime.now().minusDays(1),
            ),
        )
        testDb.insertBehandlingsutfall(
            sykmeldingId,
            getBehandlingsutfall(RegelStatusDTO.OK),
        )
        testDb.insertSykmeldt(fnr, 1.januar(1954))

        val sykmelding = sykmeldingDb.getSykmelding(sykmeldingId, fnr)!!
        sykmelding.pasient.overSyttiAar shouldBeEqualTo true
    }

    @Test
    fun `Sykmeldt under 70`() = testApplication {
        testDb.insertSymelding(
            sykmeldingId,
            fnr,
            getSykmelding(),
        )
        testDb.insertStatus(
            sykmeldingId,
            getStatus(
                StatusEventDTO.APEN.name,
                OffsetDateTime.now().minusDays(1),
            ),
        )
        testDb.insertBehandlingsutfall(
            sykmeldingId,
            getBehandlingsutfall(RegelStatusDTO.OK),
        )
        testDb.insertSykmeldt(fnr, 1.januar(2000))

        val sykmelding = sykmeldingDb.getSykmelding(sykmeldingId, fnr)!!
        sykmelding.pasient.overSyttiAar shouldBeEqualTo false
    }
}
