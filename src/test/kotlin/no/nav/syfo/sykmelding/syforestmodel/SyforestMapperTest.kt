package no.nav.syfo.sykmelding.syforestmodel

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.model.sykmeldingstatus.STATUS_APEN
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.model.GradertDTO
import no.nav.syfo.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.model.Sykmelding
import no.nav.syfo.sykmelding.model.SykmeldingStatusDTO
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SyforestMapperTest : Spek({

    describe("Sykmelding mappes til riktig syforest-aktig format") {
        it("Test av fullstendig, ny sykmelding") {
            val sykmelding: Sykmelding = objectMapper.readValue(
                SyforestMapperTest::class.java.getResourceAsStream("/smFraRegister.json").readBytes()
                    .toString(Charsets.UTF_8)
            )
            val syforestSykmeldingFasit: SyforestSykmelding = objectMapper.readValue(
                SyforestMapperTest::class.java.getResourceAsStream("/syforestSM.json").readBytes()
                    .toString(Charsets.UTF_8)
            )
            val pasient = Pasient(fnr = "10987654321", fornavn = "Frida", mellomnavn = "Perma", etternavn = "Frost")

            val syforestSykmelding = tilSyforestSykmelding(sykmelding, pasient, false)

            syforestSykmelding.id shouldBeEqualTo syforestSykmeldingFasit.id
            syforestSykmelding.startLegemeldtFravaer shouldBeEqualTo syforestSykmeldingFasit.startLegemeldtFravaer
            syforestSykmelding.skalViseSkravertFelt shouldBeEqualTo syforestSykmeldingFasit.skalViseSkravertFelt
            syforestSykmelding.identdato shouldBeEqualTo syforestSykmeldingFasit.identdato
            syforestSykmelding.status shouldBeEqualTo syforestSykmeldingFasit.status
            syforestSykmelding.naermesteLederStatus shouldBeEqualTo syforestSykmeldingFasit.naermesteLederStatus
            syforestSykmelding.erEgenmeldt shouldBeEqualTo syforestSykmeldingFasit.erEgenmeldt
            syforestSykmelding.erPapirsykmelding shouldBeEqualTo syforestSykmeldingFasit.erPapirsykmelding
            syforestSykmelding.innsendtArbeidsgivernavn shouldBeEqualTo syforestSykmeldingFasit.innsendtArbeidsgivernavn
            syforestSykmelding.valgtArbeidssituasjon shouldBeEqualTo syforestSykmeldingFasit.valgtArbeidssituasjon
            syforestSykmelding.mottakendeArbeidsgiver shouldBeEqualTo syforestSykmeldingFasit.mottakendeArbeidsgiver
            syforestSykmelding.orgnummer shouldBeEqualTo syforestSykmeldingFasit.orgnummer
            syforestSykmelding.sendtdato shouldBeEqualTo syforestSykmeldingFasit.sendtdato
            syforestSykmelding.sporsmal shouldBeEqualTo syforestSykmeldingFasit.sporsmal
            syforestSykmelding.pasient shouldBeEqualTo syforestSykmeldingFasit.pasient
            syforestSykmelding.arbeidsgiver shouldBeEqualTo syforestSykmeldingFasit.arbeidsgiver
            syforestSykmelding.stillingsprosent shouldBeEqualTo syforestSykmeldingFasit.stillingsprosent
            syforestSykmelding.diagnose shouldBeEqualTo syforestSykmeldingFasit.diagnose
            syforestSykmelding.mulighetForArbeid shouldBeEqualTo syforestSykmeldingFasit.mulighetForArbeid
            syforestSykmelding.friskmelding shouldBeEqualTo syforestSykmeldingFasit.friskmelding
            syforestSykmelding.utdypendeOpplysninger shouldBeEqualTo syforestSykmeldingFasit.utdypendeOpplysninger
            syforestSykmelding.arbeidsevne shouldBeEqualTo syforestSykmeldingFasit.arbeidsevne
            syforestSykmelding.meldingTilNav shouldBeEqualTo syforestSykmeldingFasit.meldingTilNav
            syforestSykmelding.innspillTilArbeidsgiver shouldBeEqualTo syforestSykmeldingFasit.innspillTilArbeidsgiver
            syforestSykmelding.tilbakedatering shouldBeEqualTo syforestSykmeldingFasit.tilbakedatering
            syforestSykmelding.bekreftelse shouldBeEqualTo syforestSykmeldingFasit.bekreftelse
        }

        it("Test av fullstendig, sendt sykmelding") {
            val sykmelding: Sykmelding = objectMapper.readValue(
                SyforestMapperTest::class.java.getResourceAsStream("/sendtSMFraRegister.json").readBytes()
                    .toString(Charsets.UTF_8)
            )
            val syforestSykmeldingFasit: SyforestSykmelding = objectMapper.readValue(
                SyforestMapperTest::class.java.getResourceAsStream("/sendtSyforestSM.json").readBytes()
                    .toString(Charsets.UTF_8)
            )
            val pasient = Pasient(fnr = "10987654321", fornavn = "Frida", mellomnavn = "Perma", etternavn = "Frost")

            val syforestSykmelding = tilSyforestSykmelding(sykmelding, pasient, false)

            syforestSykmelding.id shouldBeEqualTo syforestSykmeldingFasit.id
            syforestSykmelding.startLegemeldtFravaer shouldBeEqualTo syforestSykmeldingFasit.startLegemeldtFravaer
            syforestSykmelding.skalViseSkravertFelt shouldBeEqualTo syforestSykmeldingFasit.skalViseSkravertFelt
            syforestSykmelding.identdato shouldBeEqualTo syforestSykmeldingFasit.identdato
            syforestSykmelding.status shouldBeEqualTo syforestSykmeldingFasit.status
            syforestSykmelding.naermesteLederStatus shouldBeEqualTo syforestSykmeldingFasit.naermesteLederStatus
            syforestSykmelding.erEgenmeldt shouldBeEqualTo syforestSykmeldingFasit.erEgenmeldt
            syforestSykmelding.erPapirsykmelding shouldBeEqualTo syforestSykmeldingFasit.erPapirsykmelding
            syforestSykmelding.innsendtArbeidsgivernavn shouldBeEqualTo syforestSykmeldingFasit.innsendtArbeidsgivernavn
            syforestSykmelding.valgtArbeidssituasjon shouldBeEqualTo syforestSykmeldingFasit.valgtArbeidssituasjon
            syforestSykmelding.mottakendeArbeidsgiver shouldBeEqualTo syforestSykmeldingFasit.mottakendeArbeidsgiver
            syforestSykmelding.orgnummer shouldBeEqualTo syforestSykmeldingFasit.orgnummer
            syforestSykmelding.sendtdato shouldBeEqualTo syforestSykmeldingFasit.sendtdato
            syforestSykmelding.sporsmal shouldBeEqualTo syforestSykmeldingFasit.sporsmal
            syforestSykmelding.pasient shouldBeEqualTo syforestSykmeldingFasit.pasient
            syforestSykmelding.arbeidsgiver shouldBeEqualTo syforestSykmeldingFasit.arbeidsgiver
            syforestSykmelding.stillingsprosent shouldBeEqualTo syforestSykmeldingFasit.stillingsprosent
            syforestSykmelding.diagnose shouldBeEqualTo syforestSykmeldingFasit.diagnose
            syforestSykmelding.mulighetForArbeid shouldBeEqualTo syforestSykmeldingFasit.mulighetForArbeid
            syforestSykmelding.friskmelding shouldBeEqualTo syforestSykmeldingFasit.friskmelding
            syforestSykmelding.utdypendeOpplysninger shouldBeEqualTo syforestSykmeldingFasit.utdypendeOpplysninger
            syforestSykmelding.arbeidsevne shouldBeEqualTo syforestSykmeldingFasit.arbeidsevne
            syforestSykmelding.meldingTilNav shouldBeEqualTo syforestSykmeldingFasit.meldingTilNav
            syforestSykmelding.innspillTilArbeidsgiver shouldBeEqualTo syforestSykmeldingFasit.innspillTilArbeidsgiver
            syforestSykmelding.tilbakedatering shouldBeEqualTo syforestSykmeldingFasit.tilbakedatering
            syforestSykmelding.bekreftelse shouldBeEqualTo syforestSykmeldingFasit.bekreftelse
        }

        it("Test av arbeidsgivervisning for fullstendig, ny sykmelding") {
            val sykmelding: Sykmelding = objectMapper.readValue(
                SyforestMapperTest::class.java.getResourceAsStream("/smFraRegister.json").readBytes()
                    .toString(Charsets.UTF_8)
            )
            val syforestSykmeldingFasit: SyforestSykmelding = objectMapper.readValue(
                SyforestMapperTest::class.java.getResourceAsStream("/arbeidsgiverSyforestSM.json").readBytes()
                    .toString(Charsets.UTF_8)
            )
            val pasient = Pasient(fnr = "10987654321", fornavn = "Frida", mellomnavn = "Perma", etternavn = "Frost")

            val syforestSykmelding = tilSyforestSykmelding(sykmelding, pasient, true)

            syforestSykmelding.id shouldBeEqualTo syforestSykmeldingFasit.id
            syforestSykmelding.startLegemeldtFravaer shouldBeEqualTo syforestSykmeldingFasit.startLegemeldtFravaer
            syforestSykmelding.skalViseSkravertFelt shouldBeEqualTo syforestSykmeldingFasit.skalViseSkravertFelt
            syforestSykmelding.identdato shouldBeEqualTo syforestSykmeldingFasit.identdato
            syforestSykmelding.status shouldBeEqualTo syforestSykmeldingFasit.status
            syforestSykmelding.naermesteLederStatus shouldBeEqualTo syforestSykmeldingFasit.naermesteLederStatus
            syforestSykmelding.erEgenmeldt shouldBeEqualTo syforestSykmeldingFasit.erEgenmeldt
            syforestSykmelding.erPapirsykmelding shouldBeEqualTo syforestSykmeldingFasit.erPapirsykmelding
            syforestSykmelding.innsendtArbeidsgivernavn shouldBeEqualTo syforestSykmeldingFasit.innsendtArbeidsgivernavn
            syforestSykmelding.valgtArbeidssituasjon shouldBeEqualTo syforestSykmeldingFasit.valgtArbeidssituasjon
            syforestSykmelding.mottakendeArbeidsgiver shouldBeEqualTo syforestSykmeldingFasit.mottakendeArbeidsgiver
            syforestSykmelding.orgnummer shouldBeEqualTo syforestSykmeldingFasit.orgnummer
            syforestSykmelding.sendtdato shouldBeEqualTo syforestSykmeldingFasit.sendtdato
            syforestSykmelding.sporsmal shouldBeEqualTo syforestSykmeldingFasit.sporsmal
            syforestSykmelding.pasient shouldBeEqualTo syforestSykmeldingFasit.pasient
            syforestSykmelding.arbeidsgiver shouldBeEqualTo syforestSykmeldingFasit.arbeidsgiver
            syforestSykmelding.stillingsprosent shouldBeEqualTo syforestSykmeldingFasit.stillingsprosent
            syforestSykmelding.diagnose shouldBeEqualTo syforestSykmeldingFasit.diagnose
            syforestSykmelding.mulighetForArbeid shouldBeEqualTo syforestSykmeldingFasit.mulighetForArbeid
            syforestSykmelding.friskmelding shouldBeEqualTo syforestSykmeldingFasit.friskmelding
            syforestSykmelding.utdypendeOpplysninger shouldBeEqualTo syforestSykmeldingFasit.utdypendeOpplysninger
            syforestSykmelding.arbeidsevne shouldBeEqualTo syforestSykmeldingFasit.arbeidsevne
            syforestSykmelding.meldingTilNav shouldBeEqualTo syforestSykmeldingFasit.meldingTilNav
            syforestSykmelding.innspillTilArbeidsgiver shouldBeEqualTo syforestSykmeldingFasit.innspillTilArbeidsgiver
            syforestSykmelding.tilbakedatering shouldBeEqualTo syforestSykmeldingFasit.tilbakedatering
            syforestSykmelding.bekreftelse shouldBeEqualTo syforestSykmeldingFasit.bekreftelse
        }

        it("Setter status UTGAATT for ny sykmelding mottatt før 1/1 2020") {
            val status = tilStatus(
                SykmeldingStatusDTO(
                    STATUS_APEN,
                    OffsetDateTime.now(ZoneOffset.UTC).minusYears(1),
                    null,
                    emptyList()
                ),
                LocalDate.of(2019, 12, 31)
            )

            status shouldBeEqualTo "UTGAATT"
        }
        it("Setter status NY for ny sykmelding mottatt etter 1/1 2020") {
            val status = tilStatus(
                SykmeldingStatusDTO(
                    STATUS_APEN,
                    OffsetDateTime.now(ZoneOffset.UTC).minusYears(1),
                    null,
                    emptyList()
                ),
                LocalDate.of(2020, 1, 1)
            )

            status shouldBeEqualTo "NY"
        }

        it("Setter reisetilskudd til true hvis periode er gradert med reisetilskudd") {
            val sykmeldingsperiodeDTO = SykmeldingsperiodeDTO(
                fom = LocalDate.now(),
                tom = LocalDate.now(),
                gradert = GradertDTO(
                    grad = 50,
                    reisetilskudd = true
                ),
                type = PeriodetypeDTO.GRADERT,
                aktivitetIkkeMulig = null,
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false
            )
            val periode = tilPeriode(sykmeldingsperiodeDTO, false)

            periode.reisetilskudd shouldBeEqualTo true
        }
    }
})
