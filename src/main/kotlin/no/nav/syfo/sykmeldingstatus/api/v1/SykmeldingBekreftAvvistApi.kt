package no.nav.syfo.sykmeldingstatus.api.v1

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.metrics.BEKREFTET_AVVIST_AV_BRUKER_COUNTER
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService

fun Route.registerSykmeldingBekreftAvvistApi(sykmeldingStatusService: SykmeldingStatusService) {
    post("/sykmeldinger/{sykmeldingid}/bekreftAvvist") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr
        sykmeldingStatusService.registrerBekreftetAvvist(
            sykmeldingId = sykmeldingId,
            source = "user",
            fnr = fnr
        )

        BEKREFTET_AVVIST_AV_BRUKER_COUNTER.inc()
        call.respond(HttpStatusCode.Accepted)
    }
}

fun Route.registerSykmeldingBekreftAvvistApiV2(sykmeldingStatusService: SykmeldingStatusService) {
    post("/sykmeldinger/{sykmeldingid}/bekreftAvvist") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr
        sykmeldingStatusService.registrerBekreftetAvvist(
            sykmeldingId = sykmeldingId,
            source = "user",
            fnr = fnr
        )

        BEKREFTET_AVVIST_AV_BRUKER_COUNTER.inc()
        call.respond(HttpStatusCode.Accepted)
    }
}
