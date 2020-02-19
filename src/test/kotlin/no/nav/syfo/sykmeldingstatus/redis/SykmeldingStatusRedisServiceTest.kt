package no.nav.syfo.sykmeldingstatus.redis

import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.sykmeldingstatus.api.StatusEventDTO
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.testcontainers.containers.GenericContainer
import redis.clients.jedis.JedisPool

class SykmeldingStatusRedisServiceTest : Spek({

    val redisContainer: GenericContainer<Nothing> = GenericContainer("redis:5-alpine")
    redisContainer.withExposedPorts(6379)

    redisContainer.start()
    val jedisPool = JedisPool(JedisConfig(), redisContainer.containerIpAddress, redisContainer.getMappedPort(6379))
    val sykmeldingStatusRedisService = SykmeldingStatusRedisService(jedisPool)

    describe("SykmeldingStatusRedisService") {
        it("Should update status in redis") {
            val status = SykmeldingStatusRedisModel(OffsetDateTime.now(ZoneOffset.UTC), StatusEventDTO.APEN, null, null)
            sykmeldingStatusRedisService.updateStatus(status, "123")
            val redisStatus = sykmeldingStatusRedisService.getStatus("123")
            redisStatus shouldEqual status
        }

        it("Get sykmelding status empty") {
            val status = sykmeldingStatusRedisService.getStatus("1234")
            status shouldBe null
        }
    }
})
