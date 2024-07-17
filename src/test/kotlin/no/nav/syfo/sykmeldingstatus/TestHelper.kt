package no.nav.syfo.sykmeldingstatus

import java.time.LocalDate

internal class TestHelper {

    companion object {
        internal fun Int.januar(year: Int) = LocalDate.of(year, 1, this)

        internal fun Int.februar(year: Int) = LocalDate.of(year, 2, this)

        internal fun Int.mars(year: Int) = LocalDate.of(year, 3, this)

        internal fun Int.juni(year: Int) = LocalDate.of(year, 6, this)
    }
}
