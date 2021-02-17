package no.nav.syfo.sykmeldingstatus.api

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.metrics.BEKREFTET_AV_BRUKER_COUNTER
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService

fun Route.registerSykmeldingBekreftApi(sykmeldingStatusService: SykmeldingStatusService) {
    post("/api/v1/sykmeldinger/{sykmeldingid}/bekreft") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val token = call.request.headers["Authorization"]!!
        val principal: JWTPrincipal = call.authentication.principal()!!
        val fnr = principal.payload.subject
        val sykmeldingBekreftEventDTO = call.receiveOrNull<SykmeldingBekreftEventUserDTO>()

        sykmeldingStatusService.registrerBekreftet(
            sykmeldingBekreftEventDTO = SykmeldingBekreftEventDTO(
                timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                sporsmalOgSvarListe = sykmeldingBekreftEventDTO?.sporsmalOgSvarListe
            ),
            sykmeldingId = sykmeldingId,
            source = "user",
            fnr = fnr,
            token = token
        )

        BEKREFTET_AV_BRUKER_COUNTER.inc()
        call.respond(HttpStatusCode.Accepted)
    }
}
