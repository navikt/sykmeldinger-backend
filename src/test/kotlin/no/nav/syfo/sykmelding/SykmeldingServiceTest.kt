package no.nav.syfo.sykmelding

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmeldingstatus.getSykmeldingDTO
import org.amshove.kluent.shouldBeEqualTo
import java.time.OffsetDateTime

class SykmeldingServiceTest : FunSpec({
    val sykmeldingDb = mockk<SykmeldingDb>()

    val sykmeldingService = SykmeldingService(
        sykmeldingDb
    )

    beforeTest {
        clearAllMocks()
    }

    context("Get Sykmeldinger and latest status") {
        test("Get sykmeldinger") {
            val now = OffsetDateTime.now()
            val expected = getSykmeldingDTO(timestamps = now)

            coEvery { sykmeldingDb.getSykmeldinger(any()) } returns listOf(expected)
            val returndSykmelding = sykmeldingService.hentSykmeldinger("12345678901")
            returndSykmelding shouldBeEqualTo listOf(expected)
        }
    }
})
