package no.nav.syfo.sykmelding.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import no.nav.syfo.client.TokenXClient
import no.nav.syfo.log
import no.nav.syfo.sykmelding.api.ApiFilter
import no.nav.syfo.sykmelding.model.Sykmelding

class SyfosmregisterSykmeldingClient(
    private val endpointUrl: String,
    private val httpClient: HttpClient,
    private val tokenXClient: TokenXClient,
    private val audience: String
) {
    suspend fun getSykmelding(token: String, sykmeldingid: String): Sykmelding? {
        try {
            return httpClient.get("$endpointUrl/api/v2/sykmeldinger/$sykmeldingid") {
                accept(ContentType.Application.Json)
                headers {
                    append("Authorization", token)
                }
            }.body()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall getSykmelding $sykmeldingid", e)
            throw e
        }
    }

    suspend fun getSykmeldingTokenX(subjectToken: String, sykmeldingid: String): Sykmelding? {
        val token = tokenXClient.getAccessToken(
            subjectToken = subjectToken,
            audience = audience
        )
        try {
            return httpClient.get("$endpointUrl/api/v3/sykmeldinger/$sykmeldingid") {
                accept(ContentType.Application.Json)
                headers {
                    append("Authorization", "Bearer $token")
                }
            }.body()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall getSykmelding $sykmeldingid (tokenx)", e)
            throw e
        }
    }

    suspend fun getSykmeldinger(token: String, apiFilter: ApiFilter?): List<Sykmelding> {
        try {
            return httpClient.get(getRequestUrl(apiFilter, "$endpointUrl/api/v2")) {
                accept(ContentType.Application.Json)
                headers {
                    append("Authorization", token)
                }
            }.body()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall getSykmeldinger", e)
            throw e
        }
    }

    suspend fun getSykmeldingerTokenX(subjectToken: String, apiFilter: ApiFilter?): List<Sykmelding> {
        val token = tokenXClient.getAccessToken(
            subjectToken = subjectToken,
            audience = audience
        )
        try {
            return httpClient.get(getRequestUrl(apiFilter, "$endpointUrl/api/v3")) {
                accept(ContentType.Application.Json)
                headers {
                    append("Authorization", "Bearer $token")
                }
            }.body()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall getSykmeldinger (tokenx)", e)
            throw e
        }
    }

    fun getRequestUrl(apiFilter: ApiFilter?, url: String): String {
        if (apiFilter == null || (apiFilter.fom == null && apiFilter.tom == null && apiFilter.exclude.isNullOrEmpty() && apiFilter.include.isNullOrEmpty())) {
            return "$url/sykmeldinger"
        }
        val excludes = apiFilter.exclude?.joinToString("&") { "exclude=$it" }
        val includes = apiFilter.include?.joinToString("&") { "include=$it" }

        if (includes == null && excludes == null) {
            return if (apiFilter.fom != null) {
                "$url/sykmeldinger?" + apiFilter.fom.let { "fom=$it" } + apiFilter.tom?.let { "&tom=$it" }.orEmpty()
            } else {
                "$url/sykmeldinger?" + apiFilter.tom?.let { "tom=$it" }.orEmpty()
            }
        }
        return "$url/sykmeldinger?" + excludes?.let { excludes }.orEmpty() + includes?.let { includes }.orEmpty() + apiFilter.fom?.let { "&fom=$it" }.orEmpty() + apiFilter.tom?.let { "&tom=$it" }.orEmpty()
    }
}
