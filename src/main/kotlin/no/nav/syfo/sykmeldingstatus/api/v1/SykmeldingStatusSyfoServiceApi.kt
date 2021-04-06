package no.nav.syfo.sykmeldingstatus.api.v1

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService

fun Route.registerSykmeldingStatusSyfoServiceApi(sykmeldingStatusService: SykmeldingStatusService) {

    post("/sykmeldinger/{sykmeldingsid}/status") {
        val sykmeldingId = call.parameters["sykmeldingsid"]!!
        val fnr = call.request.headers["FNR"]!!
        val sykmeldingStatusEventDTO = call.receive<SykmeldingStatusEventDTO>()
        val token = call.request.headers["Authorization"]!!
        sykmeldingStatusService.registrerStatus(sykmeldingStatusEventDTO = sykmeldingStatusEventDTO, sykmeldingId = sykmeldingId, source = "syfoservice", fnr = fnr, token = token)
        call.respond(HttpStatusCode.Created)
    }
}
