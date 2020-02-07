package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "sykmeldinger-backend"),
    val jwtIssuer: String = getEnvVar("JWT_ISSUER"),
    val appIds: List<String> = getEnvVar("ALLOWED_APP_IDS")
        .split(",")
        .map { it.trim() },
    val clientId: String = getEnvVar("CLIENT_ID"),
    val jwkKeysUrl: String = getEnvVar("JWKKEYS_URL", "https://login.microsoftonline.com/common/discovery/keys"),
    val sykmeldingStatusTopic: String = getEnvVar("KAFKA_SYKMELDING_STATUS_TOPIC", "aapen-syfo-sykmelding-status"),
    val stsOidcIssuer: String = getEnvVar("STS_OIDC_ISSUER"),
    val stsOidcAudience: String = getEnvVar("STS_OIDC_AUDIENCE"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL")
) : KafkaConfig

data class VaultSecrets(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val oidcWellKnownUri: String,
    val stsOidcWellKnownUri: String,
    val loginserviceClientId: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
