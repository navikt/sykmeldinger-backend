package no.nav.syfo.sykmeldingstatus.api.v1

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.metrics.GJENAPNET_AV_BRUKER_COUNTER
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun Route.registerSykmeldingGjenapneApi(sykmeldingStatusService: SykmeldingStatusService) {
    post("/api/v1/sykmeldinger/{sykmeldingid}/gjenapne") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val token = call.request.headers["Authorization"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr

        sykmeldingStatusService.registrerStatus(
            sykmeldingStatusEventDTO = SykmeldingStatusEventDTO(StatusEventDTO.APEN, OffsetDateTime.now(ZoneOffset.UTC)),
            sykmeldingId = sykmeldingId,
            source = "user",
            fnr = fnr,
            token = token
        )

        GJENAPNET_AV_BRUKER_COUNTER.inc()
        call.respond(HttpStatusCode.Accepted)
    }
}
