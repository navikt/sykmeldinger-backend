package no.nav.syfo.sykmelding.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.log
import no.nav.syfo.securelog
import no.nav.syfo.sykmelding.SykmeldingService

fun Route.registerSykmeldingApiV2(sykmeldingService: SykmeldingService) {
    get("/sykmeldinger") {
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr
        val sykmeldinger = sykmeldingService.getSykmeldinger(fnr = fnr)
        securelog.info(
            "getting sykmeldinger for fnr: $fnr, sykmeldingIds ${sykmeldinger.map { it.id }}",
        )
        call.respond(sykmeldinger)
    }

    get("/sykmeldinger/{sykmeldingid}") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr

        if (sykmeldingId == "null") {
            log.warn("Mottok kall for å hente sykmelding med id null, sender 404 Not Found")
            call.respond(HttpStatusCode.NotFound)
        } else {
            log.info("Henter ut sykmelding for sykmeldingid: $sykmeldingId")
            val sykmelding = sykmeldingService.getSykmelding(fnr, sykmeldingId)
            log.info("Er over 70 år : {} ", sykmelding?.overSyttiAar)
            when (sykmelding) {
                null ->
                    call.respond(HttpStatusCode.NotFound).also {
                        sykmeldingService.logInfo(sykmeldingId, fnr)
                        securelog.info(
                            "Fikk null fra sql, prøvde å hente ut sykmelding for " +
                                "fnr: $fnr med sykmeldingid: $sykmeldingId",
                        )
                    }
                else -> call.respond(sykmelding)
            }
        }
    }
}
