package no.nav.syfo.sykmeldingstatus.api.v2

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.response.respond
import no.nav.syfo.log

fun StatusPagesConfig.setUpSykmeldingSendApiV2ExeptionHandler() {
    exception<ValidationException> { call, cause ->
        call.respond(HttpStatusCode.BadRequest, cause.message ?: "Unknown error")
        log.warn(cause.message ?: "Unknown error")
    }
}
