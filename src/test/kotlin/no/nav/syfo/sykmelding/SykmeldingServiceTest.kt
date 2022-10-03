package no.nav.syfo.sykmelding

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockkClass
import no.nav.syfo.arbeidsgivere.service.getPdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.client.SyfosmregisterSykmeldingClient
import no.nav.syfo.sykmelding.model.SykmeldingStatusDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.db.SykmeldingStatusDb
import no.nav.syfo.sykmeldingstatus.getSykmeldingDTO
import no.nav.syfo.sykmeldingstatus.getSykmeldingModel
import no.nav.syfo.sykmeldingstatus.getSykmeldingStatusDbModel
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SykmeldingServiceTest : FunSpec({
    val sykmeldingStatusDb = mockkClass(SykmeldingStatusDb::class)
    val syfosmregisterSykmeldingClient = mockkClass(SyfosmregisterSykmeldingClient::class)
    val pdlPersonService = mockkClass(PdlPersonService::class)
    val sykmeldingService = SykmeldingService(
        pdlPersonService,
        syfosmregisterSykmeldingClient,
        sykmeldingStatusDb
    )

    beforeTest {
        clearAllMocks()
    }

    context("Get Sykmeldinger and latest status") {
        test("Get sykmeldinger") {
            val now = OffsetDateTime.now()
            val expected = getSykmeldingDTO(timestamps = now)
            val sykmelding = getSykmeldingModel(timestamps = now)
            coEvery { syfosmregisterSykmeldingClient.getSykmeldingerTokenX("token", null) } returns listOf(sykmelding)
            coEvery { pdlPersonService.getPerson(any(), any(), any()) } returns getPdlPerson()
            coEvery { sykmeldingStatusDb.getLatesSykmeldingStatus(any()) } returns getSykmeldingStatusDbModel(StatusEventDTO.SENDT, now)

            val returndSykmelding = sykmeldingService.hentSykmeldinger("12345678901", "token", null)
            returndSykmelding shouldBeEqualTo listOf(expected)
        }
        test("Get sykmeldinger with newest status from db") {
            val now = OffsetDateTime.now()
            val sykmelding = getSykmeldingModel(
                timestamps = now,
            )
            val statusFromDb = getSykmeldingStatusDbModel(
                StatusEventDTO.SENDT, OffsetDateTime.now(ZoneOffset.UTC)
            )
            coEvery { pdlPersonService.getPerson(any(), any(), any()) } returns getPdlPerson()
            coEvery { syfosmregisterSykmeldingClient.getSykmeldingerTokenX("token", null) } returns listOf(sykmelding)
            coEvery { sykmeldingStatusDb.getLatesSykmeldingStatus(any()) } returns statusFromDb

            val returndSykmelding = sykmeldingService.hentSykmeldinger("fnr", "token", null)
            returndSykmelding shouldNotBeEqualTo listOf(sykmelding)
            returndSykmelding[0].sykmeldingStatus shouldBeEqualTo SykmeldingStatusDTO(
                timestamp = statusFromDb.timestamp,
                statusEvent = statusFromDb.statusEvent,
                arbeidsgiver = null,
                sporsmalOgSvarListe = emptyList()
            )
        }
    }
})
