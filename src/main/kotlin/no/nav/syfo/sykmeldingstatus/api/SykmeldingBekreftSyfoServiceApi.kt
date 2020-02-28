package no.nav.syfo.sykmeldingstatus.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.syfo.log
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService

fun Route.registerSykmeldingBekreftSyfoServiceApi(sykmeldingStatusService: SykmeldingStatusService) {

    post("/sykmeldinger/{sykmeldingid}/bekreft") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val fnr = call.request.headers["FNR"]!!
        val sykmeldingBekreftEventDTO = call.receive<SykmeldingBekreftEventDTO>()
        val token = call.request.headers["Authorization"]!!

        sykmeldingStatusService.registrerBekreftet(sykmeldingBekreftEventDTO = sykmeldingBekreftEventDTO, sykmeldingId = sykmeldingId, source = "syfoservice", fnr = fnr, token = token)
        log.info("Bekreftet sykmelding {}", sykmeldingId)
        call.respond(HttpStatusCode.Accepted)
    }
}
