package no.nav.syfo.sykmelding.model

import io.kotest.core.spec.style.FunSpec
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate

internal class SykmeldingMapperKtTest : FunSpec({
    test("Big dato should be 0") {
        val localDate = LocalDate.parse("+92020-01-01")
        val newDate = toDate(localDate)
        newDate shouldBeEqualTo null
    }
    test("regular date should be regular date") {
        val localDate = LocalDate.parse("2020-01-01")
        val newDate = toDate(localDate)
        newDate.toString() shouldBeEqualTo "2020-01-01"
    }
})
