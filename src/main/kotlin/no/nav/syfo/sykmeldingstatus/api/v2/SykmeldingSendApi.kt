package no.nav.syfo.sykmeldingstatus.api.v2

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.log
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService

fun Route.registrerSykmeldingSendApiV3(sendtSykmeldingStatusService: SykmeldingStatusService) {
    post("/sykmeldinger/{sykmeldingid}/send") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr

        val sykmeldingFormResponse = call.safeReceiveOrNull<SykmeldingFormResponse>()

        when (sykmeldingFormResponse) {
            null -> call.respond(HttpStatusCode.BadRequest, "Empty body")
            else -> {
                sykmeldingFormResponse.validate()
                sendtSykmeldingStatusService.createSendtStatus(
                    sykmeldingFormResponse,
                    sykmeldingId,
                    fnr
                )

                call.respond(HttpStatusCode.Accepted)
            }
        }
    }

    post("/sykmeldinger/{sykmeldingid}/endre-egenmeldingsdager") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr

        val endreEgenmeldingsdagerEvent = call.safeReceiveOrNull<EndreEgenmeldingsdagerEvent>()

        when (endreEgenmeldingsdagerEvent) {
            null -> call.respond(HttpStatusCode.BadRequest, "Empty body")
            else -> {
                sendtSykmeldingStatusService.endreEgenmeldingsdager(
                    sykmeldingId,
                    endreEgenmeldingsdagerEvent,
                    fnr
                )

                call.respond(HttpStatusCode.Accepted)
            }
        }
    }
}

// Workaround pga. bug i ktor: https://github.com/ktorio/ktor/issues/901
suspend inline fun <reified T : Any> ApplicationCall.safeReceiveOrNull(): T? =
    try {
        kotlin.runCatching { receiveNullable<T>() }.getOrNull()
    } catch (e: Exception) {
        log.error("An error occurred while receiving body content: ${e.message}")
        null
    }
