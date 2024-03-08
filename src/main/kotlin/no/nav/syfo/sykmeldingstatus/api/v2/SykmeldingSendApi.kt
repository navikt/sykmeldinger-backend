package no.nav.syfo.sykmeldingstatus.api.v2

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.plugins.BrukerPrincipal
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.utils.logger
import org.koin.ktor.ext.inject

private val logger = logger("Route.registrerSykmeldingSendApiV3")

fun Route.registrerSykmeldingSendApiV3() {
    val sendtSykmeldingStatusService by inject<SykmeldingStatusService>()

    post("/sykmeldinger/{sykmeldingid}/send") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr

        when (val sykmeldingFormResponse = call.safeReceiveOrNull<SykmeldingFormResponse>()) {
            null -> call.respond(HttpStatusCode.BadRequest, "Empty body")
            else -> {
                sykmeldingFormResponse.validate()
                sendtSykmeldingStatusService.createStatus(
                    sykmeldingFormResponse,
                    sykmeldingId,
                    fnr,
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
                    fnr,
                )

                call.respond(HttpStatusCode.Accepted)
            }
        }
    }
}

// Workaround pga. bug i ktor: https://github.com/ktorio/ktor/issues/901
private suspend inline fun <reified T : Any> ApplicationCall.safeReceiveOrNull(): T? =
    try {
        kotlin.runCatching { receiveNullable<T>() }.getOrNull()
    } catch (e: Exception) {
        logger.error("An error occurred while receiving body content: ${e.message}")
        null
    }
