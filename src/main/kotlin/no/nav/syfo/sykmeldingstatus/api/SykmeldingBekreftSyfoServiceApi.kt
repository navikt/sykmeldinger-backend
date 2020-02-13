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
        val sykmeldingBekreftEventDTO = call.receive<SykmeldingBekreftEventDTO>()

        try {
            sykmeldingStatusService.registrerBekreftet(sykmeldingBekreftEventDTO = sykmeldingBekreftEventDTO, sykmeldingId = sykmeldingId, source = "syfoservice")
            log.info("Bekreftet sykmelding {}", sykmeldingId)
            call.respond(HttpStatusCode.Created)
        } catch (ex: Exception) {
            log.error("Noe gikk galt ved bekrefting av sykmelding {}", sykmeldingId, ex)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}
