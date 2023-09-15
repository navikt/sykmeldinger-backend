package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "sykmeldinger-backend"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val sykmeldingStatusTopic: String = "teamsykmelding.sykmeldingstatus-leesah",
    val tokenXWellKnownUrl: String = getEnvVar("TOKEN_X_WELL_KNOWN_URL"),
    val clientIdTokenX: String = getEnvVar("TOKEN_X_CLIENT_ID"),
    val databaseUsername: String = getEnvVar("DB_USERNAME"),
    val databasePassword: String = getEnvVar("DB_PASSWORD"),
    val dbHost: String = getEnvVar("DB_HOST"),
    val dbPort: String = getEnvVar("DB_PORT"),
    val dbName: String = getEnvVar("DB_DATABASE"),
) {
    fun jdbcUrl(): String {
        return "jdbc:postgresql://$dbHost:$dbPort/$dbName"
    }
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName)
        ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
