package no.nav.syfo.sykmeldingstatus.exception

import io.ktor.application.call
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import no.nav.syfo.log

fun StatusPages.Configuration.setUpSykmeldingStatusExeptionHandler() {
    exception<InvalidSykmeldingStatusException> { cause ->
        call.respond(HttpStatusCode.BadRequest, cause.message ?: "Unknown error")
        log.warn(cause.message ?: "Unknown error")
    }
    exception<SykmeldingStatusNotFoundException> { cause ->
        call.respond(HttpStatusCode.NotFound, cause.message ?: "Unknown error")
        log.warn(cause.message ?: "Unknown error")
    }
}
