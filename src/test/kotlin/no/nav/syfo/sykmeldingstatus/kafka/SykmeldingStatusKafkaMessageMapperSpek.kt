package no.nav.syfo.sykmeldingstatus.kafka

import no.nav.syfo.model.sykmeldingstatus.STATUS_APEN
import no.nav.syfo.model.sykmeldingstatus.STATUS_AVBRUTT
import no.nav.syfo.model.sykmeldingstatus.STATUS_BEKREFTET
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.sykmeldingstatus.api.ArbeidsgiverStatusDTO
import no.nav.syfo.sykmeldingstatus.api.ShortNameDTO
import no.nav.syfo.sykmeldingstatus.api.SporsmalOgSvarDTO
import no.nav.syfo.sykmeldingstatus.api.SvartypeDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingSendEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingStatusEventDTO
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SykmeldingStatusKafkaMessageMapperSpek : Spek({
    val sykmeldingId = "id"

    describe("Test av tilSykmeldingStatusKafkaEventDTO - send") {
        it("Mapper SykmeldingSendEventDTO riktig") {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            val sykmeldingSendEventDTO = SykmeldingSendEventDTO(
                timestamp,
                ArbeidsgiverStatusDTO(orgnummer = "orgnummer", juridiskOrgnummer = null, orgNavn = "navn"),
                listOf(
                    SporsmalOgSvarDTO("Arbeidssituasjon", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "ARBEIDSTAKER"),
                    SporsmalOgSvarDTO("Nærmeste leder", ShortNameDTO.NY_NARMESTE_LEDER, SvartypeDTO.JA_NEI, "NEI")
                )
            )

            val sykmeldingStatusKafkaEventDTO = sykmeldingSendEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId)

            sykmeldingStatusKafkaEventDTO.sykmeldingId shouldEqual sykmeldingId
            sykmeldingStatusKafkaEventDTO.timestamp shouldEqual timestamp
            sykmeldingStatusKafkaEventDTO.statusEvent shouldEqual STATUS_SENDT
            sykmeldingStatusKafkaEventDTO.sporsmals shouldEqual listOf(
                no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO(
                    "Arbeidssituasjon",
                    no.nav.syfo.model.sykmeldingstatus.ShortNameDTO.ARBEIDSSITUASJON,
                    no.nav.syfo.model.sykmeldingstatus.SvartypeDTO.ARBEIDSSITUASJON, "ARBEIDSTAKER"
                ),
                no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO(
                    "Nærmeste leder",
                    no.nav.syfo.model.sykmeldingstatus.ShortNameDTO.NY_NARMESTE_LEDER,
                    no.nav.syfo.model.sykmeldingstatus.SvartypeDTO.JA_NEI, "NEI"
                )
            )
            sykmeldingStatusKafkaEventDTO.arbeidsgiver shouldEqual no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO(orgnummer = "orgnummer", juridiskOrgnummer = null, orgNavn = "navn")
        }
    }

    describe("Test av tilSykmeldingStatusKafkaEventDTO - bekreft") {
        it("Mapper SykmeldingBekreftEventDTO med spørsmål riktig") {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            val sykmeldingBekreftEventDTO = SykmeldingBekreftEventDTO(timestamp, lagSporsmalOgSvarDTOListe())

            val sykmeldingStatusKafkaEventDTO = sykmeldingBekreftEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId)

            sykmeldingStatusKafkaEventDTO.sykmeldingId shouldEqual sykmeldingId
            sykmeldingStatusKafkaEventDTO.timestamp shouldEqual timestamp
            sykmeldingStatusKafkaEventDTO.statusEvent shouldEqual STATUS_BEKREFTET
            sykmeldingStatusKafkaEventDTO.arbeidsgiver shouldEqual null
            sykmeldingStatusKafkaEventDTO.sporsmals?.size shouldEqual 4
            sykmeldingStatusKafkaEventDTO.sporsmals!![0] shouldEqual no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO(
                "Sykmeldt fra ",
                no.nav.syfo.model.sykmeldingstatus.ShortNameDTO.ARBEIDSSITUASJON,
                no.nav.syfo.model.sykmeldingstatus.SvartypeDTO.ARBEIDSSITUASJON,
                "Frilanser"
            )
            sykmeldingStatusKafkaEventDTO.sporsmals!![1] shouldEqual no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO(
                "Har forsikring?",
                no.nav.syfo.model.sykmeldingstatus.ShortNameDTO.FORSIKRING,
                no.nav.syfo.model.sykmeldingstatus.SvartypeDTO.JA_NEI,
                "Ja"
            )
            sykmeldingStatusKafkaEventDTO.sporsmals!![2] shouldEqual no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO(
                "Hatt fravær?",
                no.nav.syfo.model.sykmeldingstatus.ShortNameDTO.FRAVAER,
                no.nav.syfo.model.sykmeldingstatus.SvartypeDTO.JA_NEI,
                "Ja"
            )
            sykmeldingStatusKafkaEventDTO.sporsmals!![3] shouldEqual no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO(
                "Når hadde du fravær?",
                no.nav.syfo.model.sykmeldingstatus.ShortNameDTO.PERIODE,
                no.nav.syfo.model.sykmeldingstatus.SvartypeDTO.PERIODER,
                "{[{\"fom\": \"2019-8-1\", \"tom\": \"2019-8-15\"}, {\"fom\": \"2019-9-1\", \"tom\": \"2019-9-3\"}]}"
            )
        }

        it("Mapper SykmeldingBekreftEventDTO uten spørsmål riktig") {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            val sykmeldingBekreftEventDTOUtenSpm = SykmeldingBekreftEventDTO(timestamp, null)

            val sykmeldingStatusKafkaEventDTO = sykmeldingBekreftEventDTOUtenSpm.tilSykmeldingStatusKafkaEventDTO(sykmeldingId)

            sykmeldingStatusKafkaEventDTO.sykmeldingId shouldEqual sykmeldingId
            sykmeldingStatusKafkaEventDTO.timestamp shouldEqual timestamp
            sykmeldingStatusKafkaEventDTO.statusEvent shouldEqual STATUS_BEKREFTET
            sykmeldingStatusKafkaEventDTO.arbeidsgiver shouldEqual null
            sykmeldingStatusKafkaEventDTO.sporsmals shouldEqual null
        }

        it("Mapper sykmeldingBekreftEventDTO med tom spørsmålsliste riktig") {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            val sykmeldingBekreftEventDTOUtenSpm = SykmeldingBekreftEventDTO(timestamp, emptyList())

            val sykmeldingStatusKafkaEventDTO = sykmeldingBekreftEventDTOUtenSpm.tilSykmeldingStatusKafkaEventDTO(sykmeldingId)

            sykmeldingStatusKafkaEventDTO.sykmeldingId shouldEqual sykmeldingId
            sykmeldingStatusKafkaEventDTO.timestamp shouldEqual timestamp
            sykmeldingStatusKafkaEventDTO.statusEvent shouldEqual STATUS_BEKREFTET
            sykmeldingStatusKafkaEventDTO.arbeidsgiver shouldEqual null
            sykmeldingStatusKafkaEventDTO.sporsmals shouldEqual null
        }
    }

    describe("Test av tilSykmeldingStatusKafkaEventDTO for SykmeldingStatusEventDTO") {
        it("Mapper SykmeldingStatusEventDTO for AVBRUTT") {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            val sykmeldingStatusEventDTO = SykmeldingStatusEventDTO(no.nav.syfo.sykmeldingstatus.api.StatusEventDTO.AVBRUTT, timestamp)

            val sykmeldingStatusKafkaEventDTO = sykmeldingStatusEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId)

            sykmeldingStatusKafkaEventDTO.sykmeldingId shouldEqual sykmeldingId
            sykmeldingStatusKafkaEventDTO.timestamp shouldEqual timestamp
            sykmeldingStatusKafkaEventDTO.statusEvent shouldEqual STATUS_AVBRUTT
            sykmeldingStatusKafkaEventDTO.sporsmals shouldEqual null
            sykmeldingStatusKafkaEventDTO.arbeidsgiver shouldEqual null
        }

        it("Mapper SykmeldingStatusEventDTO for APEN riktig") {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            val sykmeldingStatusEventDTO = SykmeldingStatusEventDTO(no.nav.syfo.sykmeldingstatus.api.StatusEventDTO.APEN, timestamp)

            val sykmeldingStatusKafkaEventDTO = sykmeldingStatusEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId)

            sykmeldingStatusKafkaEventDTO.sykmeldingId shouldEqual sykmeldingId
            sykmeldingStatusKafkaEventDTO.timestamp shouldEqual timestamp
            sykmeldingStatusKafkaEventDTO.statusEvent shouldEqual STATUS_APEN
            sykmeldingStatusKafkaEventDTO.sporsmals shouldEqual null
            sykmeldingStatusKafkaEventDTO.arbeidsgiver shouldEqual null
        }
    }
})

fun lagSporsmalOgSvarDTOListe(): List<SporsmalOgSvarDTO> {
    return listOf(
        SporsmalOgSvarDTO("Sykmeldt fra ", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "Frilanser"),
        SporsmalOgSvarDTO("Har forsikring?", ShortNameDTO.FORSIKRING, SvartypeDTO.JA_NEI, "Ja"),
        SporsmalOgSvarDTO("Hatt fravær?", ShortNameDTO.FRAVAER, SvartypeDTO.JA_NEI, "Ja"),
        SporsmalOgSvarDTO("Når hadde du fravær?", ShortNameDTO.PERIODE, SvartypeDTO.PERIODER, "{[{\"fom\": \"2019-8-1\", \"tom\": \"2019-8-15\"}, {\"fom\": \"2019-9-1\", \"tom\": \"2019-9-3\"}]}")
    )
}
