package no.nav.syfo.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun <T : Any> T.applog(): Logger {
    return LoggerFactory.getLogger(this.javaClass)
}

fun applog(name: String): Logger {
    return LoggerFactory.getLogger(name)
}

inline fun <reified T> T.teamLogger(): Logger =
    LoggerFactory.getLogger("teamlog.${T::class.java.name}")

fun teamlog(name: String): Logger {
    return LoggerFactory.getLogger("teamlog.$name")
}
