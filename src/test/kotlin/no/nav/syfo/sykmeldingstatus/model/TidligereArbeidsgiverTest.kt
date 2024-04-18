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
    val tidligereArbeidsgiver = TidligereArbeidsgiver(sykmeldingStatusDb)
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
            tidligereArbeidsgiver.findLastRelevantSykmelding(
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
            tidligereArbeidsgiver.findLastRelevantSykmelding(
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
            tidligereArbeidsgiver.findLastRelevantSykmelding(
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
            tidligereArbeidsgiver.findLastRelevantSykmelding(
                sykmeldingerFraDb,
                currentSykmeldingId,
                tidligereArbeidsgiverFraBruker
            )
        assertEquals(null, sisteSykmelding)
    }

    @Test
    fun `Flere ag arbeidsledig fra ag1, forlengelse på arbeidsledig, men nå arbeidsledig fra ag2`() {
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

        val sisteSykmelding =
            tidligereArbeidsgiver.findLastRelevantSykmelding(
                sykmeldingerFraDb,
                currentSykmeldingId,
                AG2
            )
        assertEquals(null, sisteSykmelding)
    }

    @Test
    fun `Flere ag, arbeidsledig fra ag1, forlengelse på arbeidsledig ag1`() {
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

        val sisteSykmelding =
            tidligereArbeidsgiver.findLastRelevantSykmelding(
                sykmeldingerFraDb,
                currentSykmeldingId,
                AG1
            )
        assertNotNull(sisteSykmelding)
        sisteSykmelding.tidligereArbeidsgiver?.let { assertEquals(AG1, it.orgnummer) }
    }

    @Test
    fun `Flere ag, kant til kant med ag1 - brukersvar null, og ag2, tidligere ag svar fra bruker er null og ag2`() {
        val currentSykmeldingId = "5"
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

        val sisteSykmelding =
            tidligereArbeidsgiver.findLastRelevantSykmelding(
                sykmeldingerFraDb,
                currentSykmeldingId,
                AG2
            )
        assertEquals(AG2, sisteSykmelding?.arbeidsgiver?.orgnummer)
    }

    /*
        @Test
        fun `Flere ag - brukersvar null - skal kaste exception`() {
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
                        status = STATUS_APEN,
                    ),
                )

            val exception =
                assertFailsWith<UserInputFlereArbeidsgivereIsNullException> {
                    tidligereArbeidsgiver.findLastRelevantSykmelding(
                        sykmeldingerFraDb,
                        currentSykmeldingId,
                        null
                    )
                }
            exception.message shouldBeEqualTo
                "TidligereArbeidsgivereBrukerInput felt er null i flere-relevante-arbeidsgivere-flyten. Dette skal ikke være mulig for sykmeldingId $currentSykmeldingId"
        }
    */

    @Test
    fun `En ag - brukersvar null - skal ikke kaste exception`() {
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
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "3",
                    orgnummer = null,
                    status = STATUS_APEN,
                ),
            )
        tidligereArbeidsgiver.findLastRelevantSykmelding(
            sykmeldingerFraDb,
            currentSykmeldingId,
            null
        )
    }

    @Test
    fun `flere ag - brukersvar null - skal ikke kaste exception`() {
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
        val sisteSykmelding =
            tidligereArbeidsgiver.findLastRelevantSykmelding(
                sykmeldingerFraDb,
                currentSykmeldingId,
                null
            )
        assertEquals(AG1, sisteSykmelding?.arbeidsgiver?.orgnummer)
    }

    @Test
    fun `flere ag - brukersvar ikke null - matche på brukersvar`() {
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
        val sisteSykmelding =
            tidligereArbeidsgiver.findLastRelevantSykmelding(
                sykmeldingerFraDb,
                currentSykmeldingId,
                AG2
            )
        assertEquals(null, sisteSykmelding?.arbeidsgiver?.orgnummer)
    }

    @Test
    fun `flere ag - brukersvar null - direkte overlapp`() {
        val currentSykmeldingId = "2"
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
        val sisteSykmelding =
            tidligereArbeidsgiver.findLastRelevantSykmelding(
                sykmeldingerFraDb,
                currentSykmeldingId,
                null
            )
        assertEquals(null, sisteSykmelding?.arbeidsgiver?.orgnummer)
    }

    @Test
    fun `flere ag - brukersvar ikke null - direkte overlapp`() {
        val currentSykmeldingId = "2"
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
        val sisteSykmelding =
            tidligereArbeidsgiver.findLastRelevantSykmelding(
                sykmeldingerFraDb,
                currentSykmeldingId,
                AG1
            )
        assertEquals(null, sisteSykmelding?.arbeidsgiver?.orgnummer)
    }

    @Test
    fun `flere ag - to direkte overlappende bekreftede - tidligereag kun på en`() {
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
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "2",
                    orgnummer = null,
                    status = STATUS_BEKREFTET,
                ),
                opprettSykmelding(
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "3",
                    orgnummer = null,
                    status = STATUS_BEKREFTET,
                )
            )
        val sisteSykmelding =
            tidligereArbeidsgiver.findLastRelevantSykmelding(
                sykmeldingerFraDb,
                currentSykmeldingId,
                AG1
            )
        assertEquals(AG1, sisteSykmelding?.arbeidsgiver?.orgnummer)
    }

    @Test
    fun `flere ag - to direkte overlappende bekreftede - tidligereag allerede satt på en`() {
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
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "2",
                    tidligereArbeidsgiver =
                        TidligereArbeidsgiverDTO(
                            orgNavn = AG1,
                            orgnummer = AG1,
                            sykmeldingsId = "1"
                        ),
                    orgnummer = null,
                    status = STATUS_BEKREFTET,
                ),
                opprettSykmelding(
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "3",
                    orgnummer = null,
                    status = STATUS_BEKREFTET,
                )
            )
        val sisteSykmelding =
            tidligereArbeidsgiver.findLastRelevantSykmelding(
                sykmeldingerFraDb,
                currentSykmeldingId,
                AG1
            )
        assertEquals(null, sisteSykmelding?.arbeidsgiver?.orgnummer)
    }

    @Test
    fun `en ag - to direkte overlappende bekreftede - tidligereag allerede satt på en - brukerinput null`() {
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
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "2",
                    tidligereArbeidsgiver =
                        TidligereArbeidsgiverDTO(
                            orgNavn = AG1,
                            orgnummer = AG1,
                            sykmeldingsId = "1"
                        ),
                    orgnummer = null,
                    status = STATUS_BEKREFTET,
                ),
                opprettSykmelding(
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "3",
                    orgnummer = null,
                    status = STATUS_BEKREFTET,
                )
            )
        val sisteSykmelding =
            tidligereArbeidsgiver.findLastRelevantSykmelding(
                sykmeldingerFraDb,
                currentSykmeldingId,
                null
            )
        assertEquals(null, sisteSykmelding?.arbeidsgiver?.orgnummer)
    }

    @Test
    fun `en ag - to direkte overlappende bekreftede - tidligereag kun på en - brukerinput null`() {
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
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "2",
                    orgnummer = null,
                    status = STATUS_BEKREFTET,
                ),
                opprettSykmelding(
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "3",
                    orgnummer = null,
                    status = STATUS_BEKREFTET,
                )
            )
        val sisteSykmelding =
            tidligereArbeidsgiver.findLastRelevantSykmelding(
                sykmeldingerFraDb,
                currentSykmeldingId,
                null
            )
        assertEquals(AG1, sisteSykmelding?.arbeidsgiver?.orgnummer)
    }

    @Test
    fun `fire direkte overlappende bekreftede - tidligereag på en som ikke er kant til kant`() {
        val currentSykmeldingId = "3"
        val sykmeldingerFraDb =
            listOf(
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "1",
                    tidligereArbeidsgiver =
                        TidligereArbeidsgiverDTO(
                            orgNavn = AG1,
                            orgnummer = AG1,
                            sykmeldingsId = "1"
                        ),
                    orgnummer = null,
                    status = STATUS_BEKREFTET
                ),
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "2",
                    orgnummer = null,
                    status = STATUS_BEKREFTET,
                ),
                opprettSykmelding(
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "3",
                    orgnummer = null,
                    status = STATUS_BEKREFTET,
                ),
                opprettSykmelding(
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "4",
                    orgnummer = null,
                    status = STATUS_BEKREFTET,
                ),
                opprettSykmelding(
                    fom = 1.februar(2023),
                    tom = 15.februar(2023),
                    sykmeldingId = currentSykmeldingId,
                    orgnummer = null,
                    status = STATUS_APEN,
                )
            )
        val sisteSykmelding =
            tidligereArbeidsgiver.findLastRelevantSykmelding(
                sykmeldingerFraDb,
                currentSykmeldingId,
                AG1
            )
        assertEquals(null, sisteSykmelding?.tidligereArbeidsgiver?.orgnummer)
    }

    @Test
    fun `fire direkte overlappende bekreftede - tidligereag på ingen`() {
        val currentSykmeldingId = "3"
        val sykmeldingerFraDb =
            listOf(
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "1",
                    orgnummer = null,
                    status = STATUS_BEKREFTET
                ),
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "2",
                    orgnummer = null,
                    status = STATUS_BEKREFTET,
                ),
                opprettSykmelding(
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "3",
                    orgnummer = null,
                    status = STATUS_BEKREFTET,
                ),
                opprettSykmelding(
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "4",
                    orgnummer = null,
                    status = STATUS_BEKREFTET,
                ),
                opprettSykmelding(
                    fom = 1.februar(2023),
                    tom = 15.februar(2023),
                    sykmeldingId = currentSykmeldingId,
                    orgnummer = null,
                    status = STATUS_APEN,
                )
            )
        val sisteSykmelding =
            tidligereArbeidsgiver.findLastRelevantSykmelding(
                sykmeldingerFraDb,
                currentSykmeldingId,
                AG1
            )
        assertEquals(null, sisteSykmelding?.tidligereArbeidsgiver?.orgnummer)
    }

    @Test
    fun `fire direkte overlappende bekreftede - to forskjellige tidligere ag - skal kun ta med en`() {
        val currentSykmeldingId = "5"
        val sykmeldingerFraDb =
            listOf(
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "1",
                    tidligereArbeidsgiver =
                        TidligereArbeidsgiverDTO(
                            orgNavn = AG2,
                            orgnummer = AG2,
                            sykmeldingsId = "0"
                        ),
                    orgnummer = null,
                    status = STATUS_BEKREFTET
                ),
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "2",
                    tidligereArbeidsgiver =
                        TidligereArbeidsgiverDTO(
                            orgNavn = AG1,
                            orgnummer = AG1,
                            sykmeldingsId = "01"
                        ),
                    orgnummer = null,
                    status = STATUS_BEKREFTET,
                ),
                opprettSykmelding(
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "3",
                    tidligereArbeidsgiver =
                        TidligereArbeidsgiverDTO(
                            orgNavn = AG1,
                            orgnummer = AG1,
                            sykmeldingsId = "2"
                        ),
                    orgnummer = null,
                    status = STATUS_BEKREFTET,
                ),
                opprettSykmelding(
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "4",
                    orgnummer = null,
                    status = STATUS_BEKREFTET,
                ),
                opprettSykmelding(
                    fom = 1.februar(2023),
                    tom = 15.februar(2023),
                    sykmeldingId = currentSykmeldingId,
                    orgnummer = null,
                    status = STATUS_APEN,
                )
            )
        val sisteSykmelding =
            tidligereArbeidsgiver.findLastRelevantSykmelding(
                sykmeldingerFraDb,
                currentSykmeldingId,
                AG1
            )
        assertEquals(AG1, sisteSykmelding?.tidligereArbeidsgiver?.orgnummer)
    }

    @Test
    fun `fire direkte overlappende bekreftede - to forskjellige tidligere ag - brukerinput er null`() {
        val currentSykmeldingId = "5"
        val sykmeldingerFraDb =
            listOf(
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "1",
                    tidligereArbeidsgiver =
                        TidligereArbeidsgiverDTO(
                            orgNavn = AG2,
                            orgnummer = AG2,
                            sykmeldingsId = "0"
                        ),
                    orgnummer = null,
                    status = STATUS_BEKREFTET
                ),
                opprettSykmelding(
                    fom = 1.januar(2023),
                    tom = 16.januar(2023),
                    sykmeldingId = "2",
                    tidligereArbeidsgiver =
                        TidligereArbeidsgiverDTO(
                            orgNavn = AG1,
                            orgnummer = AG1,
                            sykmeldingsId = "01"
                        ),
                    orgnummer = null,
                    status = STATUS_BEKREFTET,
                ),
                opprettSykmelding(
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "3",
                    tidligereArbeidsgiver =
                        TidligereArbeidsgiverDTO(
                            orgNavn = AG1,
                            orgnummer = AG1,
                            sykmeldingsId = "2"
                        ),
                    orgnummer = null,
                    status = STATUS_BEKREFTET,
                ),
                opprettSykmelding(
                    fom = 17.januar(2023),
                    tom = 31.januar(2023),
                    sykmeldingId = "4",
                    orgnummer = null,
                    status = STATUS_BEKREFTET,
                ),
                opprettSykmelding(
                    fom = 1.februar(2023),
                    tom = 15.februar(2023),
                    sykmeldingId = currentSykmeldingId,
                    orgnummer = null,
                    status = STATUS_APEN,
                )
            )
        val sisteSykmelding =
            tidligereArbeidsgiver.findLastRelevantSykmelding(
                sykmeldingerFraDb,
                currentSykmeldingId,
                null
            )
        assertEquals(AG1, sisteSykmelding?.tidligereArbeidsgiver?.orgnummer)
    }
}
