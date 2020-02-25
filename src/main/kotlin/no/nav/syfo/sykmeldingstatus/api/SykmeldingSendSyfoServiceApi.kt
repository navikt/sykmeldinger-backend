package no.nav.syfo.sykmeldingstatus.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.syfo.log
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService

fun Route.registerSykmeldingSendSyfoServiceApi(sykmeldingStatusService: SykmeldingStatusService) {

    post("/sykmeldinger/{sykmeldingid}/send") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val fnr = call.request.headers["FNR"]!!
        val sykmeldingSendEventDTO = call.receive<SykmeldingSendEventDTO>()

        try {
            sykmeldingStatusService.registrerSendt(sykmeldingSendEventDTO = sykmeldingSendEventDTO, sykmeldingId = sykmeldingId, source = "syfoservice", fnr = fnr)
            log.info("Sendt sykmelding {}", sykmeldingId)
            call.respond(HttpStatusCode.Created)
        } catch (ex: Exception) {
            log.error("Noe gikk galt ved innsending av sykmelding {}", sykmeldingId, ex)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}
