package no.nav.syfo.sykmeldingstatus.redis

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.db.SykmeldingStatusDb
import no.nav.syfo.testutils.TestDb
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SykmeldingStatusRedisServiceTest : FunSpec({

    val sykmeldingStatusDb = SykmeldingStatusDb(
        TestDb.database
    )

    context("SykmeldingStatusRedisService") {
        test("Should update status in redis") {
            val status = SykmeldingStatusKafkaEventDTO(
                sykmeldingId = "123",
                statusEvent = StatusEventDTO.APEN.name,
                timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            )
            sykmeldingStatusDb.updateSykmeldingStatus(status)
            val statusFromDb = sykmeldingStatusDb.getLatesSykmeldingStatus("123")
            statusFromDb?.arbeidsgiver shouldBeEqualTo status.arbeidsgiver
            statusFromDb?.sporsmals shouldBeEqualTo status.sporsmals
            statusFromDb?.statusEvent shouldBeEqualTo status.statusEvent
        }

        test("Get sykmelding status empty") {
            val status = sykmeldingStatusDb.getLatesSykmeldingStatus("1234")
            status shouldBe null
        }
    }
})
