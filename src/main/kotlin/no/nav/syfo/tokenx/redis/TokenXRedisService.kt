package no.nav.syfo.tokenx.redis

import no.nav.syfo.application.jedisObjectMapper
import no.nav.syfo.tokenx.AccessTokenMedExpiry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

class TokenXRedisService(private val jedisPool: JedisPool, private val redisSecret: String) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(TokenXRedisService::class.java)
        private const val redisTimeoutSeconds: Long = 780
        private const val prefix = "TOKENX"
    }

    fun updateToken(key: String, accessToken: AccessTokenMedExpiry) {
        var jedis: Jedis? = null
        try {
            jedis = jedisPool.resource
            jedis.auth(redisSecret)
            jedis.setex("${prefix}$key", redisTimeoutSeconds, jedisObjectMapper.writeValueAsString(accessToken))
        } catch (ex: Exception) {
            log.error("Could not update redis for tokenx {}", ex.message)
        } finally {
            jedis?.close()
        }
    }

    fun getToken(key: String): AccessTokenMedExpiry? {
        var jedis: Jedis? = null
        return try {
            jedis = jedisPool.resource
            jedis.auth(redisSecret)
            when (val stringValue = jedis.get("${prefix}$key")) {
                null -> null
                else -> jedisObjectMapper.readValue(stringValue, AccessTokenMedExpiry::class.java)
            }
        } catch (ex: Exception) {
            log.error("Could not get redis for tokenx", ex.message)
            null
        } finally {
            jedis?.close()
        }
    }
}
