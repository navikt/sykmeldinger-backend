package no.nav.syfo.sykmeldingstatus.model

import io.mockk.clearAllMocks
import io.mockk.mockkClass
import kotlin.test.assertNotNull
import no.nav.syfo.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.sykmeldingstatus.TestHelper.Companion.februar
import no.nav.syfo.sykmeldingstatus.TestHelper.Companion.januar
import no.nav.syfo.sykmeldingstatus.db.SykmeldingStatusDb
import no.nav.syfo.sykmeldingstatus.kafka.model.STATUS_APEN
import no.nav.syfo.sykmeldingstatus.kafka.model.STATUS_BEKREFTET
import no.nav.syfo.sykmeldingstatus.kafka.model.STATUS_SENDT
import no.nav.syfo.sykmeldingstatus.opprettSykmelding
import org.amshove.kluent.internal.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TidligereArbeidsgiverTest {
    val sykmeldingStatusDb = mockkClass(SykmeldingStatusDb::class)
    val employmentHistoryRetriever = EmploymentHistoryRetriever(sykmeldingStatusDb)
    val AG1 = "ag1"
    val AG2 = "ag2"

    @BeforeEach
    fun init() {
        clearAllMocks()
    }

    @Test
    fun `flere arbeidsgivere med arbeidsledig kant til kant`() {
        val tidligereArbeidsgiverFraBruker = AG1
        val currentSykmeldingId = "3"
        val sykmeldingerFraDb =
            listOf(
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "1",
                    orgnummer = AG1,
                    status = STATUS_SENDT
                ),
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "2",
                    orgnummer = AG2,
                    status = STATUS_SENDT
                ),
                opprettSykmelding(
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "3",
                    orgnummer = null,
                    status = STATUS_APEN
                )
            )

        val sisteSykmelding =
            employmentHistoryRetriever.findLastCorrectSykmelding(
                sykmeldingerFraDb,
                currentSykmeldingId,
                tidligereArbeidsgiverFraBruker
            )
        assertNotNull(sisteSykmelding?.arbeidsgiver?.orgnummer)
        assertEquals(AG1, sisteSykmelding!!.arbeidsgiver!!.orgnummer)
        assertEquals("1", sisteSykmelding.sykmeldingId)
    }

    @Test
    fun `flere arbeidsgivere med arbeidsledig forlengelse`() {
        val tidligereArbeidsgiverFraBruker = AG1
        val currentSykmeldingId = "4"
        val sykmeldingerFraDb =
            listOf(
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "1",
                    orgnummer = AG1,
                    status = STATUS_SENDT
                ),
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "2",
                    orgnummer = AG2,
                    status = STATUS_SENDT
                ),
                opprettSykmelding(
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "3",
                    orgnummer = null,
                    status = STATUS_BEKREFTET,
                    tidligereArbeidsgiver =
                        TidligereArbeidsgiverDTO(
                            orgNavn = AG1,
                            orgnummer = "ag1",
                            sykmeldingsId = "1"
                        )
                ),
                opprettSykmelding(
                    fom = 1.februar(2023),
                    tom = 28.februar(2023),
                    sykmeldingId = "4",
                    orgnummer = null,
                    status = STATUS_APEN
                )
            )

        val sisteSykmelding =
            employmentHistoryRetriever.findLastCorrectSykmelding(
                sykmeldingerFraDb,
                currentSykmeldingId,
                tidligereArbeidsgiverFraBruker
            )
        assertNull(sisteSykmelding?.arbeidsgiver?.orgnummer)
        assertEquals(AG1, sisteSykmelding!!.tidligereArbeidsgiver!!.orgnummer)
    }

    @Test
    fun `flere arbeidsgivere med arbeidsledig overlappende`() {
        val tidligereArbeidsgiverFraBruker = AG1
        val currentSykmeldingId = "3"
        val sykmeldingerFraDb =
            listOf(
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "1",
                    orgnummer = AG1,
                    status = STATUS_SENDT
                ),
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "2",
                    orgnummer = AG2,
                    status = STATUS_SENDT
                ),
                opprettSykmelding(
                    fom = 5.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "3",
                    orgnummer = null,
                    status = STATUS_APEN
                ),
            )

        val sisteSykmelding =
            employmentHistoryRetriever.findLastCorrectSykmelding(
                sykmeldingerFraDb,
                currentSykmeldingId,
                tidligereArbeidsgiverFraBruker
            )
        assertEquals(AG1, sisteSykmelding!!.arbeidsgiver!!.orgnummer)
        assertEquals("1", sisteSykmelding.sykmeldingId)
    }

    @Test
    fun `flere arbeidsgivere arbeidsledig ikke overlappende eller kant til kant `() {
        val tidligereArbeidsgiverFraBruker = AG1
        val currentSykmeldingId = "3"
        val sykmeldingerFraDb =
            listOf(
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "1",
                    orgnummer = AG1,
                    status = STATUS_SENDT
                ),
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "2",
                    orgnummer = AG2,
                    status = STATUS_SENDT
                ),
                opprettSykmelding(
                    fom = 18.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "3",
                    orgnummer = null,
                    status = STATUS_APEN
                ),
            )

        val sisteSykmelding =
            employmentHistoryRetriever.findLastCorrectSykmelding(
                sykmeldingerFraDb,
                currentSykmeldingId,
                tidligereArbeidsgiverFraBruker
            )
        assertEquals(null, sisteSykmelding)
    }

    @Test
    fun `flere arbeidsgivere tidligereArbeidsgiverFraBruker er null `() {
        val currentSykmeldingId = "3"
        val sykmeldingerFraDb =
            listOf(
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "1",
                    orgnummer = AG1,
                    status = STATUS_SENDT
                ),
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "2",
                    orgnummer = AG2,
                    status = STATUS_SENDT
                ),
                opprettSykmelding(
                    fom = 18.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "3",
                    orgnummer = null,
                    status = STATUS_APEN
                ),
            )

        val sisteSykmelding =
            employmentHistoryRetriever.findLastCorrectSykmelding(
                sykmeldingerFraDb,
                currentSykmeldingId,
                null
            )
        assertEquals(null, sisteSykmelding)
    }
}
