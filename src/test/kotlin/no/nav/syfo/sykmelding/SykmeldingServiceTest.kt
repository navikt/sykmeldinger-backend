package no.nav.syfo.sykmelding

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.time.OffsetDateTime
import no.nav.syfo.model.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmeldingstatus.getSykmeldingDTO
import org.amshove.kluent.shouldBeEqualTo

class SykmeldingServiceTest :
    FunSpec({
        val sykmeldingDb = mockk<SykmeldingDb>()

        val sykmeldingService =
            SykmeldingService(
                sykmeldingDb,
            )

        beforeTest { clearAllMocks() }

        context("Get Sykmeldinger and latest status") {
            test("Get sykmeldinger") {
                val now = OffsetDateTime.now()
                val expected = getSykmeldingDTO(timestamps = now)

                coEvery { sykmeldingDb.getSykmeldinger(any()) } returns listOf(expected)
                val returndSykmelding = sykmeldingService.getSykmeldinger("12345678901")
                returndSykmelding shouldBeEqualTo listOf(expected)
            }
            test("Get sykmeldinger med tidligerearbeidsgiver felt") {
                val now = OffsetDateTime.now()
                val expected =
                    getSykmeldingDTO(
                        timestamps = now,
                        tidligereArbeidsgiver =
                            TidligereArbeidsgiverDTO("orgNavn", "orgnummer", "sykmeldingsId")
                    )

                coEvery { sykmeldingDb.getSykmeldinger(any()) } returns listOf(expected)
                val returndSykmelding = sykmeldingService.getSykmeldinger("12345678901")
                returndSykmelding shouldBeEqualTo listOf(expected)
                returndSykmelding.first().tidligereArbeidsgiver?.orgNavn shouldBeEqualTo "orgNavn"
            }
        }
    })
