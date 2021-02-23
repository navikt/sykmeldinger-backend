package no.nav.syfo.sykmelding.api

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmeldingstatus.api.StatusEventDTO
import java.time.LocalDate

@KtorExperimentalAPI
fun Route.registerSykmeldingApi(sykmeldingService: SykmeldingService) {
    route("/api/v1") {

        get("/sykmeldinger") {
            val token = call.request.headers["Authorization"]!!
            val fom = call.parameters["fom"]?.let { LocalDate.parse(it) }
            val tom = call.parameters["tom"]?.let { LocalDate.parse(it) }
            val exclude = call.parameters.getAll("exclude")
            val include = call.parameters.getAll("include")
            when {
                checkExcludeInclude(exclude, include) -> call.respond(HttpStatusCode.BadRequest, "Can not use both include and exclude")
                checkFomAndTomDate(fom, tom) -> call.respond(HttpStatusCode.BadRequest, "FOM should be before or equal to TOM")
                hasInvalidStatus(exclude ?: include) -> call.respond(HttpStatusCode.BadRequest, "include or exclude can only contain ${StatusEventDTO.values().joinToString()}")
                else -> call.respond(sykmeldingService.hentSykmeldinger(token = token, apiFilter = ApiFilter(fom = fom, tom = tom, exclude = exclude, include = include)))
            }
        }

        get("/syforest/sykmeldinger") {
            val token = call.request.headers["Authorization"]!!
            val principal: JWTPrincipal = call.authentication.principal()!!
            val fnr = principal.payload.subject
            call.respond(sykmeldingService.hentSykmeldingerSyforestFormat(token = token, fnr = fnr, apiFilter = null))
        }
    }
}
private fun hasInvalidStatus(statusFilter: List<String>?): Boolean {
    return !statusFilter.isNullOrEmpty() && !StatusEventDTO.values().map { it.name }.containsAll(statusFilter)
}

private fun checkExcludeInclude(exclude: List<String>?, include: List<String>?): Boolean {
    return exclude != null && include != null
}

private fun checkFomAndTomDate(fom: LocalDate?, tom: LocalDate?) =
    fom != null && tom != null && tom.isBefore(fom)
