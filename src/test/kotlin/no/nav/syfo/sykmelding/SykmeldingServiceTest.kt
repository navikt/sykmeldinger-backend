package no.nav.syfo.sykmelding

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import no.nav.syfo.arbeidsgivere.service.getPdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.client.SyfosmregisterSykmeldingClient
import no.nav.syfo.sykmelding.model.SykmeldingStatusDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.getSykmeldingDTO
import no.nav.syfo.sykmeldingstatus.getSykmeldingModel
import no.nav.syfo.sykmeldingstatus.getSykmeldingStatusRedisModel
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisService
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.sykmelding.db.SykmeldingDb

class SykmeldingServiceTest : FunSpec({
    val sykmeldingStatusRedisService = mockkClass(SykmeldingStatusRedisService::class)
    val syfosmregisterSykmeldingClient = mockkClass(SyfosmregisterSykmeldingClient::class)
    val pdlPersonService = mockkClass(PdlPersonService::class)

    val sykmeldingDb = mockk<SykmeldingDb>()

    val sykmeldingService = SykmeldingService(
        sykmeldingDb
    )

    beforeTest {
        clearAllMocks()
    }

    context("Get Sykmeldinger and latest status") {
        test("Get sykmeldinger") {
            val now = OffsetDateTime.now()
            val expected = getSykmeldingDTO(timestamps = now)

            coEvery { sykmeldingDb.getSykmeldinger(any()) } returns listOf(expected)
            val returndSykmelding = sykmeldingService.hentSykmeldinger("12345678901")
            returndSykmelding shouldBeEqualTo listOf(expected)
        }
    }
})
