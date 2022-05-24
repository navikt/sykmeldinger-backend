package no.nav.syfo.sykmelding.exception

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.response.respond
import no.nav.syfo.log

fun StatusPagesConfig.setUpSykmeldingExceptionHandler() {
    exception<ServerResponseException> { call, cause ->
        call.respond(cause.response.status, cause.response.bodyAsText())
        when (cause.response.status) {
            HttpStatusCode.InternalServerError -> log.error(cause.message)
            else -> log.warn(cause.message)
        }
    }
    exception<ClientRequestException> { call, cause ->
        call.respond(cause.response.status, cause.response.bodyAsText())
        when (cause.response.status) {
            HttpStatusCode.Unauthorized -> log.warn("User has not access to sykmelding")
            else -> log.warn(cause.message)
        }
    }
}
