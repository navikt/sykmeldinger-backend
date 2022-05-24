package no.nav.syfo.sykmeldingstatus.redis

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.application.JedisConfig
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import redis.clients.jedis.JedisPool
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SykmeldingStatusRedisServiceTest : FunSpec({

    val redisContainer: GenericContainer<Nothing> = GenericContainer("navikt/secure-redis:5.0.3-alpine-2")
    redisContainer.withExposedPorts(6379)
    redisContainer.withEnv("REDIS_PASSWORD", "secret")
    redisContainer.withClasspathResourceMapping(
        "redis.env",
        "/var/run/secrets/nais.io/vault/redis.env",
        BindMode.READ_ONLY
    )

    redisContainer.start()
    val jedisPool = JedisPool(JedisConfig(), redisContainer.containerIpAddress, redisContainer.getMappedPort(6379))
    val sykmeldingStatusRedisService = SykmeldingStatusRedisService(jedisPool, "secret")

    afterSpec {
        redisContainer.stop()
    }

    context("SykmeldingStatusRedisService") {
        test("Should update status in redis") {
            val status = SykmeldingStatusRedisModel(
                OffsetDateTime.now(ZoneOffset.UTC),
                StatusEventDTO.APEN,
                null,
                null,
                erAvvist = false,
                erEgenmeldt = false
            )
            sykmeldingStatusRedisService.updateStatus(status, "123")
            val redisStatus = sykmeldingStatusRedisService.getStatus("123")
            redisStatus shouldBeEqualTo status
        }

        test("Get sykmelding status empty") {
            val status = sykmeldingStatusRedisService.getStatus("1234")
            status shouldBe null
        }
    }
})
