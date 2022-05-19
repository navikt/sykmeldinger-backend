package no.nav.syfo.sykmeldingstatus.api.v2

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.log
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService

fun Route.registrerSykmeldingSendApiV2(sykmeldingStatusService: SykmeldingStatusService) {
    post("/sykmeldinger/{sykmeldingid}/send") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val token = call.request.headers["Authorization"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr

        val sykmeldingUserEvent = call.safeReceiveOrNull<SykmeldingUserEvent>()

        when (sykmeldingUserEvent) {
            null -> call.respond(HttpStatusCode.BadRequest, "Empty body")
            else -> {
                sykmeldingUserEvent.validate()
                sykmeldingStatusService.registrerUserEvent(sykmeldingUserEvent, sykmeldingId, fnr, token)

                call.respond(HttpStatusCode.Accepted)
            }
        }
    }
}

fun Route.registrerSykmeldingSendApiV3(sykmeldingStatusService: SykmeldingStatusService) {
    post("/sykmeldinger/{sykmeldingid}/send") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val token = call.request.headers["Authorization"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr
        val tokenUtenPrefiks = token.removePrefix("Bearer ")

        val sykmeldingUserEvent = call.safeReceiveOrNull<SykmeldingUserEvent>()

        when (sykmeldingUserEvent) {
            null -> call.respond(HttpStatusCode.BadRequest, "Empty body")
            else -> {
                sykmeldingUserEvent.validate()
                sykmeldingStatusService.registrerUserEvent(sykmeldingUserEvent, sykmeldingId, fnr, tokenUtenPrefiks)

                call.respond(HttpStatusCode.Accepted)
            }
        }
    }
}

// Workaround pga. bug i ktor: https://github.com/ktorio/ktor/issues/901
suspend inline fun <reified T : Any> ApplicationCall.safeReceiveOrNull(): T? = try {
    receiveOrNull()
} catch (e: Exception) {
    log.error("An error occurred while receiving body content: ${e.message}")
    null
}
