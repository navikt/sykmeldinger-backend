package no.nav.syfo.sykmelding.model

import no.nav.syfo.log
import java.time.LocalDate

fun toDate(syketilfelleStartDato: LocalDate?): LocalDate? {
    return syketilfelleStartDato?.let {
        when (it.year) {
            in 0..9999 -> it
            else -> {
                log.warn("Ugyldig dato: $it")
                null
            }
        }
    }
}
