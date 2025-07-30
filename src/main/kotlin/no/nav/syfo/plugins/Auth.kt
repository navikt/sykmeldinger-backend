package no.nav.syfo.plugins

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import java.net.URI
import java.time.Duration
import java.util.concurrent.TimeUnit
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.getWellKnownTokenX
import no.nav.syfo.utils.Environment
import no.nav.syfo.utils.applog
import org.koin.ktor.ext.inject

private val logger = applog("Application.configureAuth")

fun Application.configureAuth() {
    val config by inject<AuthConfiguration>()

    install(Authentication) {
        jwt(name = "tokenx") {
            authHeader {
                when (val token: String? = it.getToken()) {
                    null -> return@authHeader null
                    else -> return@authHeader HttpAuthHeader.Single("Bearer", token)
                }
            }
            verifier(config.jwkProviderTokenX, config.tokenXIssuer)
            validate { credentials ->
                when {
                    hasClientIdAudience(
                        credentials,
                        config.clientIdTokenX,
                    ) && erNiva4(credentials) -> {
                        val principal = JWTPrincipal(credentials.payload)
                        BrukerPrincipal(
                            fnr = finnFnrFraToken(principal),
                            principal = principal,
                            token = this.getToken()!!,
                        )
                    }
                    else -> unauthorized(credentials)
                }
            }
        }
    }
}

class AuthConfiguration(
    val jwkProviderTokenX: JwkProvider,
    val tokenXIssuer: String,
    val clientIdTokenX: String,
)

fun getProductionAuthConfig(env: Environment): AuthConfiguration {
    val wellKnownTokenX = getWellKnownTokenX(env.tokenXWellKnownUrl)
    val jwkProviderTokenX =
        JwkProviderBuilder(URI.create(wellKnownTokenX.jwks_uri).toURL())
            .cached(10, Duration.ofHours(24))
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()
    val tokenXIssuer: String = wellKnownTokenX.issuer

    return AuthConfiguration(
        jwkProviderTokenX = jwkProviderTokenX,
        tokenXIssuer = tokenXIssuer,
        clientIdTokenX = env.clientIdTokenX,
    )
}

fun ApplicationCall.getToken(): String? = request.header("Authorization")?.removePrefix("Bearer ")

fun hasClientIdAudience(credentials: JWTCredential, clientId: String): Boolean {
    return credentials.payload.audience.contains(clientId)
}

fun unauthorized(credentials: JWTCredential): Principal? {
    logger.warn(
        "Auth: Unexpected audience for jwt {}, {}",
        StructuredArguments.keyValue("issuer", credentials.payload.issuer),
        StructuredArguments.keyValue("audience", credentials.payload.audience),
    )
    return null
}

fun erNiva4(credentials: JWTCredential): Boolean {
    return "Level4" == credentials.payload.getClaim("acr").asString()
}

fun finnFnrFraToken(principal: JWTPrincipal): String {
    return if (
        principal.payload.getClaim("pid") != null &&
            !principal.payload.getClaim("pid").asString().isNullOrEmpty()
    ) {
        logger.debug("Bruker fnr fra pid-claim")
        principal.payload.getClaim("pid").asString()
    } else {
        logger.debug("Bruker fnr fra subject")
        principal.payload.subject
    }
}

data class BrukerPrincipal(
    val fnr: String,
    val principal: JWTPrincipal,
    val token: String,
) : Principal
