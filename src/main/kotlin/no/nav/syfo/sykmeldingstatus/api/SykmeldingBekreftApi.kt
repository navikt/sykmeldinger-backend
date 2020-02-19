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
import no.nav.syfo.hentsykmelding.SykmeldingService
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService

fun Route.registerSykmeldingBekreftApi(sykmeldingService: SykmeldingService, sykmeldingStatusService: SykmeldingStatusService) {
    post("/api/v1/sykmeldinger/{sykmeldingsid}/bekreft") {
        val sykmeldingsid = call.parameters["sykmeldingsid"]!!
        val principal: JWTPrincipal = call.authentication.principal()!!
        val subject = principal.payload.subject
        if (sykmeldingService.erEier(sykmeldingsid, subject)) {
            sykmeldingStatusService.registrerBekreftet(sykmeldingBekreftEventDTO = SykmeldingBekreftEventDTO(OffsetDateTime.now(ZoneOffset.UTC), null), sykmeldingId = sykmeldingsid, source = "user")
            call.respond(HttpStatusCode.OK)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}
