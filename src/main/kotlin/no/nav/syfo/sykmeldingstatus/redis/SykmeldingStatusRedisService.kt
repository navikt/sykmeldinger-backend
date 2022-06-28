package no.nav.syfo.sykmeldingstatus.redis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.jedisObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

class SykmeldingStatusRedisService(private val jedisPool: JedisPool, private val redisSecret: String) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SykmeldingStatusRedisService::class.java)
        private const val redisTimeoutSeconds: Long = 180
    }

    suspend fun updateStatus(sykmeldingStatusEventDTO: SykmeldingStatusRedisModel, sykmeldingId: String) {
        withContext(Dispatchers.IO) {
            var jedis: Jedis? = null
            try {
                jedis = jedisPool.resource
                jedis.auth(redisSecret)
                jedis.setex(
                    sykmeldingId,
                    redisTimeoutSeconds,
                    jedisObjectMapper.writeValueAsString(sykmeldingStatusEventDTO)
                )
            } catch (ex: Exception) {
                log.error("Could not update redis for sykmeldingId {}", sykmeldingId, ex)
            } finally {
                jedis?.close()
            }
        }
    }

    suspend fun getStatus(sykmeldingId: String): SykmeldingStatusRedisModel? = withContext(Dispatchers.IO) {
        var jedis: Jedis? = null
        try {
            jedis = jedisPool.resource
            jedis.auth(redisSecret)
            when (val stringValue = jedis.get(sykmeldingId)) {
                null -> null
                else -> jedisObjectMapper.readValue(stringValue, SykmeldingStatusRedisModel::class.java)
            }
        } catch (ex: Exception) {
            log.error("Could not get redis for sykmeldingId {}", sykmeldingId, ex)
            null
        } finally {
            jedis?.close()
        }
    }
}
