package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.Principal
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.request.header
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log

fun Application.setupAuth(
    jwkProviderTokenX: JwkProvider,
    tokenXIssuer: String,
    clientIdTokenX: String
) {
    install(Authentication) {
        jwt(name = "tokenx") {
            authHeader {
                when (val token: String? = it.getToken()) {
                    null -> return@authHeader null
                    else -> return@authHeader HttpAuthHeader.Single("Bearer", token)
                }
            }
            verifier(jwkProviderTokenX, tokenXIssuer)
            validate { credentials ->
                when {
                    hasClientIdAudience(credentials, clientIdTokenX) && erNiva4(credentials) ->
                        {
                            val principal = JWTPrincipal(credentials.payload)
                            BrukerPrincipal(
                                fnr = finnFnrFraToken(principal),
                                principal = principal,
                                token = this.getToken()!!
                            )
                        }
                    else -> unauthorized(credentials)
                }
            }
        }
    }
}

fun ApplicationCall.getToken(): String? {
    return when (val authHeader = request.header("Authorization")) {
        null -> request.cookies.get(name = "selvbetjening-idtoken")
        else -> authHeader.removePrefix("Bearer ")
    }
}

fun hasClientIdAudience(credentials: JWTCredential, clientId: String): Boolean {
    return credentials.payload.audience.contains(clientId)
}

fun unauthorized(credentials: JWTCredential): Principal? {
    log.warn(
        "Auth: Unexpected audience for jwt {}, {}",
        StructuredArguments.keyValue("issuer", credentials.payload.issuer),
        StructuredArguments.keyValue("audience", credentials.payload.audience)
    )
    return null
}

fun erNiva4(credentials: JWTCredential): Boolean {
    return "Level4" == credentials.payload.getClaim("acr").asString()
}

fun finnFnrFraToken(principal: JWTPrincipal): String {
    return if (principal.payload.getClaim("pid") != null && !principal.payload.getClaim("pid").asString().isNullOrEmpty()) {
        log.debug("Bruker fnr fra pid-claim")
        principal.payload.getClaim("pid").asString()
    } else {
        log.debug("Bruker fnr fra subject")
        principal.payload.subject
    }
}

data class BrukerPrincipal(
    val fnr: String,
    val principal: JWTPrincipal,
    val token: String
) : Principal
