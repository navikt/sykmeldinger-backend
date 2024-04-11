package no.nav.syfo.sykmeldingstatus.db

import io.ktor.server.testing.*
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertFailsWith
import no.nav.syfo.sykmelding.model.RegelStatusDTO
import no.nav.syfo.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v2.Arbeidssituasjon
import no.nav.syfo.sykmeldingstatus.api.v2.Blad
import no.nav.syfo.sykmeldingstatus.api.v2.Egenmeldingsperiode
import no.nav.syfo.sykmeldingstatus.api.v2.FiskerSvar
import no.nav.syfo.sykmeldingstatus.api.v2.JaEllerNei
import no.nav.syfo.sykmeldingstatus.api.v2.LottOgHyre
import no.nav.syfo.sykmeldingstatus.api.v2.SporsmalSvar
import no.nav.syfo.sykmeldingstatus.api.v2.SykmeldingFormResponse
import no.nav.syfo.sykmeldingstatus.exception.SykmeldingStatusNotFoundException
import no.nav.syfo.sykmeldingstatus.kafka.model.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.testutils.TestDB
import no.nav.syfo.testutils.getBehandlingsutfall
import no.nav.syfo.testutils.getStatus
import no.nav.syfo.testutils.getSykmelding
import no.nav.syfo.testutils.insertBehandlingsutfall
import no.nav.syfo.testutils.insertStatus
import no.nav.syfo.testutils.insertSymelding
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SykmeldingStatusDbTest {
    val database = SykmeldingStatusDb(TestDB.database)

    @BeforeEach
    fun init() {
        TestDB.clearAllData()
    }

    @Test
    fun `test sykmeldingstatus not found`() = testApplication {
        assertFailsWith<SykmeldingStatusNotFoundException> { database.getLatestStatus("1", "fnr") }
    }

    @Test
    fun `test sykmeldingstatus with wrong fnr`() = testApplication {
        TestDB.database.insertStatus(
            "1",
            getStatus(StatusEventDTO.APEN.name, OffsetDateTime.now().minusDays(1)),
        )
        TestDB.database.insertBehandlingsutfall(
            "1",
            getBehandlingsutfall(RegelStatusDTO.OK),
        )
        TestDB.database.insertSymelding("1", "fnr2", getSykmelding())
        assertFailsWith<SykmeldingStatusNotFoundException> { database.getLatestStatus("1", "fnr") }
    }

    @Test
    fun `test get latest sykmeldingstatus`() = testApplication {
        val apenStatus = getStatus(StatusEventDTO.APEN.name, OffsetDateTime.now().minusDays(1))
        TestDB.database.insertStatus(
            "1",
            getStatus(StatusEventDTO.APEN.name, OffsetDateTime.now().minusDays(1)),
        )
        TestDB.database.insertBehandlingsutfall(
            "1",
            getBehandlingsutfall(RegelStatusDTO.OK),
        )
        TestDB.database.insertSymelding("1", "fnr", getSykmelding())

        val status = database.getLatestStatus("1", "fnr")
        status.statusEvent.name shouldBeEqualTo apenStatus.statusEvent
        status.erAvvist shouldBeEqualTo false
        status.erEgenmeldt shouldBeEqualTo false
    }

    @Test
    fun `test get latest avvist sykmeldingstatus`() = testApplication {
        val apenStatus = getStatus(StatusEventDTO.APEN.name, OffsetDateTime.now().minusDays(1))
        TestDB.database.insertStatus(
            "1",
            getStatus(StatusEventDTO.APEN.name, OffsetDateTime.now().minusDays(1)),
        )
        TestDB.database.insertBehandlingsutfall(
            "1",
            getBehandlingsutfall(RegelStatusDTO.INVALID),
        )
        TestDB.database.insertSymelding("1", "fnr", getSykmelding())

        val status = database.getLatestStatus("1", "fnr")
        status.statusEvent.name shouldBeEqualTo apenStatus.statusEvent
        status.erAvvist shouldBeEqualTo true
        status.erEgenmeldt shouldBeEqualTo false
    }

    @Test
    fun `test get latest egenmeldt sykmeldingstatus`() = testApplication {
        val apenStatus = getStatus(StatusEventDTO.APEN.name, OffsetDateTime.now().minusDays(1))
        TestDB.database.insertStatus(
            "1",
            getStatus(StatusEventDTO.APEN.name, OffsetDateTime.now().minusDays(1)),
        )
        TestDB.database.insertBehandlingsutfall(
            "1",
            getBehandlingsutfall(RegelStatusDTO.OK),
        )
        TestDB.database.insertSymelding(
            "1",
            "fnr",
            getSykmelding().copy(egenmeldt = true),
        )

        val status = database.getLatestStatus("1", "fnr")
        status.statusEvent.name shouldBeEqualTo apenStatus.statusEvent
        status.erAvvist shouldBeEqualTo false
        status.erEgenmeldt shouldBeEqualTo true
    }

    @Test
    fun `update and get latest sykmeldingstatus SENDT`() = testApplication {
        TestDB.database.insertStatus(
            "1",
            getStatus(StatusEventDTO.APEN.name, OffsetDateTime.now().minusDays(1)),
        )
        TestDB.database.insertBehandlingsutfall(
            "1",
            getBehandlingsutfall(RegelStatusDTO.OK),
        )
        TestDB.database.insertSymelding("1", "fnr", getSykmelding())
        TestDB.database.insertStatus(
            "1",
            getStatus(StatusEventDTO.SENDT.name, OffsetDateTime.now()),
        )
        val status = database.getLatestStatus("1", "fnr")
        status.statusEvent.name shouldBeEqualTo "SENDT"
        status.erAvvist shouldBeEqualTo false
        status.erEgenmeldt shouldBeEqualTo false
    }

    @Test
    fun `update and get latest sykmeldingstatus BEKREFTET`() = testApplication {
        TestDB.database.insertStatus(
            "1",
            getStatus(StatusEventDTO.APEN.name, OffsetDateTime.now().minusDays(1)),
        )
        TestDB.database.insertBehandlingsutfall(
            "1",
            getBehandlingsutfall(RegelStatusDTO.OK),
        )
        TestDB.database.insertSymelding("1", "fnr", getSykmelding())
        TestDB.database.insertStatus(
            "1",
            getStatus(
                StatusEventDTO.BEKREFTET.name,
                OffsetDateTime.now().minusHours(2),
            ),
        )
        val status = database.getLatestStatus("1", "fnr")
        status.statusEvent.name shouldBeEqualTo "BEKREFTET"
        status.erAvvist shouldBeEqualTo false
        status.erEgenmeldt shouldBeEqualTo false
    }

    @Test
    fun `update and get tidligere_arbeidsgiver`() = testApplication {
        TestDB.database.insertStatus(
            "1",
            getStatus(StatusEventDTO.APEN.name, OffsetDateTime.now().minusDays(1)),
        )
        TestDB.database.insertBehandlingsutfall(
            "1",
            getBehandlingsutfall(RegelStatusDTO.OK),
        )
        TestDB.database.insertSymelding("1", "fnr", getSykmelding())
        TestDB.database.insertStatus(
            "1",
            getStatus(
                StatusEventDTO.BEKREFTET.name,
                OffsetDateTime.now().minusHours(2),
            ),
            tidligereArbeidsgiver =
                TidligereArbeidsgiverDTO(
                    "orgNavn",
                    "orgnummer",
                    "1",
                ),
        )
        val (status) = database.getSykmeldingStatus("1", "fnr")
        status.tidligereArbeidsgiver?.orgnummer shouldBeEqualTo "orgnummer"
    }

    @Nested
    @DisplayName("Inserting status")
    inner class InsertStatusGroup {
        @Test
        fun `status for arbeidsledig should be inserted with form values`() = testApplication {
            TestDB.database.insertStatus(
                "1",
                getStatus(
                    StatusEventDTO.APEN.name,
                    OffsetDateTime.now().minusDays(1),
                ),
            )
            TestDB.database.insertBehandlingsutfall(
                "1",
                getBehandlingsutfall(RegelStatusDTO.OK),
            )
            TestDB.database.insertSymelding("1", "fnr", getSykmelding())

            val event =
                SykmeldingStatusKafkaEventDTO(
                    sykmeldingId = "1",
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                    statusEvent = StatusEventDTO.BEKREFTET.name,
                    arbeidsgiver = null,
                    sporsmals = null,
                )
            val formData =
                SykmeldingFormResponse(
                    erOpplysningeneRiktige =
                        SporsmalSvar(
                            sporsmaltekst = "Er opplysningene riktige?",
                            svar = JaEllerNei.JA,
                        ),
                    arbeidssituasjon =
                        SporsmalSvar(
                            sporsmaltekst = "Arbeidssituasjon",
                            svar = Arbeidssituasjon.ARBEIDSLEDIG,
                        ),
                    arbeidsledig = null,
                    uriktigeOpplysninger = null,
                    arbeidsgiverOrgnummer = null,
                    riktigNarmesteLeder = null,
                    harBruktEgenmelding = null,
                    egenmeldingsperioder = null,
                    harForsikring = null,
                    egenmeldingsdager = null,
                    harBruktEgenmeldingsdager = null,
                    fisker = null,
                )

            database.insertStatus(event, formData) {}

            val (status, formResponse) = database.getSykmeldingStatus("1", "fnr")

            status.statusEvent shouldBeEqualTo StatusEventDTO.BEKREFTET.name
            formResponse?.arbeidssituasjon?.svar shouldBeEqualTo Arbeidssituasjon.ARBEIDSLEDIG
            formResponse?.erOpplysningeneRiktige?.svar shouldBeEqualTo JaEllerNei.JA
        }

        @Test
        fun `status for arbeidstaker should be inserted with form values`() = testApplication {
            TestDB.database.insertStatus(
                "1",
                getStatus(
                    StatusEventDTO.APEN.name,
                    OffsetDateTime.now().minusDays(1),
                ),
            )
            TestDB.database.insertBehandlingsutfall(
                "1",
                getBehandlingsutfall(RegelStatusDTO.OK),
            )
            TestDB.database.insertSymelding("1", "fnr", getSykmelding())

            val event =
                SykmeldingStatusKafkaEventDTO(
                    sykmeldingId = "1",
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                    statusEvent = StatusEventDTO.BEKREFTET.name,
                    arbeidsgiver = null,
                    sporsmals = null,
                )
            val formData =
                SykmeldingFormResponse(
                    erOpplysningeneRiktige =
                        SporsmalSvar(
                            sporsmaltekst = "Er opplysningene riktige?",
                            svar = JaEllerNei.JA,
                        ),
                    arbeidssituasjon =
                        SporsmalSvar(
                            sporsmaltekst = "Arbeidssituasjon",
                            svar = Arbeidssituasjon.ARBEIDSTAKER,
                        ),
                    arbeidsgiverOrgnummer =
                        SporsmalSvar(
                            sporsmaltekst = "Arbeidsgiver",
                            svar = "123456789",
                        ),
                    riktigNarmesteLeder =
                        SporsmalSvar(
                            sporsmaltekst = "Riktig nærmeste leder",
                            svar = JaEllerNei.JA,
                        ),
                    harBruktEgenmeldingsdager =
                        SporsmalSvar(
                            sporsmaltekst = "Har du brukt egenmeldingsdager?",
                            svar = JaEllerNei.JA,
                        ),
                    egenmeldingsdager =
                        SporsmalSvar(
                            sporsmaltekst = "Når var du sykmeldt?",
                            svar =
                                listOf(
                                    OffsetDateTime.now().minusDays(6).toLocalDate(),
                                    OffsetDateTime.now().minusDays(5).toLocalDate(),
                                ),
                        ),
                    arbeidsledig = null,
                    uriktigeOpplysninger = null,
                    harBruktEgenmelding = null,
                    egenmeldingsperioder = null,
                    harForsikring = null,
                    fisker = null,
                )

            database.insertStatus(event, formData) {}

            val (status, formResponse) = database.getSykmeldingStatus("1", "fnr")

            status.statusEvent shouldBeEqualTo StatusEventDTO.BEKREFTET.name
            formResponse?.arbeidssituasjon?.svar shouldBeEqualTo Arbeidssituasjon.ARBEIDSTAKER
            formResponse?.erOpplysningeneRiktige?.svar shouldBeEqualTo JaEllerNei.JA
            formResponse?.arbeidsgiverOrgnummer?.svar shouldBeEqualTo "123456789"
            formResponse?.riktigNarmesteLeder?.svar shouldBeEqualTo JaEllerNei.JA
            formResponse?.harBruktEgenmeldingsdager?.svar shouldBeEqualTo JaEllerNei.JA
            formResponse?.egenmeldingsdager?.svar shouldBeEqualTo
                listOf(
                    OffsetDateTime.now().minusDays(6).toLocalDate(),
                    OffsetDateTime.now().minusDays(5).toLocalDate(),
                )
        }

        @Test
        fun `status for fisker on hyre (in other words arbeidstaker) should be inserted with form values`() =
            testApplication {
                TestDB.database.insertStatus(
                    "1",
                    getStatus(
                        StatusEventDTO.APEN.name,
                        OffsetDateTime.now().minusDays(1),
                    ),
                )
                TestDB.database.insertBehandlingsutfall(
                    "1",
                    getBehandlingsutfall(RegelStatusDTO.OK),
                )
                TestDB.database.insertSymelding("1", "fnr", getSykmelding())

                val event =
                    SykmeldingStatusKafkaEventDTO(
                        sykmeldingId = "1",
                        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                        statusEvent = StatusEventDTO.BEKREFTET.name,
                        arbeidsgiver = null,
                        sporsmals = null,
                    )
                val formData =
                    SykmeldingFormResponse(
                        erOpplysningeneRiktige =
                            SporsmalSvar(
                                sporsmaltekst = "Er opplysningene riktige?",
                                svar = JaEllerNei.JA,
                            ),
                        arbeidssituasjon =
                            SporsmalSvar(
                                sporsmaltekst = "Arbeidssituasjon",
                                svar = Arbeidssituasjon.FISKER,
                            ),
                        fisker =
                            FiskerSvar(
                                blad =
                                    SporsmalSvar(
                                        sporsmaltekst = "Blad",
                                        svar = Blad.A,
                                    ),
                                lottOgHyre =
                                    SporsmalSvar(
                                        sporsmaltekst = "Lott og hyre",
                                        svar = LottOgHyre.HYRE,
                                    ),
                            ),
                        arbeidsgiverOrgnummer =
                            SporsmalSvar(
                                sporsmaltekst = "Arbeidsgiver",
                                svar = "123456789",
                            ),
                        riktigNarmesteLeder =
                            SporsmalSvar(
                                sporsmaltekst = "Riktig nærmeste leder",
                                svar = JaEllerNei.JA,
                            ),
                        harBruktEgenmeldingsdager =
                            SporsmalSvar(
                                sporsmaltekst = "Har du brukt egenmeldingsdager?",
                                svar = JaEllerNei.JA,
                            ),
                        egenmeldingsdager =
                            SporsmalSvar(
                                sporsmaltekst = "Når var du sykmeldt?",
                                svar =
                                    listOf(
                                        OffsetDateTime.now().minusDays(6).toLocalDate(),
                                        OffsetDateTime.now().minusDays(5).toLocalDate(),
                                    ),
                            ),
                        uriktigeOpplysninger = null,
                        harBruktEgenmelding = null,
                        egenmeldingsperioder = null,
                        harForsikring = null,
                        arbeidsledig = null,
                    )

                database.insertStatus(event, formData) {}

                val (status, formResponse) = database.getSykmeldingStatus("1", "fnr")

                status.statusEvent shouldBeEqualTo StatusEventDTO.BEKREFTET.name
                formResponse?.arbeidssituasjon?.svar shouldBeEqualTo Arbeidssituasjon.FISKER
                formResponse?.erOpplysningeneRiktige?.svar shouldBeEqualTo JaEllerNei.JA
                formResponse?.fisker?.blad?.svar shouldBeEqualTo Blad.A
                formResponse?.fisker?.lottOgHyre?.svar shouldBeEqualTo LottOgHyre.HYRE
                formResponse?.arbeidsgiverOrgnummer?.svar shouldBeEqualTo "123456789"
                formResponse?.riktigNarmesteLeder?.svar shouldBeEqualTo JaEllerNei.JA
                formResponse?.harBruktEgenmeldingsdager?.svar shouldBeEqualTo JaEllerNei.JA
                formResponse?.egenmeldingsdager?.svar shouldBeEqualTo
                    listOf(
                        OffsetDateTime.now().minusDays(6).toLocalDate(),
                        OffsetDateTime.now().minusDays(5).toLocalDate(),
                    )
            }

        @Test
        fun `status for fisker on lott (in other words næringsdrivende) should be inserted with form values`() =
            testApplication {
                TestDB.database.insertStatus(
                    "1",
                    getStatus(
                        StatusEventDTO.APEN.name,
                        OffsetDateTime.now().minusDays(1),
                    ),
                )
                TestDB.database.insertBehandlingsutfall(
                    "1",
                    getBehandlingsutfall(RegelStatusDTO.OK),
                )
                TestDB.database.insertSymelding("1", "fnr", getSykmelding())

                val event =
                    SykmeldingStatusKafkaEventDTO(
                        sykmeldingId = "1",
                        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                        statusEvent = StatusEventDTO.BEKREFTET.name,
                        arbeidsgiver = null,
                        sporsmals = null,
                    )
                val formData =
                    SykmeldingFormResponse(
                        erOpplysningeneRiktige =
                            SporsmalSvar(
                                sporsmaltekst = "Er opplysningene riktige?",
                                svar = JaEllerNei.JA,
                            ),
                        arbeidssituasjon =
                            SporsmalSvar(
                                sporsmaltekst = "Arbeidssituasjon",
                                svar = Arbeidssituasjon.FISKER,
                            ),
                        fisker =
                            FiskerSvar(
                                blad =
                                    SporsmalSvar(
                                        sporsmaltekst = "Blad",
                                        svar = Blad.B,
                                    ),
                                lottOgHyre =
                                    SporsmalSvar(
                                        sporsmaltekst = "Lott og hyre",
                                        svar = LottOgHyre.LOTT,
                                    ),
                            ),
                        harBruktEgenmelding =
                            SporsmalSvar(
                                sporsmaltekst = "Har du brukt egenmelding?",
                                svar = JaEllerNei.JA,
                            ),
                        egenmeldingsperioder =
                            SporsmalSvar(
                                sporsmaltekst = "Når var du sykmeldt?",
                                svar =
                                    listOf(
                                        Egenmeldingsperiode(
                                            OffsetDateTime.now().minusDays(6).toLocalDate(),
                                            OffsetDateTime.now().minusDays(5).toLocalDate(),
                                        ),
                                    ),
                            ),
                        harForsikring =
                            SporsmalSvar(
                                sporsmaltekst = "Har du forsikring?",
                                svar = JaEllerNei.JA,
                            ),
                        arbeidsgiverOrgnummer = null,
                        riktigNarmesteLeder = null,
                        harBruktEgenmeldingsdager = null,
                        egenmeldingsdager = null,
                        uriktigeOpplysninger = null,
                        arbeidsledig = null,
                    )

                database.insertStatus(event, formData) {}

                val (status, formResponse) = database.getSykmeldingStatus("1", "fnr")

                status.statusEvent shouldBeEqualTo StatusEventDTO.BEKREFTET.name
                formResponse?.arbeidssituasjon?.svar shouldBeEqualTo Arbeidssituasjon.FISKER
                formResponse?.erOpplysningeneRiktige?.svar shouldBeEqualTo JaEllerNei.JA
                formResponse?.fisker?.blad?.svar shouldBeEqualTo Blad.B
                formResponse?.fisker?.lottOgHyre?.svar shouldBeEqualTo LottOgHyre.LOTT
                formResponse?.harBruktEgenmelding?.svar shouldBeEqualTo JaEllerNei.JA
                formResponse?.egenmeldingsperioder?.svar shouldBeEqualTo
                    listOf(
                        Egenmeldingsperiode(
                            OffsetDateTime.now().minusDays(6).toLocalDate(),
                            OffsetDateTime.now().minusDays(5).toLocalDate(),
                        ),
                    )
                formResponse?.harForsikring?.svar shouldBeEqualTo JaEllerNei.JA
            }
    }
}
