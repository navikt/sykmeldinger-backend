package no.nav.syfo.sykmeldingstatus.api.v1

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.metrics.AVBRUTT_AV_BRUKER_COUNTER
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun Route.registerSykmeldingAvbrytApi(sykmeldingStatusService: SykmeldingStatusService) {
    post("/sykmeldinger/{sykmeldingid}/avbryt") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val token = call.request.headers["Authorization"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr

        sykmeldingStatusService.registrerStatus(
            sykmeldingStatusEventDTO = SykmeldingStatusEventDTO(StatusEventDTO.AVBRUTT, OffsetDateTime.now(ZoneOffset.UTC)),
            sykmeldingId = sykmeldingId,
            source = "user",
            fnr = fnr,
            token = token
        )

        AVBRUTT_AV_BRUKER_COUNTER.inc()
        call.respond(HttpStatusCode.Accepted)
    }
}

fun Route.registerSykmeldingAvbrytApiV2(sykmeldingStatusService: SykmeldingStatusService) {
    post("/sykmeldinger/{sykmeldingid}/avbryt") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val token = call.request.headers["Authorization"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr
        val tokenUtenPrefiks = token.removePrefix("Bearer ")

        sykmeldingStatusService.registrerStatus(
            sykmeldingStatusEventDTO = SykmeldingStatusEventDTO(StatusEventDTO.AVBRUTT, OffsetDateTime.now(ZoneOffset.UTC)),
            sykmeldingId = sykmeldingId,
            source = "user",
            fnr = fnr,
            token = tokenUtenPrefiks
        )

        AVBRUTT_AV_BRUKER_COUNTER.inc()
        call.respond(HttpStatusCode.Accepted)
    }
}
