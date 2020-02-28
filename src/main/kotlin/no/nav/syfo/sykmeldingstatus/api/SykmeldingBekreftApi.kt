package no.nav.syfo.sykmeldingstatus.api

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.metrics.BEKREFTET_AV_BRUKER_COUNTER
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService

fun Route.registerSykmeldingBekreftApi(sykmeldingStatusService: SykmeldingStatusService) {
    post("/api/v1/sykmeldinger/{sykmeldingsid}/bekreft") {
        val sykmeldingsid = call.parameters["sykmeldingsid"]!!
        val token = call.request.headers["Authorization"]!!
        val principal: JWTPrincipal = call.authentication.principal()!!
        val fnr = principal.payload.subject

        sykmeldingStatusService.registrerBekreftet(sykmeldingBekreftEventDTO = SykmeldingBekreftEventDTO(OffsetDateTime.now(ZoneOffset.UTC), null),
                sykmeldingId = sykmeldingsid,
                source = "user",
                fnr = fnr,
                token = token)

        BEKREFTET_AV_BRUKER_COUNTER.inc()
        call.respond(HttpStatusCode.Accepted)
    }
}
