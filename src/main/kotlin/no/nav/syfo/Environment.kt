package no.nav.syfo

import com.nimbusds.jose.jwk.RSAKey

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "sykmeldinger-backend"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val sykmeldingStatusTopic: String = "teamsykmelding.sykmeldingstatus-leesah",
    val redisHost: String = getEnvVar("REDIS_HOST", "sykmeldinger-backend-redis.teamsykmelding.svc.cluster.local"),
    val redisPort: Int = getEnvVar("REDIS_PORT_SYKMELDINGER", "6379").toInt(),
    val smregisterUrl: String = getEnvVar("SMREGISTER_URL"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val eregUrl: String = getEnvVar("EREG_URL"),
    val aaregUrl: String = getEnvVar("AAREG_URL"),
    val loginserviceIdportenDiscoveryUrl: String = getEnvVar("LOGINSERVICE_IDPORTEN_DISCOVERY_URL"),
    val loginserviceIdportenAudience: List<String> = getEnvVar("LOGINSERVICE_IDPORTEN_AUDIENCE").split(","),
    val narmesteLederBasePath: String = getEnvVar("NARMESTELEDER_URL"),
    val tokenXWellKnownUrl: String = getEnvVar("TOKEN_X_WELL_KNOWN_URL"),
    val clientIdTokenX: String = getEnvVar("TOKEN_X_CLIENT_ID"),
    val tokenXPrivateJwk: RSAKey = RSAKey.parse(getEnvVar("TOKEN_X_PRIVATE_JWK")),
    val smregisterAudience: String = getEnvVar("SMREGISTER_AUDIENCE"),
    val pdlAudience: String = getEnvVar("PDL_AUDIENCE"),
    val aaregAudience: String = getEnvVar("AAREG_AUDIENCE"),
    val narmestelederAudience: String = getEnvVar("NARMESTELEDER_AUDIENCE"),
    val allowedOrigin: List<String> = getEnvVar("ALLOWED_ORIGIN").split(",")
)

data class VaultSecrets(
    val redisSecret: String = getEnvVar("REDIS_PASSWORD")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
