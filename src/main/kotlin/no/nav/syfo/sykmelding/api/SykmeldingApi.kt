package no.nav.syfo.sykmelding.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.syfo.plugins.BrukerPrincipal
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.utils.logger
import no.nav.syfo.utils.securelog
import org.koin.ktor.ext.inject

fun Route.registerSykmeldingApiV2() {
    val logger = logger()
    val sykmeldingService by inject<SykmeldingService>()
    val sykmeldingStatusService by inject<SykmeldingStatusService>()

    get("/sykmeldinger") {
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr
        val sykmeldinger = sykmeldingService.getSykmeldinger(fnr = fnr)
        securelog.info(
            "getting sykmeldinger for fnr: $fnr, sykmeldingIds ${sykmeldinger.map { it.id }}",
        )
        call.respond(sykmeldinger)
    }

    get("/sykmeldinger/{sykmeldingid}/tidligere-arbeidsgivere") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr
        logger.info("Henter ut tidligere arbeidsgivere for sykmeldingid: $sykmeldingId")
        val tidligereArbeidsgivereList =
            sykmeldingStatusService.finnTidligereArbeidsgivere(fnr, sykmeldingId)
        if (!tidligereArbeidsgivereList.isNullOrEmpty()) {
            call.respond(tidligereArbeidsgivereList)
        }
    }

    get("/sykmeldinger/{sykmeldingid}") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr

        if (sykmeldingId == "null") {
            logger.warn("Mottok kall for å hente sykmelding med id null, sender 404 Not Found")
            call.respond(HttpStatusCode.NotFound)
        } else {
            logger.info("Henter ut sykmelding for sykmeldingid: $sykmeldingId")
            val sykmelding = sykmeldingService.getSykmelding(fnr, sykmeldingId)
            logger.info(
                "Er over 70 år : {} Og sykmeldingsId: {}",
                sykmelding?.pasient?.overSyttiAar,
                sykmeldingId
            )
            when (sykmelding) {
                null ->
                    call.respond(HttpStatusCode.NotFound).also {
                        sykmeldingService.logInfo(sykmeldingId, fnr)
                        securelog.info(
                            "Fikk null fra sql, prøvde å hente ut sykmelding for " +
                                "fnr: $fnr med sykmeldingid: $sykmeldingId",
                        )
                    }
                else -> call.respond(sykmelding)
            }
        }
    }
}
