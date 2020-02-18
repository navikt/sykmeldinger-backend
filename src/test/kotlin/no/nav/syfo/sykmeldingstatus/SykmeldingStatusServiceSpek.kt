package no.nav.syfo.sykmeldingstatus

import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockkClass
import io.mockk.verify
import java.time.ZonedDateTime
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

    val sykmeldingStatusKafkaProducer = mockkClass(SykmeldingStatusKafkaProducer::class)
    val sykmeldingStatusJedisService = mockkClass(SykmeldingStatusRedisService::class)
    val sykmeldingStatusService = SykmeldingStatusService(sykmeldingStatusKafkaProducer, sykmeldingStatusJedisService)

    beforeEachTest {
        clearAllMocks()
        every { sykmeldingStatusKafkaProducer.send(any(), any()) } just Runs
        every { sykmeldingStatusJedisService.updateStatus(any(), any()) } just Runs
    }

    describe("Test av at SykmeldingStatusService skriver til kafka, redis og sjekker tilgang (etterhvert)") {
        it("registrerStatus legger melding på kafka og oppdaterer redis") {
            val timestamp = ZonedDateTime.now()
            sykmeldingStatusService.registrerStatus(SykmeldingStatusEventDTO(StatusEventDTO.AVBRUTT, timestamp), sykmeldingId, "syfoservice")

            verify { sykmeldingStatusKafkaProducer.send(any(), eq("syfoservice")) }
            verify { sykmeldingStatusJedisService.updateStatus(any(), sykmeldingId) }
        }

        it("registrerSendt legger melding på kafka og oppdaterer redis") {
            sykmeldingStatusService.registrerSendt(opprettSykmeldingSendEventDTO(), sykmeldingId, "syfoservice")

            verify { sykmeldingStatusKafkaProducer.send(any(), eq("syfoservice")) }
            verify { sykmeldingStatusJedisService.updateStatus(any(), sykmeldingId) }
        }

        it("registrerBekreftet legger melding på kafka og oppdaterer redis") {
            sykmeldingStatusService.registrerBekreftet(opprettSykmeldingBekreftEventDTO(), sykmeldingId, "syfoservice")

            verify { sykmeldingStatusKafkaProducer.send(any(), eq("syfoservice")) }
            verify { sykmeldingStatusJedisService.updateStatus(any(), sykmeldingId) }
        }

        it("registrerBekreftet sjekker tilgang, legger melding på kafka og oppdaterer redis") {
            sykmeldingStatusService.registrerBekreftet(opprettSykmeldingBekreftEventDTO(), sykmeldingId, "user")

            // verifiser tilgangskontroll-sjekk
            verify { sykmeldingStatusKafkaProducer.send(any(), eq("user")) }
            verify { sykmeldingStatusJedisService.updateStatus(any(), sykmeldingId) }
        }
    }
})
