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
import no.nav.syfo.client.SyfosmregisterClient
import no.nav.syfo.log
import no.nav.syfo.metrics.BEKREFTET_AV_BRUKER_COUNTER
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService

fun Route.registerSykmeldingBekreftApi(syfosmregisterClient: SyfosmregisterClient, sykmeldingStatusService: SykmeldingStatusService) {
    post("/api/v1/sykmeldinger/{sykmeldingsid}/bekreft") {
        val sykmeldingsid = call.parameters["sykmeldingsid"]!!
        val token = call.request.headers["Authorization"]!!
        val principal: JWTPrincipal = call.authentication.principal()!!
        val fnr = principal.payload.subject
        val sisteStatus = hentSisteStatusOgSjekkTilgang(syfosmregisterClient = syfosmregisterClient, sykmeldingId = sykmeldingsid, token = token)

        if (sisteStatus == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            if (kanBekrefte(sisteStatus, sykmeldingsid)) {
                sykmeldingStatusService.registrerBekreftet(sykmeldingBekreftEventDTO = SykmeldingBekreftEventDTO(OffsetDateTime.now(ZoneOffset.UTC), null), sykmeldingId = sykmeldingsid, source = "user", fnr = fnr)
                BEKREFTET_AV_BRUKER_COUNTER.inc()
                call.respond(HttpStatusCode.Accepted)
            } else {
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }
}

suspend fun hentSisteStatusOgSjekkTilgang(syfosmregisterClient: SyfosmregisterClient, sykmeldingId: String, token: String): SykmeldingStatusEventDTO? {
    return try {
        syfosmregisterClient.hentSykmeldingstatus(sykmeldingId = sykmeldingId, token = token)
    } catch (e: Exception) {
        log.warn("Noe gikk galt ved oppdatering av status for sykmeldingid {}: {}", sykmeldingId, e.message)
        null
    }
}

fun kanBekrefte(sisteStatus: SykmeldingStatusEventDTO, sykmeldingId: String): Boolean {
    if (sisteStatus.statusEvent == StatusEventDTO.APEN || sisteStatus.statusEvent == StatusEventDTO.BEKREFTET) {
        return true
    }
    log.warn("Kan ikke bekrefte sykmelding med id {} fordi den har status {}", sykmeldingId, sisteStatus.statusEvent.name)
    return false
}
