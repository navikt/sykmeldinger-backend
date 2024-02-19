package no.nav.syfo.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import no.nav.syfo.brukerinformasjon.api.registrerBrukerinformasjonApi
import no.nav.syfo.metrics.monitorHttpRequests
import no.nav.syfo.sykmelding.api.registerSykmeldingApiV2
import no.nav.syfo.sykmeldingstatus.api.v2.registerSykmeldingAvbrytApiV2
import no.nav.syfo.sykmeldingstatus.api.v2.registerSykmeldingBekreftAvvistApiV2
import no.nav.syfo.sykmeldingstatus.api.v2.registerSykmeldingGjenapneApiV2
import no.nav.syfo.sykmeldingstatus.api.v2.registrerSykmeldingSendApiV3
import no.nav.syfo.utils.Environment
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val env by inject<Environment>()

    routing {
        if (env.cluster == "dev-gcp") {
            staticResources("/api/v1/docs/", "api") { default("api/index.html") }
        }
        authenticate("tokenx") {
            route("/api/v2") {
                registerSykmeldingApiV2()
                registerSykmeldingBekreftAvvistApiV2()
                registerSykmeldingAvbrytApiV2()
                registerSykmeldingGjenapneApiV2()
                registrerBrukerinformasjonApi()
            }
            route("/api/v3") { registrerSykmeldingSendApiV3() }
        }
    }

    intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
}
