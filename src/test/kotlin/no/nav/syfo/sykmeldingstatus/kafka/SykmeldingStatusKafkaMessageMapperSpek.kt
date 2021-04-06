package no.nav.syfo.sykmeldingstatus.kafka

import no.nav.syfo.model.sykmeldingstatus.STATUS_APEN
import no.nav.syfo.model.sykmeldingstatus.STATUS_AVBRUTT
import no.nav.syfo.model.sykmeldingstatus.STATUS_BEKREFTET
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.sykmeldingstatus.api.v1.ArbeidsgiverStatusDTO
import no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SporsmalOgSvarDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingSendEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO
import org.amshove.kluent.shouldBeEqualTo
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
                            SporsmalOgSvarDTO(
                                    "Arbeidssituasjon",
                                    ShortNameDTO.ARBEIDSSITUASJON,
                                    SvartypeDTO.ARBEIDSSITUASJON,
                                    "ARBEIDSTAKER"
                            ),
                            SporsmalOgSvarDTO("Nærmeste leder", ShortNameDTO.NY_NARMESTE_LEDER, SvartypeDTO.JA_NEI, "NEI")
                    )
            )

            val sykmeldingStatusKafkaEventDTO = sykmeldingSendEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId)

            sykmeldingStatusKafkaEventDTO.sykmeldingId shouldBeEqualTo sykmeldingId
            sykmeldingStatusKafkaEventDTO.timestamp shouldBeEqualTo timestamp
            sykmeldingStatusKafkaEventDTO.statusEvent shouldBeEqualTo STATUS_SENDT
            sykmeldingStatusKafkaEventDTO.sporsmals shouldBeEqualTo listOf(
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
            sykmeldingStatusKafkaEventDTO.arbeidsgiver shouldBeEqualTo no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO(
                orgnummer = "orgnummer",
                juridiskOrgnummer = null,
                orgNavn = "navn"
            )
        }
    }

    describe("Test av tilSykmeldingStatusKafkaEventDTO - bekreft") {
        it("Mapper SykmeldingBekreftEventDTO med spørsmål riktig") {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            val sykmeldingBekreftEventDTO = SykmeldingBekreftEventDTO(timestamp, lagSporsmalOgSvarDTOListe())

            val sykmeldingStatusKafkaEventDTO = sykmeldingBekreftEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId)

            sykmeldingStatusKafkaEventDTO.sykmeldingId shouldBeEqualTo sykmeldingId
            sykmeldingStatusKafkaEventDTO.timestamp shouldBeEqualTo timestamp
            sykmeldingStatusKafkaEventDTO.statusEvent shouldBeEqualTo STATUS_BEKREFTET
            sykmeldingStatusKafkaEventDTO.arbeidsgiver shouldBeEqualTo null
            sykmeldingStatusKafkaEventDTO.sporsmals?.size shouldBeEqualTo 4
            sykmeldingStatusKafkaEventDTO.sporsmals!![0] shouldBeEqualTo no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO(
                "Sykmeldt fra ",
                no.nav.syfo.model.sykmeldingstatus.ShortNameDTO.ARBEIDSSITUASJON,
                no.nav.syfo.model.sykmeldingstatus.SvartypeDTO.ARBEIDSSITUASJON,
                "Frilanser"
            )
            sykmeldingStatusKafkaEventDTO.sporsmals!![1] shouldBeEqualTo no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO(
                "Har forsikring?",
                no.nav.syfo.model.sykmeldingstatus.ShortNameDTO.FORSIKRING,
                no.nav.syfo.model.sykmeldingstatus.SvartypeDTO.JA_NEI,
                "Ja"
            )
            sykmeldingStatusKafkaEventDTO.sporsmals!![2] shouldBeEqualTo no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO(
                "Hatt fravær?",
                no.nav.syfo.model.sykmeldingstatus.ShortNameDTO.FRAVAER,
                no.nav.syfo.model.sykmeldingstatus.SvartypeDTO.JA_NEI,
                "Ja"
            )
            sykmeldingStatusKafkaEventDTO.sporsmals!![3] shouldBeEqualTo no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO(
                "Når hadde du fravær?",
                no.nav.syfo.model.sykmeldingstatus.ShortNameDTO.PERIODE,
                no.nav.syfo.model.sykmeldingstatus.SvartypeDTO.PERIODER,
                "{[{\"fom\": \"2019-8-1\", \"tom\": \"2019-8-15\"}, {\"fom\": \"2019-9-1\", \"tom\": \"2019-9-3\"}]}"
            )
        }

        it("Mapper SykmeldingBekreftEventDTO uten spørsmål riktig") {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            val sykmeldingBekreftEventDTOUtenSpm = SykmeldingBekreftEventDTO(timestamp, null)

            val sykmeldingStatusKafkaEventDTO =
                sykmeldingBekreftEventDTOUtenSpm.tilSykmeldingStatusKafkaEventDTO(sykmeldingId)

            sykmeldingStatusKafkaEventDTO.sykmeldingId shouldBeEqualTo sykmeldingId
            sykmeldingStatusKafkaEventDTO.timestamp shouldBeEqualTo timestamp
            sykmeldingStatusKafkaEventDTO.statusEvent shouldBeEqualTo STATUS_BEKREFTET
            sykmeldingStatusKafkaEventDTO.arbeidsgiver shouldBeEqualTo null
            sykmeldingStatusKafkaEventDTO.sporsmals shouldBeEqualTo null
        }

        it("Mapper sykmeldingBekreftEventDTO med tom spørsmålsliste riktig") {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            val sykmeldingBekreftEventDTOUtenSpm = SykmeldingBekreftEventDTO(timestamp, emptyList())

            val sykmeldingStatusKafkaEventDTO =
                sykmeldingBekreftEventDTOUtenSpm.tilSykmeldingStatusKafkaEventDTO(sykmeldingId)

            sykmeldingStatusKafkaEventDTO.sykmeldingId shouldBeEqualTo sykmeldingId
            sykmeldingStatusKafkaEventDTO.timestamp shouldBeEqualTo timestamp
            sykmeldingStatusKafkaEventDTO.statusEvent shouldBeEqualTo STATUS_BEKREFTET
            sykmeldingStatusKafkaEventDTO.arbeidsgiver shouldBeEqualTo null
            sykmeldingStatusKafkaEventDTO.sporsmals shouldBeEqualTo null
        }
    }

    describe("Test av tilSykmeldingStatusKafkaEventDTO for SykmeldingStatusEventDTO") {
        it("Mapper SykmeldingStatusEventDTO for AVBRUTT") {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            val sykmeldingStatusEventDTO =
                    SykmeldingStatusEventDTO(StatusEventDTO.AVBRUTT, timestamp)

            val sykmeldingStatusKafkaEventDTO = sykmeldingStatusEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId)

            sykmeldingStatusKafkaEventDTO.sykmeldingId shouldBeEqualTo sykmeldingId
            sykmeldingStatusKafkaEventDTO.timestamp shouldBeEqualTo timestamp
            sykmeldingStatusKafkaEventDTO.statusEvent shouldBeEqualTo STATUS_AVBRUTT
            sykmeldingStatusKafkaEventDTO.sporsmals shouldBeEqualTo null
            sykmeldingStatusKafkaEventDTO.arbeidsgiver shouldBeEqualTo null
        }

        it("Mapper SykmeldingStatusEventDTO for APEN riktig") {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            val sykmeldingStatusEventDTO =
                    SykmeldingStatusEventDTO(StatusEventDTO.APEN, timestamp)

            val sykmeldingStatusKafkaEventDTO = sykmeldingStatusEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId)

            sykmeldingStatusKafkaEventDTO.sykmeldingId shouldBeEqualTo sykmeldingId
            sykmeldingStatusKafkaEventDTO.timestamp shouldBeEqualTo timestamp
            sykmeldingStatusKafkaEventDTO.statusEvent shouldBeEqualTo STATUS_APEN
            sykmeldingStatusKafkaEventDTO.sporsmals shouldBeEqualTo null
            sykmeldingStatusKafkaEventDTO.arbeidsgiver shouldBeEqualTo null
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
