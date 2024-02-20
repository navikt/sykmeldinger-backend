package no.nav.syfo.sykmelding

import io.ktor.server.testing.*
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.time.OffsetDateTime
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmeldingstatus.getSykmeldingDTO
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SykmeldingServiceTest {
    val sykmeldingDb = mockk<SykmeldingDb>()
    val sykmeldingService =
        SykmeldingService(
            sykmeldingDb,
        )

    @BeforeEach
    fun init() {
        clearAllMocks()
    }

    @Test
    fun `Get Sykmeldinger and latest status`() = testApplication {
        val now = OffsetDateTime.now()
        val expected = getSykmeldingDTO(timestamps = now)

        coEvery { sykmeldingDb.getSykmeldinger(any()) } returns listOf(expected)
        val returndSykmelding = sykmeldingService.getSykmeldinger("12345678901")
        returndSykmelding shouldBeEqualTo listOf(expected)
    }
}
