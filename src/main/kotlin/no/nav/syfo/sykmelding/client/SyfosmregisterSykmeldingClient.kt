package no.nav.syfo.sykmelding.client

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import no.nav.syfo.log
import no.nav.syfo.sykmelding.api.ApiFilter
import no.nav.syfo.sykmelding.model.Sykmelding

class SyfosmregisterSykmeldingClient(private val endpointUrl: String, private val httpClient: HttpClient) {
    suspend fun getSykmelding(token: String, sykmeldingid: String): Sykmelding? {
        try {
            return httpClient.get("$endpointUrl/api/v2/sykmeldinger/$sykmeldingid") {
                accept(ContentType.Application.Json)
                headers {
                    append("Authorization", token)
                }
            }
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall getSykmelding $sykmeldingid", e)
            throw e
        }
    }

    suspend fun getSykmeldinger(token: String, apiFilter: ApiFilter?): List<Sykmelding> {
        try {
            return httpClient.get(getRequestUrl(apiFilter)) {
                accept(ContentType.Application.Json)
                headers {
                    append("Authorization", token)
                }
            }
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall getSykmeldinger", e)
            throw e
        }
    }

    fun getRequestUrl(apiFilter: ApiFilter?): String {
        if (apiFilter == null || (apiFilter.fom == null && apiFilter.tom == null && apiFilter.exclude.isNullOrEmpty() && apiFilter.include.isNullOrEmpty())) {
            return "$endpointUrl/api/v2/sykmeldinger"
        }
        val excludes = apiFilter.exclude?.joinToString("&") { "exclude=$it" }
        val includes = apiFilter.include?.joinToString("&") { "include=$it" }

        if (includes == null && excludes == null) {
            return if (apiFilter.fom != null) {
                "$endpointUrl/api/v2/sykmeldinger?" + apiFilter.fom.let { "fom=$it" } + apiFilter.tom?.let { "&tom=$it" }.orEmpty()
            } else {
                "$endpointUrl/api/v2/sykmeldinger?" + apiFilter.tom?.let { "tom=$it" }.orEmpty()
            }
        }
        return "$endpointUrl/api/v2/sykmeldinger?" + excludes?.let { excludes }.orEmpty() + includes?.let { includes }.orEmpty() + apiFilter.fom?.let { "&fom=$it" }.orEmpty() + apiFilter.tom?.let { "&tom=$it" }.orEmpty()
    }
}
