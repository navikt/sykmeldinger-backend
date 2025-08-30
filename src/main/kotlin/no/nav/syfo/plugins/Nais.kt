package no.nav.syfo.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import java.util.*
import java.util.concurrent.ExecutionException
import no.nav.syfo.sykmelding.exception.setUpSykmeldingExceptionHandler
import no.nav.syfo.sykmeldingstatus.api.v2.setUpSykmeldingSendApiV2ExeptionHandler
import no.nav.syfo.sykmeldingstatus.exception.setUpSykmeldingStatusExeptionHandler
import no.nav.syfo.utils.applog
import org.koin.ktor.ext.inject

fun Application.configureNaisResources() {
    val logger = applog()
    val applicationState by inject<ApplicationState>()

    install(CallId) {
        generate { UUID.randomUUID().toString() }
        verify { callId: String -> callId.isNotEmpty() }
        header(HttpHeaders.XCorrelationId)
    }
    install(StatusPages) {
        setUpSykmeldingSendApiV2ExeptionHandler()
        setUpSykmeldingStatusExeptionHandler()
        setUpSykmeldingExceptionHandler()
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
            logger.error("Caught exception ${cause.message}", cause)
            if (cause is ExecutionException) {
                logger.error("Exception is ExecutionException, restarting", cause.cause)
                applicationState.ready = false
                applicationState.alive = false
            }
        }
    }

    routing { registerNaisApi(applicationState) }
}

private fun Routing.registerNaisApi(
    applicationState: ApplicationState,
    readynessCheck: () -> Boolean = { applicationState.ready },
    alivenessCheck: () -> Boolean = { applicationState.alive },
    collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry,
) {
    get("/internal/is_alive") {
        if (alivenessCheck()) {
            call.respondText("I'm alive! :)")
        } else {
            call.respondText("I'm dead x_x", status = HttpStatusCode.InternalServerError)
        }
    }
    get("/internal/is_ready") {
        if (readynessCheck()) {
            call.respondText("I'm ready! :)")
        } else {
            call.respondText(
                "Please wait! I'm not ready :(",
                status = HttpStatusCode.InternalServerError
            )
        }
    }
    get("/internal/prometheus") {
        val names = call.request.queryParameters.getAll("name[]")?.toSet() ?: setOf()
        call.respondTextWriter(ContentType.parse(TextFormat.CONTENT_TYPE_004)) {
            TextFormat.write004(this, collectorRegistry.filteredMetricFamilySamples(names))
        }
    }
}
