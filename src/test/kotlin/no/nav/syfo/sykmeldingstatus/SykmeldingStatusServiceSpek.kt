package no.nav.syfo.sykmeldingstatus

import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockkClass
import io.mockk.verify
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.sykmeldingstatus.api.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.opprettSykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.opprettSykmeldingSendEventDTO
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisService
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SykmeldingStatusServiceSpek : Spek({
    val sykmeldingId = "id"
    val fnr = "fnr"

    val sykmeldingStatusKafkaProducer = mockkClass(SykmeldingStatusKafkaProducer::class)
    val sykmeldingStatusJedisService = mockkClass(SykmeldingStatusRedisService::class)
    val sykmeldingStatusService = SykmeldingStatusService(sykmeldingStatusKafkaProducer, sykmeldingStatusJedisService)

    beforeEachTest {
        clearAllMocks()
        every { sykmeldingStatusKafkaProducer.send(any(), any(), any()) } just Runs
        every { sykmeldingStatusJedisService.updateStatus(any(), any()) } just Runs
    }

    describe("Test av at SykmeldingStatusService skriver til kafka og redis") {
        it("registrerStatus legger melding på kafka og oppdaterer redis") {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            sykmeldingStatusService.registrerStatus(SykmeldingStatusEventDTO(StatusEventDTO.AVBRUTT, timestamp), sykmeldingId, "syfoservice", fnr)

            verify { sykmeldingStatusKafkaProducer.send(any(), eq("syfoservice"), fnr) }
            verify { sykmeldingStatusJedisService.updateStatus(any(), sykmeldingId) }
        }

        it("registrerSendt legger melding på kafka og oppdaterer redis") {
            sykmeldingStatusService.registrerSendt(opprettSykmeldingSendEventDTO(), sykmeldingId, "syfoservice", fnr)

            verify { sykmeldingStatusKafkaProducer.send(any(), eq("syfoservice"), fnr) }
            verify { sykmeldingStatusJedisService.updateStatus(any(), sykmeldingId) }
        }

        it("registrerBekreftet legger melding på kafka og oppdaterer redis") {
            sykmeldingStatusService.registrerBekreftet(opprettSykmeldingBekreftEventDTO(), sykmeldingId, "syfoservice", fnr)

            verify { sykmeldingStatusKafkaProducer.send(any(), eq("syfoservice"), fnr) }
            verify { sykmeldingStatusJedisService.updateStatus(any(), sykmeldingId) }
        }
    }
})
