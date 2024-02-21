package no.nav.syfo

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.plugins.*
import no.nav.syfo.utils.Environment

fun main() {
    val env = Environment()

    embeddedServer(Netty, port = env.applicationPort, module = Application::module).start(true)
}

fun Application.module() {
    configureModules()
    configureContentNegotiation()
    configureAuth()
    configurePrometheus()
    configureNaisResources()
    configureRouting()
    configureLifecycleHooks()
}
