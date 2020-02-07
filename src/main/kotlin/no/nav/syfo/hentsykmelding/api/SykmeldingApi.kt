package no.nav.syfo.hentsykmelding.api

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.hentsykmelding.SykmeldingService

@KtorExperimentalAPI
fun Route.registerSykmeldingApi(sykmeldingService: SykmeldingService) {
    route("/api/v1") {

        get("/sykmeldinger") {
            val principal: JWTPrincipal = call.authentication.principal()!!
            val subject = principal.payload.subject

            val sykmeldinger: List<SykmeldingDTO> = sykmeldingService.hentSykmeldinger(subject)

            when {
                sykmeldinger.isNotEmpty() -> call.respond(sykmeldinger)
                else -> call.respond(emptyList<SykmeldingDTO>())
            }
        }
    }
}
