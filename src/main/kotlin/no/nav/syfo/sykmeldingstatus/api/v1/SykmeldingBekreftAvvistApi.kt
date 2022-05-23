package no.nav.syfo.sykmeldingstatus.api.v1

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.metrics.BEKREFTET_AVVIST_AV_BRUKER_COUNTER
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService

fun Route.registerSykmeldingBekreftAvvistApi(sykmeldingStatusService: SykmeldingStatusService) {
    post("/sykmeldinger/{sykmeldingid}/bekreftAvvist") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val token = call.request.headers["Authorization"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr

        sykmeldingStatusService.registrerBekreftetAvvist(
            sykmeldingId = sykmeldingId,
            source = "user",
            fnr = fnr,
            token = token
        )

        BEKREFTET_AVVIST_AV_BRUKER_COUNTER.inc()
        call.respond(HttpStatusCode.Accepted)
    }
}

fun Route.registerSykmeldingBekreftAvvistApiV2(sykmeldingStatusService: SykmeldingStatusService) {
    post("/sykmeldinger/{sykmeldingid}/bekreftAvvist") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val token = call.request.headers["Authorization"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr
        val tokenUtenPrefiks = token.removePrefix("Bearer ")

        sykmeldingStatusService.registrerBekreftetAvvist(
            sykmeldingId = sykmeldingId,
            source = "user",
            fnr = fnr,
            token = tokenUtenPrefiks,
            erTokenX = true
        )

        BEKREFTET_AVVIST_AV_BRUKER_COUNTER.inc()
        call.respond(HttpStatusCode.Accepted)
    }
}
