package no.nav.syfo.sykmelding.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.log
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import java.time.LocalDate

fun Route.registerSykmeldingApi(sykmeldingService: SykmeldingService) {
    get("/sykmeldinger") {
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr
        call.respond(sykmeldingService.hentSykmeldinger(fnr = fnr))
    }

    get("/sykmeldinger/{sykmeldingid}") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val token = principal.token
        val fnr = principal.fnr

        if (sykmeldingId == "null") {
            log.warn("Mottok kall for å hente sykmelding med id null")
            call.respond(HttpStatusCode.NotFound)
        } else {
            val sykmelding = sykmeldingService.hentSykmelding(fnr, sykmeldingId)

            when (sykmelding) {
                null -> call.respond(HttpStatusCode.NotFound)
                else -> call.respond(sykmelding)
            }
        }
    }
}

fun Route.registerSykmeldingApiV2(sykmeldingService: SykmeldingService) {
    get("/sykmeldinger") {
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr

        call.respond(sykmeldingService.hentSykmeldinger(fnr = fnr))
    }

    get("/sykmeldinger/{sykmeldingid}") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr

        if (sykmeldingId == "null") {
            log.warn("Mottok kall for å hente sykmelding med id null")
            call.respond(HttpStatusCode.NotFound)
        } else {
            when (val sykmelding = sykmeldingService.hentSykmelding(fnr, sykmeldingId)) {
                null -> call.respond(HttpStatusCode.NotFound)
                else -> call.respond(sykmelding)
            }
        }
    }
}