package no.nav.syfo.pdl.service

import no.nav.syfo.log
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.Gradering
import no.nav.syfo.pdl.error.PersonNotFoundInPdl
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.redis.PdlPersonRedisService
import no.nav.syfo.pdl.redis.toPdlPerson
import no.nav.syfo.pdl.redis.toPdlPersonRedisModel
import no.nav.syfo.tokenx.TokenXClient

const val AKTORID_GRUPPE = "AKTORID"

class PdlPersonService(
    private val pdlClient: PdlClient,
    private val pdlPersonRedisService: PdlPersonRedisService,
    private val tokenXClient: TokenXClient,
    private val audience: String
) {

    suspend fun getPerson(fnr: String, userToken: String, callId: String): PdlPerson {
        val personFraRedis = getPersonFromRedis(fnr)
        if (personFraRedis != null) {
            log.debug("Fant person i redis")
            return personFraRedis
        }

        val token = tokenXClient.getAccessToken(
            subjectToken = userToken,
            audience = audience
        )
        val pdlResponse = pdlClient.getPersonTokenX(fnr, token)

        if (pdlResponse.errors != null) {
            pdlResponse.errors.forEach {
                log.error("PDL returnerte feilmelding: ${it.message}, ${it.extensions?.code}, $callId")
                it.extensions?.details?.let { details -> log.error("Type: ${details.type}, cause: ${details.cause}, policy: ${details.policy}, $callId") }
            }
        }
        if (pdlResponse.data.person == null) {
            log.error("Fant ikke person i PDL {}", callId)
            throw PersonNotFoundInPdl("Fant ikke person i PDL")
        }
        if (pdlResponse.data.person.navn.isNullOrEmpty()) {
            log.error("Fant ikke navn på person i PDL {}", callId)
            throw PersonNotFoundInPdl("Fant ikke navn på person i PDL")
        }
        val aktorId = pdlResponse.data.identer?.identer?.firstOrNull() { it.gruppe == AKTORID_GRUPPE }?.ident
        if (aktorId == null) {
            log.error("Fant ikke aktør i PDL {}", callId)
            throw PersonNotFoundInPdl("Fant ikke aktør i PDL")
        }

        val diskresjonskode = pdlResponse.data.person.adressebeskyttelse?.any { adressebeskyttelse ->
            adressebeskyttelse.gradering == Gradering.STRENGT_FORTROLIG ||
                adressebeskyttelse.gradering == Gradering.STRENGT_FORTROLIG_UTLAND
        } ?: false

        val pdlPerson = PdlPerson(getNavn(pdlResponse.data.person.navn[0]), aktorId, diskresjonskode)
        pdlPersonRedisService.updatePerson(pdlPerson.toPdlPersonRedisModel(), fnr)
        return pdlPerson
    }

    private fun getNavn(navn: no.nav.syfo.pdl.client.model.Navn): Navn {
        return Navn(fornavn = navn.fornavn, mellomnavn = navn.mellomnavn, etternavn = navn.etternavn)
    }

    private fun getPersonFromRedis(fnr: String): PdlPerson? {
        return pdlPersonRedisService.getPerson(fnr)?.toPdlPerson()
    }
}
