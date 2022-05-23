package no.nav.syfo.sykmelding

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
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
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SykmeldingServiceTest : Spek({
    val sykmeldingStatusRedisService = mockkClass(SykmeldingStatusRedisService::class)
    val syfosmregisterSykmeldingClient = mockkClass(SyfosmregisterSykmeldingClient::class)
    val pdlPersonService = mockkClass(PdlPersonService::class)
    val sykmeldingService = SykmeldingService(syfosmregisterSykmeldingClient, sykmeldingStatusRedisService, pdlPersonService)

    beforeEachTest {
        clearAllMocks()
    }

    describe("Get Sykmeldinger and latest status") {
        it("Get sykmeldinger") {
            val now = OffsetDateTime.now()
            val expected = getSykmeldingDTO(timestamps = now)
            val sykmelding = getSykmeldingModel(timestamps = now)
            coEvery { syfosmregisterSykmeldingClient.getSykmeldinger("token", null) } returns listOf(sykmelding)
            coEvery { pdlPersonService.getPerson(any(), any(), any(), any()) } returns getPdlPerson()
            every { sykmeldingStatusRedisService.getStatus(any()) } returns null
            runBlocking {
                val returndSykmelding = sykmeldingService.hentSykmeldinger("12345678901", "token", null)
                returndSykmelding shouldBeEqualTo listOf(expected)
            }
        }
        it("Get sykmeldinger with newest status from redis") {
            val now = OffsetDateTime.now()
            val sykmelding = getSykmeldingModel(
                timestamps = now,
            )
            val statusFromRedis = getSykmeldingStatusRedisModel(
                StatusEventDTO.SENDT, OffsetDateTime.now(ZoneOffset.UTC)
            )
            coEvery { pdlPersonService.getPerson(any(), any(), any(), any()) } returns getPdlPerson()
            coEvery { syfosmregisterSykmeldingClient.getSykmeldinger("token", null) } returns listOf(sykmelding)
            every { sykmeldingStatusRedisService.getStatus(any()) } returns statusFromRedis
            runBlocking {
                val returndSykmelding = sykmeldingService.hentSykmeldinger("fnr", "token", null)
                returndSykmelding shouldNotBeEqualTo listOf(sykmelding)
                returndSykmelding[0].sykmeldingStatus shouldBeEqualTo SykmeldingStatusDTO(
                    timestamp = statusFromRedis.timestamp,
                    statusEvent = statusFromRedis.statusEvent.name,
                    arbeidsgiver = null,
                    sporsmalOgSvarListe = emptyList()
                )
            }
        }
    }
})
