package no.nav.syfo.brukerinformasjon.api

import io.ktor.application.call
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.principal
import io.ktor.http.HttpHeaders
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.pdl.service.PdlPersonService
import java.util.UUID

@KtorExperimentalAPI
fun Route.registrerBrukerinformasjonApi(pdlPersonService: PdlPersonService, stsOidcClient: StsOidcClient) {
    route("api/v1/syforest/brukerinformasjon") {
        get {
            val principal = call.principal<JWTPrincipal>()!!
            val fnr = principal.payload.subject
            val token = call.request.headers[HttpHeaders.Authorization]!!

            val stsToken = stsOidcClient.oidcToken()
            val person = pdlPersonService.getPerson(
                fnr = fnr,
                userToken = token,
                callId = UUID.randomUUID().toString(),
                stsToken = stsToken.access_token
            )

            call.respond(
                Brukerinformasjon(strengtFortroligAdresse = person.diskresjonskode)
            )
        }
    }
}

data class Brukerinformasjon(
    val strengtFortroligAdresse: Boolean
)
