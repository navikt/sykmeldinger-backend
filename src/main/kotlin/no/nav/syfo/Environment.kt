package no.nav.syfo

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "sykmeldinger-backend"),
    val jwtIssuer: String = getEnvVar("JWT_ISSUER"),
    val jwkKeysUrl: String = getEnvVar("JWKKEYS_URL", "https://login.microsoftonline.com/common/discovery/keys"),
    val sykmeldingStatusTopic: String = getEnvVar("KAFKA_SYKMELDING_STATUS_TOPIC", "aapen-syfo-sykmeldingstatus-leesah-v1"),
    val stsOidcIssuer: String = getEnvVar("STS_OIDC_ISSUER"),
    val stsOidcAudience: String = getEnvVar("STS_OIDC_AUDIENCE"),
    val redisHost: String = getEnvVar("REDIS_HOST", "sykmeldinger-backend-redis.default.svc.nais.local"),
    val redisPort: Int = getEnvVar("REDIS_PORT_SYKMELDINGER", "6379").toInt(),
    val syfosmregisterUrl: String = getEnvVar("SYFOSMREGISTER_URL", "http://syfosmregister"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL")
) : KafkaConfig

data class VaultSecrets(
    val serviceuserUsername: String = getFileAsString("/secrets/serviceuser/username"),
    val serviceuserPassword: String = getFileAsString("/secrets/serviceuser/password"),
    val clientId: String = getFileAsString("/secrets/azuread/sykmeldinger-backend/client_id"),
    val oidcWellKnownUri: String = getEnvVar("OIDC_WELLKNOWN_URI"),
    val stsOidcWellKnownUri: String = getEnvVar("STS_OIDC_WELLKNOWN_URI"),
    val loginserviceClientId: String = getEnvVar("LOGINSERVICE_CLIENTID"),
    val redisSecret: String = getEnvVar("REDIS_SECRET")
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
