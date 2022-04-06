package no.nav.syfo

import com.nimbusds.jose.jwk.RSAKey
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "sykmeldinger-backend"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val jwtIssuer: String = getEnvVar("JWT_ISSUER"),
    val jwkKeysUrl: String = getEnvVar("JWKKEYS_URL", "https://login.microsoftonline.com/common/discovery/keys"),
    val sykmeldingStatusTopic: String = "teamsykmelding.sykmeldingstatus-leesah",
    val stsOidcIssuer: String = getEnvVar("STS_OIDC_ISSUER"),
    val stsUrl: String = getEnvVar("STS_URL", "http://security-token-service.default/rest/v1/sts/token"),
    val redisHost: String = getEnvVar("REDIS_HOST", "sykmeldinger-backend-redis.teamsykmelding.svc.nais.local"),
    val redisPort: Int = getEnvVar("REDIS_PORT_SYKMELDINGER", "6379").toInt(),
    val syfosmregisterUrl: String = getEnvVar("SYFOSMREGISTER_URL", "http://syfosmregister"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val eregUrl: String = getEnvVar("EREG_URL"),
    val aaregUrl: String = getEnvVar("AAREG_URL"),
    val loginserviceIdportenDiscoveryUrl: String = getEnvVar("LOGINSERVICE_IDPORTEN_DISCOVERY_URL"),
    val loginserviceIdportenAudience: List<String> = getEnvVar("LOGINSERVICE_IDPORTEN_AUDIENCE").split(","),
    val narmesteLederBasePath: String = getEnvVar("NARMESTELEDER_URL"),
    val tokenXWellKnownUrl: String = getEnvVar("TOKEN_X_WELL_KNOWN_URL"),
    val clientIdTokenX: String = getEnvVar("TOKEN_X_CLIENT_ID"),
    val tokenXPrivateJwk: RSAKey = RSAKey.parse(getEnvVar("TOKEN_X_PRIVATE_JWK"))
)

data class VaultSecrets(
    val serviceuserUsername: String = getFileAsString("/secrets/serviceuser/username"),
    val serviceuserPassword: String = getFileAsString("/secrets/serviceuser/password"),
    val clientId: String = getFileAsString("/secrets/azuread/sykmeldinger-backend/client_id"),
    val clientSecret: String = getFileAsString("/secrets/azuread/sykmeldinger-backend/client_secret"),
    val redisSecret: String = getEnvVar("REDIS_PASSWORD")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
