package no.nav.syfo.arbeidsgivere.redis

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.jedisObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

class ArbeidsgiverRedisService(private val jedisPool: JedisPool, private val redisSecret: String) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ArbeidsgiverRedisService::class.java)
        private const val redisTimeoutSeconds: Int = 60
        private const val prefix = "ARB"
    }

    fun updateArbeidsgivere(arbeidsgiverinfoRedisModelListe: List<ArbeidsgiverinfoRedisModel>, fnr: String) {
        var jedis: Jedis? = null
        try {
            jedis = jedisPool.resource
            jedis.auth(redisSecret)
            jedis.setex("$prefix$fnr", redisTimeoutSeconds, jedisObjectMapper.writeValueAsString(arbeidsgiverinfoRedisModelListe))
        } catch (ex: Exception) {
            log.error("Could not update redis for arbeidsgivere {}", ex.message)
        } finally {
            jedis?.close()
        }
    }

    fun getArbeidsgivere(fnr: String): List<ArbeidsgiverinfoRedisModel>? {
        var jedis: Jedis? = null
        return try {
            jedis = jedisPool.resource
            jedis.auth(redisSecret)
            when (val stringValue = jedis.get("$prefix$fnr")) {
                null -> null
                else -> jedisObjectMapper.readValue<List<ArbeidsgiverinfoRedisModel>>(stringValue)
            }
        } catch (ex: Exception) {
            log.error("Could not get redis for arbeidsgivere", ex.message)
            null
        } finally {
            jedis?.close()
        }
    }
}
