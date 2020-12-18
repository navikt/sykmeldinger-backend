package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.Principal
import io.ktor.auth.jwt.JWTCredential
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.Environment
import no.nav.syfo.log

fun Application.setupAuth(loginserviceIdportenClientId: List<String>, jwkProvider: JwkProvider, issuer: String, env: Environment, stsOidcJwkProvider: JwkProvider) {
    install(Authentication) {
        jwt(name = "jwt") {
            verifier(jwkProvider, issuer)
            validate { credentials ->
                when {
                    hasLoginserviceIdportenClientIdAudience(credentials, loginserviceIdportenClientId) && erNiva4(credentials) -> JWTPrincipal(credentials.payload)
                    else -> unauthorized(credentials)
                }
            }
        }

        jwt(name = "oidc") {
            verifier(stsOidcJwkProvider, env.stsOidcIssuer)
            validate { credentials ->
                when {
                    isValidStsOidcToken(credentials, env) -> JWTPrincipal(credentials.payload)
                    else -> unauthorized(credentials)
                }
            }
        }
    }
}

fun isValidStsOidcToken(credentials: JWTCredential, env: Environment): Boolean {
    return credentials.payload.audience.contains(env.stsOidcAudience) &&
        "srvsyfoservice".equals(credentials.payload.getClaim("sub").asString())
}

fun unauthorized(credentials: JWTCredential): Principal? {
    log.warn(
        "Auth: Unexpected audience for jwt {}, {}",
        StructuredArguments.keyValue("issuer", credentials.payload.issuer),
        StructuredArguments.keyValue("audience", credentials.payload.audience)
    )
    return null
}

fun hasLoginserviceIdportenClientIdAudience(credentials: JWTCredential, loginserviceIdportenClientId: List<String>): Boolean {
    return loginserviceIdportenClientId.any { credentials.payload.audience.contains(it) }
}

fun erNiva4(credentials: JWTCredential): Boolean {
    return "Level4" == credentials.payload.getClaim("acr").asString()
}
