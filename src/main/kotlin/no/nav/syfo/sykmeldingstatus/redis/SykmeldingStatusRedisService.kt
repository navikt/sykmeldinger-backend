package no.nav.syfo.sykmeldingstatus.redis

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

class SykmeldingStatusRedisService(private val jedisPool: JedisPool) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SykmeldingStatusRedisService::class.java)
        private const val redisTimeoutSeconds: Int = 60
    }

    fun updateStatus(sykmeldingStatusEventDTO: SykmeldingStatusRedisModel, sykmeldingId: String) {
        var jedis: Jedis? = null
        try {
            jedis = jedisPool.resource
            jedis.setex(sykmeldingId, redisTimeoutSeconds, objectMapper.writeValueAsString(sykmeldingStatusEventDTO))
        } catch (ex: Exception) {
            log.error("Could not update redis for sykmeldingId {}", sykmeldingId, ex)
        } finally {
            jedis?.close()
        }
    }

    fun getStatus(sykmeldingId: String): SykmeldingStatusRedisModel? {
        var jedis: Jedis? = null
        return try {
            jedis = jedisPool.resource
            when (val stringValue = jedis.get(sykmeldingId)) {
                null -> null
                else -> objectMapper.readValue(stringValue, SykmeldingStatusRedisModel::class.java)
            }
        } catch (ex: Exception) {
            log.error("Could not get redis for sykmeldingId {}", sykmeldingId, ex)
            null
        } finally {
            jedis?.close()
        }
    }
}
