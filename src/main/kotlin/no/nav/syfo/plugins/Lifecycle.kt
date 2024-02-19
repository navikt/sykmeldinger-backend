package no.nav.syfo.plugins

import io.ktor.server.application.*
import org.koin.ktor.ext.inject

fun Application.configureLifecycleHooks() {
    val applicationState by inject<ApplicationState>()

    environment.monitor.subscribe(ApplicationStarted) { applicationState.ready = true }
    environment.monitor.subscribe(ApplicationStopped) { applicationState.ready = false }
}

data class ApplicationState(
    var alive: Boolean = true,
    var ready: Boolean = true,
)
