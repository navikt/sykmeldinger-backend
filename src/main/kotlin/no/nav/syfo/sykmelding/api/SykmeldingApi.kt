package no.nav.syfo.sykmelding.api

import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.sykmelding.SykmeldingService

@KtorExperimentalAPI
fun Route.registerSykmeldingApi(sykmeldingService: SykmeldingService) {
    route("/api/v1") {

        get("/sykmeldinger") {
            val token = call.request.headers["Authorization"]!!
            val sykmeldinger: List<SykmeldingDTO> = sykmeldingService.hentSykmeldinger(token)
            call.respond(sykmeldinger)
        }
    }
}
