package no.nav.syfo.sykmelding.db

import io.kotest.core.spec.style.FunSpec
import java.time.OffsetDateTime
import java.util.UUID
import no.nav.syfo.sykmelding.model.RegelStatusDTO
import no.nav.syfo.sykmelding.model.ShortNameDTO
import no.nav.syfo.sykmelding.model.SporsmalDTO
import no.nav.syfo.sykmelding.model.SvarDTO
import no.nav.syfo.sykmelding.model.SvartypeDTO
import no.nav.syfo.sykmelding.model.UtenlandskSykmelding
import no.nav.syfo.sykmeldingstatus.TestHelper.Companion.januar
import no.nav.syfo.sykmeldingstatus.api.v1.ArbeidsgiverStatusDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.testutils.TestDB
import no.nav.syfo.testutils.getBehandlingsutfall
import no.nav.syfo.testutils.getStatus
import no.nav.syfo.testutils.getSykmelding
import no.nav.syfo.testutils.insertBehandlingsutfall
import no.nav.syfo.testutils.insertStatus
import no.nav.syfo.testutils.insertSykmeldt
import no.nav.syfo.testutils.insertSymelding
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate

class SykmeldingDbTest :
    FunSpec(
        {
            val testDb = TestDB.database
            val fnr = "12345678901"
            val sykmeldingId = UUID.randomUUID().toString()
            val sykmeldingDb = SykmeldingDb(testDb)
            beforeTest { TestDB.clearAllData() }
            context("Leser sykmeldinger fra DB") {
                test("Les sykmelding med status og behandlingsutfall") {
                    testDb.insertSymelding(sykmeldingId, fnr, getSykmelding())
                    testDb.insertStatus(
                        sykmeldingId,
                        getStatus(StatusEventDTO.SENDT.name, OffsetDateTime.now()),
                    )
                    testDb.insertStatus(
                        sykmeldingId,
                        getStatus(
                            StatusEventDTO.APEN.name,
                            OffsetDateTime.now().minusDays(1),
                        ),
                    )
                    testDb.insertBehandlingsutfall(
                        sykmeldingId,
                        getBehandlingsutfall(RegelStatusDTO.OK),
                    )
                    testDb.insertSykmeldt(fnr)
                    val sykmeldinger = sykmeldingDb.getSykmeldinger(fnr)
                    sykmeldinger.size shouldBeEqualTo 1
                    val sykmelding = sykmeldinger.first()
                    sykmelding.sykmeldingStatus.statusEvent shouldBeEqualTo
                        StatusEventDTO.SENDT.name
                    sykmelding.pasient.fornavn shouldBeEqualTo "fornavn"
                    sykmelding.pasient.etternavn shouldBeEqualTo "etternavn"
                    sykmelding.pasient.fnr shouldBeEqualTo fnr
                    sykmelding.pasient.mellomnavn shouldBeEqualTo "mellomnavn"
                }
                test("henter ikke sykmeldinger for pasienter som ikke er registrert i sykmeldt") {
                    testDb.insertSymelding(sykmeldingId, fnr, getSykmelding())
                    testDb.insertStatus(
                        sykmeldingId,
                        getStatus(StatusEventDTO.SENDT.name, OffsetDateTime.now()),
                    )
                    testDb.insertStatus(
                        sykmeldingId,
                        getStatus(
                            StatusEventDTO.APEN.name,
                            OffsetDateTime.now().minusDays(1),
                        ),
                    )
                    testDb.insertBehandlingsutfall(
                        sykmeldingId,
                        getBehandlingsutfall(RegelStatusDTO.OK),
                    )

                    val sykmeldinger = sykmeldingDb.getSykmeldinger(fnr)
                    sykmeldinger.size shouldBeEqualTo 0
                }
                test(
                    "henter ikke sykmeldinger for pasienter som ikke er registrert i med behandlingsutfall",
                ) {
                    testDb.insertSymelding(sykmeldingId, fnr, getSykmelding())
                    testDb.insertStatus(
                        sykmeldingId,
                        getStatus(StatusEventDTO.SENDT.name, OffsetDateTime.now()),
                    )
                    testDb.insertStatus(
                        sykmeldingId,
                        getStatus(
                            StatusEventDTO.APEN.name,
                            OffsetDateTime.now().minusDays(1),
                        ),
                    )
                    testDb.insertSykmeldt(fnr)
                    val sykmeldinger = sykmeldingDb.getSykmeldinger(fnr)
                    sykmeldinger.size shouldBeEqualTo 0
                }
                test(
                    "henter ikke sykmeldinger for pasienter som ikke er registrert med sykmeldingstatus",
                ) {
                    testDb.insertSymelding(sykmeldingId, fnr, getSykmelding())
                    testDb.insertSykmeldt(fnr)
                    testDb.insertBehandlingsutfall(
                        sykmeldingId,
                        getBehandlingsutfall(RegelStatusDTO.OK),
                    )
                    val sykmeldinger = sykmeldingDb.getSykmeldinger(fnr)
                    sykmeldinger.size shouldBeEqualTo 0
                }
                test("henter flere sykmeldinger") {
                    (0..10).forEach {
                        val id = UUID.randomUUID().toString()
                        testDb.insertSymelding(id, fnr, getSykmelding())
                        testDb.insertStatus(
                            id,
                            getStatus(
                                StatusEventDTO.APEN.name,
                                OffsetDateTime.now().minusDays(1),
                            ),
                        )
                        testDb.insertBehandlingsutfall(
                            id,
                            getBehandlingsutfall(RegelStatusDTO.OK),
                        )
                    }
                    testDb.insertSykmeldt(fnr)

                    val sykmeldinger = sykmeldingDb.getSykmeldinger(fnr)
                    sykmeldinger.size shouldBeEqualTo 11
                }
                test("hente nyeste status for sykmelding") {
                    val timestamp = OffsetDateTime.now()
                    testDb.insertSymelding(sykmeldingId, fnr, getSykmelding())
                    testDb.insertStatus(
                        sykmeldingId,
                        getStatus(StatusEventDTO.APEN.name, timestamp),
                    )
                    testDb.insertBehandlingsutfall(
                        sykmeldingId,
                        getBehandlingsutfall(RegelStatusDTO.OK),
                    )
                    testDb.insertSykmeldt(fnr)

                    val status = sykmeldingDb.getSykmeldinger(fnr).first().sykmeldingStatus
                    status.statusEvent shouldBeEqualTo StatusEventDTO.APEN.name
                    status.arbeidsgiver shouldBeEqualTo null
                    status.sporsmalOgSvarListe shouldBeEqualTo emptyList()

                    val arbeidsgiver =
                        ArbeidsgiverStatusDTO("orgnummer", "juridiskOrgnummer", "orgNavn")
                    val sporsmalOgSvar =
                        listOf(
                            SporsmalDTO(
                                tekst = "tekst",
                                shortName = ShortNameDTO.ARBEIDSSITUASJON,
                                svar = SvarDTO(SvartypeDTO.ARBEIDSSITUASJON, ""),
                            ),
                        )

                    testDb.insertStatus(
                        sykmeldingId,
                        getStatus(
                            StatusEventDTO.SENDT.name,
                            timestamp.plusSeconds(1),
                            arbeidsgiver,
                            sporsmalOgSvar,
                        ),
                    )

                    val sendtStatus = sykmeldingDb.getSykmeldinger(fnr).first().sykmeldingStatus
                    sendtStatus.statusEvent shouldBeEqualTo StatusEventDTO.SENDT.name
                    sendtStatus.arbeidsgiver shouldBeEqualTo arbeidsgiver
                    sendtStatus.sporsmalOgSvarListe shouldBeEqualTo sporsmalOgSvar
                }

                test("hent sykmelding med sykmeldingId") {
                    testDb.insertSymelding(sykmeldingId, fnr, getSykmelding())
                    testDb.insertStatus(
                        sykmeldingId,
                        getStatus(
                            StatusEventDTO.APEN.name,
                            OffsetDateTime.now().minusDays(1),
                        ),
                    )
                    testDb.insertBehandlingsutfall(
                        sykmeldingId,
                        getBehandlingsutfall(RegelStatusDTO.OK),
                    )
                    testDb.insertSykmeldt(fnr)

                    val sykmelding = sykmeldingDb.getSykmelding(sykmeldingId, fnr)!!
                    sykmelding.sykmeldingStatus.statusEvent shouldBeEqualTo StatusEventDTO.APEN.name
                    sykmelding.pasient.fornavn shouldBeEqualTo "fornavn"
                    sykmelding.pasient.etternavn shouldBeEqualTo "etternavn"
                    sykmelding.pasient.fnr shouldBeEqualTo fnr
                    sykmelding.pasient.mellomnavn shouldBeEqualTo "mellomnavn"
                }

                test("henter ikke sykmelding med sykmeldingId men feil fnr") {
                    testDb.insertSymelding(sykmeldingId, fnr, getSykmelding())
                    testDb.insertStatus(
                        sykmeldingId,
                        getStatus(
                            StatusEventDTO.APEN.name,
                            OffsetDateTime.now().minusDays(1),
                        ),
                    )
                    testDb.insertBehandlingsutfall(
                        sykmeldingId,
                        getBehandlingsutfall(RegelStatusDTO.OK),
                    )
                    testDb.insertSykmeldt(fnr)

                    val sykmelding = sykmeldingDb.getSykmelding(sykmeldingId, "feil")
                    sykmelding shouldBeEqualTo null
                }

                test("henter ikke sykmelding med manglende status") {
                    testDb.insertSymelding(sykmeldingId, fnr, getSykmelding())
                    testDb.insertBehandlingsutfall(
                        sykmeldingId,
                        getBehandlingsutfall(RegelStatusDTO.OK),
                    )
                    testDb.insertSykmeldt(fnr)

                    sykmeldingDb.getSykmelding(sykmeldingId, fnr) shouldBeEqualTo null
                }

                test("henter ikke sykmelding med manglende behandlingsutfall") {
                    testDb.insertSymelding(sykmeldingId, fnr, getSykmelding())
                    testDb.insertStatus(
                        sykmeldingId,
                        getStatus(
                            StatusEventDTO.APEN.name,
                            OffsetDateTime.now().minusDays(1),
                        ),
                    )
                    testDb.insertSykmeldt(fnr)

                    sykmeldingDb.getSykmelding(sykmeldingId, fnr) shouldBeEqualTo null
                }

                test("henter ikke sykmelding med manglende sykmeldt") {
                    testDb.insertSymelding(sykmeldingId, fnr, getSykmelding())
                    testDb.insertStatus(
                        sykmeldingId,
                        getStatus(
                            StatusEventDTO.APEN.name,
                            OffsetDateTime.now().minusDays(1),
                        ),
                    )
                    testDb.insertBehandlingsutfall(
                        sykmeldingId,
                        getBehandlingsutfall(RegelStatusDTO.OK),
                    )

                    sykmeldingDb.getSykmelding(sykmeldingId, fnr) shouldBeEqualTo null
                }

                test("UtenlandskSykelding") {
                    testDb.insertSymelding(
                        sykmeldingId,
                        fnr,
                        getSykmelding()
                            .copy(utenlandskSykmelding = UtenlandskSykmelding("Danmark")),
                    )
                    testDb.insertStatus(
                        sykmeldingId,
                        getStatus(
                            StatusEventDTO.APEN.name,
                            OffsetDateTime.now().minusDays(1),
                        ),
                    )
                    testDb.insertBehandlingsutfall(
                        sykmeldingId,
                        getBehandlingsutfall(RegelStatusDTO.OK),
                    )
                    testDb.insertSykmeldt(fnr)

                    val sykmelding = sykmeldingDb.getSykmelding(sykmeldingId, fnr)!!
                    sykmelding.utenlandskSykmelding shouldBeEqualTo UtenlandskSykmelding("Danmark")
                }

                test("Sykmeldt over 70") {
                    testDb.insertSymelding(
                        sykmeldingId,
                        fnr,
                        getSykmelding(),
                    )
                    testDb.insertStatus(
                        sykmeldingId,
                        getStatus(
                            StatusEventDTO.APEN.name,
                            OffsetDateTime.now().minusDays(1),
                        ),
                    )
                    testDb.insertBehandlingsutfall(
                        sykmeldingId,
                        getBehandlingsutfall(RegelStatusDTO.OK),
                    )
                    testDb.insertSykmeldt(fnr, 1.januar(1954))

                    val sykmelding = sykmeldingDb.getSykmelding(sykmeldingId, fnr)!!
                    sykmelding.pasient.overSyttiAar shouldBeEqualTo true
                }

                test("Sykmeldt under 70") {
                    testDb.insertSymelding(
                        sykmeldingId,
                        fnr,
                        getSykmelding(),
                    )
                    testDb.insertStatus(
                        sykmeldingId,
                        getStatus(
                            StatusEventDTO.APEN.name,
                            OffsetDateTime.now().minusDays(1),
                        ),
                    )
                    testDb.insertBehandlingsutfall(
                        sykmeldingId,
                        getBehandlingsutfall(RegelStatusDTO.OK),
                    )
                    testDb.insertSykmeldt(fnr, 1.januar(2000))

                    val sykmelding = sykmeldingDb.getSykmelding(sykmeldingId, fnr)!!
                    sykmelding.pasient.overSyttiAar shouldBeEqualTo false


                }
            }
        },
    )
