package no.nav.syfo.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nav.syfo.log
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Date
import java.util.UUID

class TokenXClient(
    private val tokendingsUrl: String,
    private val tokenXClientId: String,
    private val httpClient: HttpClient,
    privateKey: RSAKey
) {
    private val jwsSigner: JWSSigner
    private val algorithm: JWSAlgorithm = JWSAlgorithm.RS256
    private val jwsHeader: JWSHeader
    private val mutex = Mutex()

    @Volatile
    private var tokenMap = HashMap<String, AccessTokenMedExpiry>()

    init {
        jwsSigner = RSASSASigner(privateKey)
        jwsHeader = JWSHeader.Builder(algorithm)
            .keyID(privateKey.keyID)
            .type(JOSEObjectType.JWT)
            .build()
    }

    suspend fun getAccessToken(subjectToken: String, audience: String): String {
        val omToMinutter = Instant.now().plusSeconds(120L)
        val key = subjectToken + audience
        return mutex.withLock {
            (
                tokenMap[key]
                    ?.takeUnless { it.expiresOn.isBefore(omToMinutter) }
                    ?: run {
                        log.debug("Henter nytt token fra TokenX")
                        val response: AccessToken = httpClient.post(tokendingsUrl) {
                            accept(ContentType.Application.Json)
                            method = HttpMethod.Post
                            setBody(
                                FormDataContent(
                                    Parameters.build {
                                        append("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                                        append("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                                        append("client_assertion", getClientAssertion().serialize())
                                        append("subject_token_type", "urn:ietf:params:oauth:token-type:jwt")
                                        append("subject_token", subjectToken)
                                        append("audience", audience)
                                    }
                                )
                            )
                        }.body()
                        val tokenMedExpiry = AccessTokenMedExpiry(
                            access_token = response.access_token,
                            expires_in = response.expires_in,
                            expiresOn = Instant.now().plusSeconds(response.expires_in.toLong())
                        )
                        tokenMap[key] = tokenMedExpiry
                        log.debug("Har hentet accesstoken")
                        return@run tokenMedExpiry
                    }
                ).access_token
        }
    }

    private fun getClientAssertion(): SignedJWT {
        val jwtClaimSet = JWTClaimsSet.Builder()
            .audience(tokendingsUrl)
            .subject(tokenXClientId)
            .issuer(tokenXClientId)
            .jwtID(UUID.randomUUID().toString())
            .notBeforeTime(getNotBeforeTime())
            .expirationTime(getExpirationTime())
            .issueTime(getNotBeforeTime())
            .build()
        val signedJwt = SignedJWT(jwsHeader, jwtClaimSet)
        signedJwt.sign(jwsSigner)
        return signedJwt
    }

    private fun getNotBeforeTime(): Date {
        val now = LocalDateTime.now(Clock.systemUTC())
        return Date.from(now.toInstant(ZoneOffset.UTC))
    }

    private fun getExpirationTime(): Date {
        val exp = LocalDateTime.now(Clock.systemUTC()).plusSeconds(10)
        return Date.from(exp.toInstant(ZoneOffset.UTC))
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AccessToken(
    val access_token: String,
    val expires_in: Int
)

data class AccessTokenMedExpiry(
    val access_token: String,
    val expires_in: Int,
    val expiresOn: Instant
)
