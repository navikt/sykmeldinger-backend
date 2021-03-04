package no.nav.syfo.application

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import redis.clients.jedis.JedisPoolConfig

class JedisConfig : JedisPoolConfig() {
    init {
        testWhileIdle = true
        minEvictableIdleTimeMillis = 300000
        timeBetweenEvictionRunsMillis = 60000
        numTestsPerEvictionRun = -1
        maxTotal = 20
        maxIdle = 20
        maxWaitMillis = 1000
        minIdle = 10
        blockWhenExhausted = true
    }
}

val jedisObjectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    setSerializationInclusion(JsonInclude.Include.NON_NULL)
}
