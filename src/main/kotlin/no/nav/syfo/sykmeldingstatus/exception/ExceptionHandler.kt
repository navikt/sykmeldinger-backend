package no.nav.syfo.sykmeldingstatus.exception

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.response.respond
import no.nav.syfo.log

fun StatusPagesConfig.setUpSykmeldingStatusExeptionHandler() {
    exception<InvalidSykmeldingStatusException> { call, cause ->
        call.respond(HttpStatusCode.BadRequest, cause.message ?: "Unknown error")
        log.warn(cause.message ?: "Unknown error")
    }
    exception<SykmeldingStatusNotFoundException> { call, cause ->
        call.respond(HttpStatusCode.NotFound, cause.message ?: "Unknown error")
        log.warn(cause.message ?: "Unknown error")
    }
}
