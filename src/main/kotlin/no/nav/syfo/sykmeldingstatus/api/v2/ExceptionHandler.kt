package no.nav.syfo.sykmeldingstatus.api.v2

import io.ktor.application.call
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import no.nav.syfo.log

fun StatusPages.Configuration.setUpSykmeldingSendApiV2ExeptionHandler() {
    exception<ValidationException> { cause ->
        call.respond(HttpStatusCode.BadRequest, cause.message ?: "Unknown error")
        log.warn(cause.message ?: "Unknown error")
    }
}
