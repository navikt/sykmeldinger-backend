package no.nav.syfo.sykmelding

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkClass
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import no.nav.syfo.sykmelding.client.SyfosmregisterSykmeldingClient
import no.nav.syfo.sykmelding.model.SykmeldingStatusDTO
import no.nav.syfo.sykmeldingstatus.api.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.getSykmeldingModel
import no.nav.syfo.sykmeldingstatus.getSykmeldingStatusRedisModel
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisService
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SykmeldingServiceTest : Spek({
    val sykmeldingStatusRedisService = mockkClass(SykmeldingStatusRedisService::class)
    val syfosmregisterSykmeldingClient = mockkClass(SyfosmregisterSykmeldingClient::class)

    val sykmeldingService = SykmeldingService(syfosmregisterSykmeldingClient, sykmeldingStatusRedisService)

    describe("Get Sykmeldinger and latest status") {
        it("Get sykmeldinger") {
            val sykmelding = getSykmeldingModel()
            coEvery { syfosmregisterSykmeldingClient.getSykmeldinger("token", null) } returns listOf(sykmelding)
            every { sykmeldingStatusRedisService.getStatus(any()) } returns null
            runBlocking {
                val returndSykmelding = sykmeldingService.hentSykmeldinger("token", null)
                returndSykmelding shouldEqual listOf(sykmelding)
            }
        }
        it("Get sykmeldinger with newest status from redis") {
            val sykmelding = getSykmeldingModel(SykmeldingStatusDTO(
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1), statusEvent = StatusEventDTO.APEN.name, arbeidsgiver = null, sporsmalOgSvarListe = emptyList()
            ))
            val statusFromRedis = getSykmeldingStatusRedisModel(
                    StatusEventDTO.SENDT, OffsetDateTime.now(ZoneOffset.UTC)
            )
            coEvery { syfosmregisterSykmeldingClient.getSykmeldinger("token", null) } returns listOf(sykmelding)
            every { sykmeldingStatusRedisService.getStatus(any()) } returns statusFromRedis
            runBlocking {
                val returndSykmelding = sykmeldingService.hentSykmeldinger("token", null)
                returndSykmelding shouldNotEqual listOf(sykmelding)
                returndSykmelding[0].sykmeldingStatus shouldEqual SykmeldingStatusDTO(
                        timestamp = statusFromRedis.timestamp, statusEvent = statusFromRedis.statusEvent.name, arbeidsgiver = null, sporsmalOgSvarListe = emptyList()
                )
            }
        }
    }
})
