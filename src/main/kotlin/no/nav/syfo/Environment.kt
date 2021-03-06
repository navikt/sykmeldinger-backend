package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "sykmeldinger-backend"),
    override val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val jwtIssuer: String = getEnvVar("JWT_ISSUER"),
    val jwkKeysUrl: String = getEnvVar("JWKKEYS_URL", "https://login.microsoftonline.com/common/discovery/keys"),
    val sykmeldingStatusTopic: String = getEnvVar("KAFKA_SYKMELDING_STATUS_TOPIC", "aapen-syfo-sykmeldingstatus-leesah-v1"),
    val stsOidcIssuer: String = getEnvVar("STS_OIDC_ISSUER"),
    val stsOidcAudience: String = getEnvVar("STS_OIDC_AUDIENCE"),
    val stsUrl: String = getEnvVar("STS_URL", "http://security-token-service.default/rest/v1/sts/token"),
    val redisHost: String = getEnvVar("REDIS_HOST", "sykmeldinger-backend-redis.teamsykmelding.svc.nais.local"),
    val redisPort: Int = getEnvVar("REDIS_PORT_SYKMELDINGER", "6379").toInt(),
    val syfosmregisterUrl: String = getEnvVar("SYFOSMREGISTER_URL", "http://syfosmregister"),
    val syfosoknadUrl: String = getEnvVar("SYFOSOKNAD_URL", "http://syfosoknad.flex"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val registerBasePath: String = getEnvVar("REGISTER_BASE_PATH"),
    val loginserviceIdportenDiscoveryUrl: String = getEnvVar("LOGINSERVICE_IDPORTEN_DISCOVERY_URL"),
    val loginserviceIdportenAudience: List<String> = getEnvVar("LOGINSERVICE_IDPORTEN_AUDIENCE").split(","),
    val narmesteLederBasePath: String = getEnvVar("NARMESTELEDER_URL"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    override val truststore: String? = getEnvVar("NAV_TRUSTSTORE_PATH"),
    override val truststorePassword: String? = getEnvVar("NAV_TRUSTSTORE_PASSWORD")
) : KafkaConfig

data class VaultSecrets(
    val serviceuserUsername: String = getFileAsString("/secrets/serviceuser/username"),
    val serviceuserPassword: String = getFileAsString("/secrets/serviceuser/password"),
    val clientId: String = getFileAsString("/secrets/azuread/sykmeldinger-backend/client_id"),
    val clientSecret: String = getFileAsString("/secrets/azuread/sykmeldinger-backend/client_secret"),
    val redisSecret: String = getEnvVar("REDIS_PASSWORD")
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
