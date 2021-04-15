package no.nav.syfo.sykmeldingstatus.api.v2

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.api.v1.safeReceiveOrNull

fun Route.registrerSykmeldingSendApiV2(sykmeldingStatusService: SykmeldingStatusService) {
    post("/api/v2/sykmeldinger/{sykmeldingid}/send") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val token = call.request.headers["Authorization"]!!
        val principal: JWTPrincipal = call.authentication.principal()!!
        val fnr = principal.payload.subject

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
