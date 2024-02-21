package no.nav.syfo.sykmeldingstatus.kafka

import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SporsmalOgSvarDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.STATUS_APEN
import no.nav.syfo.sykmeldingstatus.kafka.model.STATUS_AVBRUTT
import no.nav.syfo.sykmeldingstatus.kafka.model.STATUS_BEKREFTET
import no.nav.syfo.sykmeldingstatus.kafka.model.ShortNameKafkaDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.SporsmalOgSvarKafkaDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.SvartypeKafkaDTO
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SykmeldingStatusKafkaMessageMapperSpek {
    val sykmeldingId = "id"

    @Nested
    @DisplayName("Test av tilSykmeldingStatusKafkaEventDTO - bekreft")
    inner class TestAvTilSykmeldingStatusKafkaEventDTOBekreft {

        @Test
        fun `Mapper SykmeldingBekreftEventDTO med spørsmål riktig`() {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            val sykmeldingBekreftEventDTO =
                SykmeldingBekreftEventDTO(timestamp, lagSporsmalOgSvarDTOListe())

            val sykmeldingStatusKafkaEventDTO =
                sykmeldingBekreftEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId)

            sykmeldingStatusKafkaEventDTO.sykmeldingId shouldBeEqualTo sykmeldingId
            sykmeldingStatusKafkaEventDTO.timestamp shouldBeEqualTo timestamp
            sykmeldingStatusKafkaEventDTO.statusEvent shouldBeEqualTo STATUS_BEKREFTET
            sykmeldingStatusKafkaEventDTO.arbeidsgiver shouldBeEqualTo null
            sykmeldingStatusKafkaEventDTO.sporsmals?.size shouldBeEqualTo 4
            sykmeldingStatusKafkaEventDTO.sporsmals!![0] shouldBeEqualTo
                SporsmalOgSvarKafkaDTO(
                    "Sykmeldt fra ",
                    ShortNameKafkaDTO.ARBEIDSSITUASJON,
                    SvartypeKafkaDTO.ARBEIDSSITUASJON,
                    "Frilanser",
                )
            sykmeldingStatusKafkaEventDTO.sporsmals!![1] shouldBeEqualTo
                SporsmalOgSvarKafkaDTO(
                    "Har forsikring?",
                    ShortNameKafkaDTO.FORSIKRING,
                    SvartypeKafkaDTO.JA_NEI,
                    "Ja",
                )
            sykmeldingStatusKafkaEventDTO.sporsmals!![2] shouldBeEqualTo
                SporsmalOgSvarKafkaDTO(
                    "Hatt fravær?",
                    ShortNameKafkaDTO.FRAVAER,
                    SvartypeKafkaDTO.JA_NEI,
                    "Ja",
                )
            sykmeldingStatusKafkaEventDTO.sporsmals!![3] shouldBeEqualTo
                SporsmalOgSvarKafkaDTO(
                    "Når hadde du fravær?",
                    ShortNameKafkaDTO.PERIODE,
                    SvartypeKafkaDTO.PERIODER,
                    "{[{\"fom\": \"2019-8-1\", \"tom\": \"2019-8-15\"}, {\"fom\": \"2019-9-1\", \"tom\": \"2019-9-3\"}]}",
                )
        }

        @Test
        fun `Mapper SykmeldingBekreftEventDTO uten spørsmål riktig`() {
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

        @Test
        fun `Mapper sykmeldingBekreftEventDTO med tom spørsmålsliste riktig`() {
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

    @Nested
    @DisplayName("Test av tilSykmeldingStatusKafkaEventDTO for SykmeldingStatusEventDTO")
    inner class TestAvTilSykmeldingStatusKafkaEventDTO {
        @Test
        fun `Mapper SykmeldingStatusEventDTO for AVBRUTT`() {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            val sykmeldingStatusEventDTO =
                SykmeldingStatusEventDTO(StatusEventDTO.AVBRUTT, timestamp)

            val sykmeldingStatusKafkaEventDTO =
                sykmeldingStatusEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId)

            sykmeldingStatusKafkaEventDTO.sykmeldingId shouldBeEqualTo sykmeldingId
            sykmeldingStatusKafkaEventDTO.timestamp shouldBeEqualTo timestamp
            sykmeldingStatusKafkaEventDTO.statusEvent shouldBeEqualTo STATUS_AVBRUTT
            sykmeldingStatusKafkaEventDTO.sporsmals shouldBeEqualTo null
            sykmeldingStatusKafkaEventDTO.arbeidsgiver shouldBeEqualTo null
        }

        @Test
        fun `Mapper SykmeldingStatusEventDTO for APEN riktig`() {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            val sykmeldingStatusEventDTO = SykmeldingStatusEventDTO(StatusEventDTO.APEN, timestamp)

            val sykmeldingStatusKafkaEventDTO =
                sykmeldingStatusEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId)

            sykmeldingStatusKafkaEventDTO.sykmeldingId shouldBeEqualTo sykmeldingId
            sykmeldingStatusKafkaEventDTO.timestamp shouldBeEqualTo timestamp
            sykmeldingStatusKafkaEventDTO.statusEvent shouldBeEqualTo STATUS_APEN
            sykmeldingStatusKafkaEventDTO.sporsmals shouldBeEqualTo null
            sykmeldingStatusKafkaEventDTO.arbeidsgiver shouldBeEqualTo null
        }
    }
}

fun lagSporsmalOgSvarDTOListe(): List<SporsmalOgSvarDTO> {
    return listOf(
        SporsmalOgSvarDTO(
            "Sykmeldt fra ",
            ShortNameDTO.ARBEIDSSITUASJON,
            SvartypeDTO.ARBEIDSSITUASJON,
            "Frilanser",
        ),
        SporsmalOgSvarDTO("Har forsikring?", ShortNameDTO.FORSIKRING, SvartypeDTO.JA_NEI, "Ja"),
        SporsmalOgSvarDTO("Hatt fravær?", ShortNameDTO.FRAVAER, SvartypeDTO.JA_NEI, "Ja"),
        SporsmalOgSvarDTO(
            "Når hadde du fravær?",
            ShortNameDTO.PERIODE,
            SvartypeDTO.PERIODER,
            "{[{\"fom\": \"2019-8-1\", \"tom\": \"2019-8-15\"}, {\"fom\": \"2019-9-1\", \"tom\": \"2019-9-3\"}]}",
        ),
    )
}
