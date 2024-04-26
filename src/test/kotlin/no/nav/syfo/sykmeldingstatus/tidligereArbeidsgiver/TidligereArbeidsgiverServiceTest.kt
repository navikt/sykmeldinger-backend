package no.nav.syfo.sykmeldingstatus.tidligereArbeidsgiver

import io.mockk.clearAllMocks
import kotlin.test.assertNotNull
import no.nav.syfo.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.sykmeldingstatus.TestHelper.Companion.februar
import no.nav.syfo.sykmeldingstatus.TestHelper.Companion.januar
import no.nav.syfo.sykmeldingstatus.kafka.model.STATUS_APEN
import no.nav.syfo.sykmeldingstatus.kafka.model.STATUS_BEKREFTET
import no.nav.syfo.sykmeldingstatus.kafka.model.STATUS_SENDT
import no.nav.syfo.sykmeldingstatus.opprettSykmelding
import org.amshove.kluent.internal.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TidligereArbeidsgiverServiceTest {
    val tidligereArbeidsgiverService = TidligereArbeidsgiverService()
    val AG1 = "ag1"
    val AG2 = "ag2"

    @BeforeEach
    fun init() {
        clearAllMocks()
    }

    @Test
    fun `flere arbeidsgivere med arbeidsledig kant til kant`() {
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

        val currentSm =
            opprettSykmelding(
                fom = 17.januar(2023),
                tom = 31.januar(2023),
                sykmeldingId = "3",
                orgnummer = null,
                status = STATUS_APEN
            )

        val relevanteSykmeldinger =
            tidligereArbeidsgiverService.filterRelevantSykmeldinger(sykmeldingerFraDb, currentSm)
        assertNotNull(relevanteSykmeldinger)
        assertEquals(2, relevanteSykmeldinger.size)
        assertEquals(AG1, relevanteSykmeldinger.first().first.arbeidsgiver?.orgnummer)
        assertEquals(AG2, relevanteSykmeldinger.last().first.arbeidsgiver?.orgnummer)
    }

    @Test
    fun `flere arbeidsgivere med arbeidsledig forlengelse`() {
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
        val currentSm =
            opprettSykmelding(
                fom = 1.februar(2023),
                tom = 28.februar(2023),
                sykmeldingId = "4",
                orgnummer = null,
                status = STATUS_APEN
            )

        val relevanteSykmeldinger =
            tidligereArbeidsgiverService.filterRelevantSykmeldinger(sykmeldingerFraDb, currentSm)
        assertNotNull(relevanteSykmeldinger)
        assertEquals(1, relevanteSykmeldinger.size)
        assertEquals(AG1, relevanteSykmeldinger.first().first.tidligereArbeidsgiver?.orgnummer)
    }

    @Test
    fun `flere arbeidsgivere direkte overlappende sykmeldinger filtreres bort`() {
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
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "3",
                    orgnummer = AG1,
                    status = STATUS_SENDT
                ),
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "4",
                    orgnummer = AG2,
                    status = STATUS_SENDT
                ),
                opprettSykmelding(
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "5",
                    orgnummer = null,
                    status = STATUS_APEN,
                ),
            )
        val currentSm =
            opprettSykmelding(
                fom = 17.januar(2023),
                tom = 31.januar(2023),
                sykmeldingId = "5",
                orgnummer = null,
                status = STATUS_APEN,
            )

        val relevanteSykmeldinger =
            tidligereArbeidsgiverService.filterRelevantSykmeldinger(sykmeldingerFraDb, currentSm)
        assertNotNull(relevanteSykmeldinger)
        assertEquals(2, relevanteSykmeldinger.size)
    }

    @Test
    fun `flere arbeidsgivere direkte overlappende tidligere ag sykmeldinger filtreres bort`() {
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
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "3",
                    tidligereArbeidsgiver =
                        TidligereArbeidsgiverDTO(
                            orgNavn = AG1,
                            orgnummer = "ag1",
                            sykmeldingsId = "1"
                        ),
                    status = STATUS_BEKREFTET
                ),
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "4",
                    tidligereArbeidsgiver =
                        TidligereArbeidsgiverDTO(
                            orgNavn = AG2,
                            orgnummer = "ag2",
                            sykmeldingsId = "2"
                        ),
                    status = STATUS_BEKREFTET
                ),
                opprettSykmelding(
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "5",
                    orgnummer = null,
                    status = STATUS_APEN,
                ),
            )
        val currentSm =
            opprettSykmelding(
                fom = 17.januar(2023),
                tom = 31.januar(2023),
                sykmeldingId = "5",
                orgnummer = null,
                status = STATUS_APEN,
            )

        val relevanteSykmeldinger =
            tidligereArbeidsgiverService.filterRelevantSykmeldinger(sykmeldingerFraDb, currentSm)
        assertNotNull(relevanteSykmeldinger)
        assertEquals(2, relevanteSykmeldinger.size)
    }

    @Test
    fun `flere arbeidsgivere med arbeidsledig overlappende`() {
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

        val currentSm =
            opprettSykmelding(
                fom = 5.januar(2023),
                tom = 31.januar(2023),
                sykmeldingId = "3",
                orgnummer = null,
                status = STATUS_APEN
            )

        val relevanteSykmeldinger =
            tidligereArbeidsgiverService.filterRelevantSykmeldinger(sykmeldingerFraDb, currentSm)
        assertNotNull(relevanteSykmeldinger)
        assertEquals(2, relevanteSykmeldinger.size)
    }

    @Test
    fun `flere arbeidsgivere arbeidsledig ikke overlappende eller kant til kant `() {
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

        val currentSm =
            opprettSykmelding(
                fom = 18.januar(2023),
                tom = 31.januar(2023),
                sykmeldingId = "3",
                orgnummer = null,
                status = STATUS_APEN
            )

        val relevanteSykmeldinger =
            tidligereArbeidsgiverService.filterRelevantSykmeldinger(sykmeldingerFraDb, currentSm)
        assertNotNull(relevanteSykmeldinger)
        assertEquals(0, relevanteSykmeldinger.size)
    }

    @Test
    fun `Flere ag arbeidsledig fra ag1, forlengelse på arbeidsledig, men nå arbeidsledig fra ag2`() {
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
                            orgnummer = AG1,
                            sykmeldingsId = "1"
                        )
                ),
                opprettSykmelding(
                    fom = 1.februar(2023),
                    tom = 15.februar(2023),
                    sykmeldingId = "4",
                    orgnummer = null,
                    status = STATUS_APEN
                )
            )

        val currentSm =
            opprettSykmelding(
                fom = 1.februar(2023),
                tom = 15.februar(2023),
                sykmeldingId = "4",
                orgnummer = null,
                status = STATUS_APEN
            )

        val relevanteSykmeldinger =
            tidligereArbeidsgiverService.filterRelevantSykmeldinger(sykmeldingerFraDb, currentSm)
        assertNotNull(relevanteSykmeldinger)
        assertEquals(1, relevanteSykmeldinger.size)
        assertEquals(AG1, relevanteSykmeldinger.first().first.tidligereArbeidsgiver?.orgnummer)
    }

    @Test
    fun `Flere ag, arbeidsledig fra ag1, forlengelse på arbeidsledig ag1`() {
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
                            orgnummer = AG1,
                            sykmeldingsId = "1"
                        )
                ),
                opprettSykmelding(
                    fom = 1.februar(2023),
                    tom = 15.februar(2023),
                    sykmeldingId = "4",
                    orgnummer = null,
                    status = STATUS_APEN
                )
            )

        val currentSm =
            opprettSykmelding(
                fom = 1.februar(2023),
                tom = 15.februar(2023),
                sykmeldingId = "4",
                orgnummer = null,
                status = STATUS_APEN
            )

        val relevanteSykmeldinger =
            tidligereArbeidsgiverService.filterRelevantSykmeldinger(sykmeldingerFraDb, currentSm)
        assertNotNull(relevanteSykmeldinger)
        assertEquals(1, relevanteSykmeldinger.size)
        assertEquals(AG1, relevanteSykmeldinger.first().first.tidligereArbeidsgiver?.orgnummer)
    }

    @Test
    fun `Flere ag, kant til kant med ag1 - brukersvar null, og ag2, tidligere ag svar fra bruker er null og ag2`() {
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
                    tidligereArbeidsgiver = null
                ),
                opprettSykmelding(
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "4",
                    orgnummer = AG2,
                    status = STATUS_SENDT
                ),
                opprettSykmelding(
                    fom = 1.februar(2023),
                    tom = 15.februar(2023),
                    sykmeldingId = "5",
                    orgnummer = null,
                    status = STATUS_APEN,
                )
            )

        val currentSm =
            opprettSykmelding(
                fom = 1.februar(2023),
                tom = 15.februar(2023),
                sykmeldingId = "5",
                orgnummer = null,
                status = STATUS_APEN
            )

        val relevanteSykmeldinger =
            tidligereArbeidsgiverService.filterRelevantSykmeldinger(sykmeldingerFraDb, currentSm)
        assertNotNull(relevanteSykmeldinger)
        assertEquals(1, relevanteSykmeldinger.size)
    }

    @Test
    fun `En ag - brukersvar null - skal ikke kaste exception`() {
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
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "3",
                    orgnummer = null,
                    status = STATUS_APEN,
                ),
            )
        val currentSm =
            opprettSykmelding(
                fom = 17.januar(2023),
                tom = 31.januar(2023),
                sykmeldingId = "3",
                orgnummer = null,
                status = STATUS_APEN,
            )

        val relevanteSykmeldinger =
            tidligereArbeidsgiverService.filterRelevantSykmeldinger(sykmeldingerFraDb, currentSm)
        assertNotNull(relevanteSykmeldinger)
        assertEquals(1, relevanteSykmeldinger.size)
    }

    @Test
    fun `flere ag - skal ikke kaste exception`() {
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
                    orgnummer = null,
                    tidligereArbeidsgiver =
                        TidligereArbeidsgiverDTO(
                            orgNavn = AG1,
                            orgnummer = AG1,
                            sykmeldingsId = "1"
                        ),
                    status = STATUS_BEKREFTET,
                ),
                opprettSykmelding(
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "3",
                    orgnummer = null,
                    status = STATUS_APEN,
                )
            )
        val currentSm =
            opprettSykmelding(
                fom = 17.januar(2023),
                tom = 31.januar(2023),
                sykmeldingId = "3",
                orgnummer = null,
                status = STATUS_APEN,
            )

        val relevanteSykmeldinger =
            tidligereArbeidsgiverService.filterRelevantSykmeldinger(sykmeldingerFraDb, currentSm)
        assertNotNull(relevanteSykmeldinger)
        assertEquals(1, relevanteSykmeldinger.size)
    }

    @Test
    fun `flere ag - brukersvar ikke null - matche på brukersvar`() {
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
                    orgnummer = null,
                    tidligereArbeidsgiver =
                        TidligereArbeidsgiverDTO(
                            orgNavn = AG1,
                            orgnummer = AG1,
                            sykmeldingsId = "1"
                        ),
                    status = STATUS_BEKREFTET,
                ),
                opprettSykmelding(
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "3",
                    orgnummer = null,
                    status = STATUS_APEN,
                )
            )
        val currentSm =
            opprettSykmelding(
                fom = 17.januar(2023),
                tom = 31.januar(2023),
                sykmeldingId = "3",
                orgnummer = null,
                status = STATUS_APEN,
            )

        val relevanteSykmeldinger =
            tidligereArbeidsgiverService.filterRelevantSykmeldinger(sykmeldingerFraDb, currentSm)
        assertNotNull(relevanteSykmeldinger)
        assertEquals(1, relevanteSykmeldinger.size)
    }

    @Test
    fun `flere ag - brukersvar null - direkte overlapp`() {
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
                    orgnummer = null,
                    status = STATUS_APEN,
                )
            )
        val currentSm =
            opprettSykmelding(
                fom = 1.januar(2023),
                tom = 16.januar(2023),
                sykmeldingId = "2",
                orgnummer = null,
                status = STATUS_APEN,
            )

        val relevanteSykmeldinger =
            tidligereArbeidsgiverService.filterRelevantSykmeldinger(sykmeldingerFraDb, currentSm)
        assertNotNull(relevanteSykmeldinger)
        assertEquals(0, relevanteSykmeldinger.size)
    }

    @Test
    fun `flere ag - direkte overlapp`() {
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
                    orgnummer = null,
                    status = STATUS_APEN,
                )
            )
        val currentSm =
            opprettSykmelding(
                fom = 1.januar(2023),
                tom = 16.januar(2023),
                sykmeldingId = "2",
                orgnummer = null,
                status = STATUS_APEN,
            )

        val relevanteSykmeldinger =
            tidligereArbeidsgiverService.filterRelevantSykmeldinger(sykmeldingerFraDb, currentSm)
        assertNotNull(relevanteSykmeldinger)
        assertEquals(0, relevanteSykmeldinger.size)
    }
}
