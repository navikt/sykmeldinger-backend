package no.nav.syfo.sykmelding.exception

import io.ktor.application.call
import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.ServerResponseException
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import kotlinx.coroutines.io.readUTF8Line
import no.nav.syfo.log

fun StatusPages.Configuration.setUpSykmeldingExceptionHandler() {
    exception<ServerResponseException> { cause ->
        call.respond(cause.response.status, cause.response.content.readUTF8Line() ?: "Unknown error")
        when (cause.response.status) {
            HttpStatusCode.InternalServerError -> log.error(cause.message)
            else -> log.warn(cause.message)
        }
    }
    exception<ClientRequestException> { cause ->
        call.respond(cause.response.status, cause.response.content.readUTF8Line() ?: "Unknown error")
        when (cause.response.status) {
            HttpStatusCode.Unauthorized -> log.warn("User has not access to sykmelding")
            else -> log.warn(cause.message)
        }
    }
}
