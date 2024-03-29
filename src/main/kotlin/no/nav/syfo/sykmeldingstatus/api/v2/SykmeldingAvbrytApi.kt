package no.nav.syfo.sykmeldingstatus.api.v2

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.syfo.metrics.AVBRUTT_AV_BRUKER_COUNTER
import no.nav.syfo.plugins.BrukerPrincipal
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import org.koin.ktor.ext.inject

fun Route.registerSykmeldingAvbrytApiV2() {
    val sykmeldingStatusService by inject<SykmeldingStatusService>()

    post("/sykmeldinger/{sykmeldingid}/avbryt") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr

        sykmeldingStatusService.createAvbruttStatus(
            sykmeldingId = sykmeldingId,
            fnr = fnr,
        )

        AVBRUTT_AV_BRUKER_COUNTER.inc()
        call.respond(HttpStatusCode.Accepted)
    }
}
