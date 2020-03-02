package no.nav.syfo.sykmelding.client

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import no.nav.syfo.sykmelding.api.SykmeldingDTO

class SyfosmregisterSykmeldingClient(private val endpointUrl: String, private val httpClient: HttpClient) {
    suspend fun getSykmeldinger(token: String): List<SykmeldingDTO> {
        return httpClient.get("$endpointUrl/sykmeldinger") {
            accept(ContentType.Application.Json)
            headers {
                append("Authorization", token)
            }
        }
    }
}
