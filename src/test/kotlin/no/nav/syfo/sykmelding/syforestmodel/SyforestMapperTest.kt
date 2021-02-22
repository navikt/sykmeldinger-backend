package no.nav.syfo.sykmelding.syforestmodel

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.model.sykmeldingstatus.STATUS_APEN
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.model.GradertDTO
import no.nav.syfo.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.model.SykmeldingStatusDTO
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SyforestMapperTest : Spek({

    describe("Sykmelding mappes til riktig syforest-aktig format") {
        it("Test av fullstendig, ny sykmelding") {
            val sykmeldingDTO: SykmeldingDTO = objectMapper.readValue(SyforestMapperTest::class.java.getResourceAsStream("/smFraRegister.json").readBytes().toString(Charsets.UTF_8))
            val syforestSykmeldingFasit: SyforestSykmelding = objectMapper.readValue(SyforestMapperTest::class.java.getResourceAsStream("/syforestSM.json").readBytes().toString(Charsets.UTF_8))
            val pasient = Pasient(fnr = "10987654321", fornavn = "Frida", mellomnavn = "Perma", etternavn = "Frost")

            val syforestSykmelding = tilSyforestSykmelding(sykmeldingDTO, pasient)

            syforestSykmelding.id shouldEqual syforestSykmeldingFasit.id
            syforestSykmelding.startLegemeldtFravaer shouldEqual syforestSykmeldingFasit.startLegemeldtFravaer
            syforestSykmelding.skalViseSkravertFelt shouldEqual syforestSykmeldingFasit.skalViseSkravertFelt
            syforestSykmelding.identdato shouldEqual syforestSykmeldingFasit.identdato
            syforestSykmelding.status shouldEqual syforestSykmeldingFasit.status
            syforestSykmelding.naermesteLederStatus shouldEqual syforestSykmeldingFasit.naermesteLederStatus
            syforestSykmelding.erEgenmeldt shouldEqual syforestSykmeldingFasit.erEgenmeldt
            syforestSykmelding.erPapirsykmelding shouldEqual syforestSykmeldingFasit.erPapirsykmelding
            syforestSykmelding.innsendtArbeidsgivernavn shouldEqual syforestSykmeldingFasit.innsendtArbeidsgivernavn
            syforestSykmelding.valgtArbeidssituasjon shouldEqual syforestSykmeldingFasit.valgtArbeidssituasjon
            syforestSykmelding.mottakendeArbeidsgiver shouldEqual syforestSykmeldingFasit.mottakendeArbeidsgiver
            syforestSykmelding.orgnummer shouldEqual syforestSykmeldingFasit.orgnummer
            syforestSykmelding.sendtdato shouldEqual syforestSykmeldingFasit.sendtdato
            syforestSykmelding.sporsmal shouldEqual syforestSykmeldingFasit.sporsmal
            syforestSykmelding.pasient shouldEqual syforestSykmeldingFasit.pasient
            syforestSykmelding.arbeidsgiver shouldEqual syforestSykmeldingFasit.arbeidsgiver
            syforestSykmelding.stillingsprosent shouldEqual syforestSykmeldingFasit.stillingsprosent
            syforestSykmelding.diagnose shouldEqual syforestSykmeldingFasit.diagnose
            syforestSykmelding.mulighetForArbeid shouldEqual syforestSykmeldingFasit.mulighetForArbeid
            syforestSykmelding.friskmelding shouldEqual syforestSykmeldingFasit.friskmelding
            syforestSykmelding.utdypendeOpplysninger shouldEqual syforestSykmeldingFasit.utdypendeOpplysninger
            syforestSykmelding.arbeidsevne shouldEqual syforestSykmeldingFasit.arbeidsevne
            syforestSykmelding.meldingTilNav shouldEqual syforestSykmeldingFasit.meldingTilNav
            syforestSykmelding.innspillTilArbeidsgiver shouldEqual syforestSykmeldingFasit.innspillTilArbeidsgiver
            syforestSykmelding.tilbakedatering shouldEqual syforestSykmeldingFasit.tilbakedatering
            syforestSykmelding.bekreftelse shouldEqual syforestSykmeldingFasit.bekreftelse
        }

        it("Test av fullstendig, sendt sykmelding") {
            val sykmeldingDTO: SykmeldingDTO = objectMapper.readValue(SyforestMapperTest::class.java.getResourceAsStream("/sendtSMFraRegister.json").readBytes().toString(Charsets.UTF_8))
            val syforestSykmeldingFasit: SyforestSykmelding = objectMapper.readValue(SyforestMapperTest::class.java.getResourceAsStream("/sendtSyforestSM.json").readBytes().toString(Charsets.UTF_8))
            val pasient = Pasient(fnr = "10987654321", fornavn = "Frida", mellomnavn = "Perma", etternavn = "Frost")

            val syforestSykmelding = tilSyforestSykmelding(sykmeldingDTO, pasient)

            syforestSykmelding.id shouldEqual syforestSykmeldingFasit.id
            syforestSykmelding.startLegemeldtFravaer shouldEqual syforestSykmeldingFasit.startLegemeldtFravaer
            syforestSykmelding.skalViseSkravertFelt shouldEqual syforestSykmeldingFasit.skalViseSkravertFelt
            syforestSykmelding.identdato shouldEqual syforestSykmeldingFasit.identdato
            syforestSykmelding.status shouldEqual syforestSykmeldingFasit.status
            syforestSykmelding.naermesteLederStatus shouldEqual syforestSykmeldingFasit.naermesteLederStatus
            syforestSykmelding.erEgenmeldt shouldEqual syforestSykmeldingFasit.erEgenmeldt
            syforestSykmelding.erPapirsykmelding shouldEqual syforestSykmeldingFasit.erPapirsykmelding
            syforestSykmelding.innsendtArbeidsgivernavn shouldEqual syforestSykmeldingFasit.innsendtArbeidsgivernavn
            syforestSykmelding.valgtArbeidssituasjon shouldEqual syforestSykmeldingFasit.valgtArbeidssituasjon
            syforestSykmelding.mottakendeArbeidsgiver shouldEqual syforestSykmeldingFasit.mottakendeArbeidsgiver
            syforestSykmelding.orgnummer shouldEqual syforestSykmeldingFasit.orgnummer
            syforestSykmelding.sendtdato shouldEqual syforestSykmeldingFasit.sendtdato
            syforestSykmelding.sporsmal shouldEqual syforestSykmeldingFasit.sporsmal
            syforestSykmelding.pasient shouldEqual syforestSykmeldingFasit.pasient
            syforestSykmelding.arbeidsgiver shouldEqual syforestSykmeldingFasit.arbeidsgiver
            syforestSykmelding.stillingsprosent shouldEqual syforestSykmeldingFasit.stillingsprosent
            syforestSykmelding.diagnose shouldEqual syforestSykmeldingFasit.diagnose
            syforestSykmelding.mulighetForArbeid shouldEqual syforestSykmeldingFasit.mulighetForArbeid
            syforestSykmelding.friskmelding shouldEqual syforestSykmeldingFasit.friskmelding
            syforestSykmelding.utdypendeOpplysninger shouldEqual syforestSykmeldingFasit.utdypendeOpplysninger
            syforestSykmelding.arbeidsevne shouldEqual syforestSykmeldingFasit.arbeidsevne
            syforestSykmelding.meldingTilNav shouldEqual syforestSykmeldingFasit.meldingTilNav
            syforestSykmelding.innspillTilArbeidsgiver shouldEqual syforestSykmeldingFasit.innspillTilArbeidsgiver
            syforestSykmelding.tilbakedatering shouldEqual syforestSykmeldingFasit.tilbakedatering
            syforestSykmelding.bekreftelse shouldEqual syforestSykmeldingFasit.bekreftelse
        }

        it("Setter status UTGAATT for ny sykmelding mottatt før 1/1 2020") {
            val status = tilStatus(SykmeldingStatusDTO(STATUS_APEN, OffsetDateTime.now(ZoneOffset.UTC).minusYears(1), null, emptyList()), LocalDate.of(2019, 12, 31))

            status shouldEqual "UTGAATT"
        }
        it("Setter status NY for ny sykmelding mottatt etter 1/1 2020") {
            val status = tilStatus(SykmeldingStatusDTO(STATUS_APEN, OffsetDateTime.now(ZoneOffset.UTC).minusYears(1), null, emptyList()), LocalDate.of(2020, 1, 1))

            status shouldEqual "NY"
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

            periode.reisetilskudd shouldEqual true
        }
    }
})
