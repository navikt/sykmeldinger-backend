package no.nav.syfo.sykmeldingstatus.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.syfo.log
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService

fun Route.registerSykmeldingStatusSyfoServiceApi(sykmeldingStatusService: SykmeldingStatusService) {

    post("/sykmeldinger/{sykmeldingsid}/status") {
        val sykmeldingId = call.parameters["sykmeldingsid"]!!
        val fnr = call.request.headers["FNR"]!!
        val sykmeldingStatusEventDTO = call.receive<SykmeldingStatusEventDTO>()
        try {
            sykmeldingStatusService.registrerStatus(sykmeldingStatusEventDTO = sykmeldingStatusEventDTO, sykmeldingId = sykmeldingId, source = "syfoservice", fnr = fnr)
            call.respond(HttpStatusCode.Created)
        } catch (ex: Exception) {
            log.error("Internal server error", ex)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}
