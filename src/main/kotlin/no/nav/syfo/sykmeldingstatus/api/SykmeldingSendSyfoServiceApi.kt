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
        val token = call.request.headers["Authorization"]!!
        val syfoserviceSource = call.request.headers["source"]
        val source = syfoserviceSource ?: "syfoservice"
        sykmeldingStatusService.registrerSendt(
            sykmeldingSendEventDTO = sykmeldingSendEventDTO,
            sykmeldingId =
            sykmeldingId,
            source = source,
            fnr = fnr,
            token = token,
            fromSyfoservice = true
        )
        log.info("Sendt sykmelding {}", sykmeldingId)
        call.respond(HttpStatusCode.Created)
    }
}
