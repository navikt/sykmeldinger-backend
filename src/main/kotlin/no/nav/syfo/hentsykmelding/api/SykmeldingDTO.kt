package no.nav.syfo.hentsykmelding.api

import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.syfo.sykmeldingstatus.api.SykmeldingStatusDTO

abstract class SykmeldingDTO(
    val id: String,
    val mottattTidspunkt: LocalDateTime,
    val bekreftetDato: LocalDateTime?,
    val behandlingsutfall: BehandlingsutfallDTO,
    val legekontorOrgnummer: String?,
    val legeNavn: String?,
    val arbeidsgiver: ArbeidsgiverDTO?,
    val sykmeldingsperioder: List<SykmeldingsperiodeDTO>,
    val sykmeldingStatus: SykmeldingStatusDTO
)

class SkjermetSykmeldingDTO(
    id: String,
    mottattTidspunkt: LocalDateTime,
    bekreftetDato: LocalDateTime?,
    behandlingsutfall: BehandlingsutfallDTO,
    legekontorOrgnummer: String?,
    legeNavn: String?,
    arbeidsgiver: ArbeidsgiverDTO?,
    sykmeldingsperioder: List<SykmeldingsperiodeDTO>,
    sykmeldingStatus: SykmeldingStatusDTO
) : SykmeldingDTO(
        id,
        mottattTidspunkt,
        bekreftetDato,
        behandlingsutfall,
        legekontorOrgnummer,
        legeNavn,
        arbeidsgiver,
        sykmeldingsperioder,
        sykmeldingStatus
)

class FullstendigSykmeldingDTO(
    id: String,
    mottattTidspunkt: LocalDateTime,
    bekreftetDato: LocalDateTime?,
    behandlingsutfall: BehandlingsutfallDTO,
    legekontorOrgnummer: String?,
    legeNavn: String?,
    arbeidsgiver: ArbeidsgiverDTO?,
    sykmeldingsperioder: List<SykmeldingsperiodeDTO>,
    sykmeldingStatus: SykmeldingStatusDTO,
    val medisinskVurdering: MedisinskVurderingDTO
) : SykmeldingDTO(
        id,
        mottattTidspunkt,
        bekreftetDato,
        behandlingsutfall,
        legekontorOrgnummer,
        legeNavn,
        arbeidsgiver,
        sykmeldingsperioder,
        sykmeldingStatus
)

data class MedisinskVurderingDTO(
    val hovedDiagnose: DiagnoseDTO?,
    val biDiagnoser: List<DiagnoseDTO>
)

data class BehandlingsutfallDTO(
    val ruleHits: List<RegelinfoDTO>,
    val status: BehandlingsutfallStatusDTO
)

data class RegelinfoDTO(
    val messageForSender: String,
    val messageForUser: String,
    val ruleName: String,
    val ruleStatus: BehandlingsutfallStatus?
)

enum class BehandlingsutfallStatusDTO {
    OK, MANUAL_PROCESSING, INVALID
}

enum class BehandlingsutfallStatus {
    OK, MANUAL_PROCESSING, INVALID
}

data class ArbeidsgiverDTO(
    val navn: String,
    val stillingsprosent: Int?
)

data class SykmeldingsperiodeDTO(
    val fom: LocalDate,
    val tom: LocalDate,
    val gradert: GradertDTO?,
    val behandlingsdager: Int?,
    val innspillTilArbeidsgiver: String?,
    val type: PeriodetypeDTO
)

data class GradertDTO(
    val grad: Int,
    val reisetilskudd: Boolean
)

enum class PeriodetypeDTO {
    AKTIVITET_IKKE_MULIG,
    AVVENTENDE,
    BEHANDLINGSDAGER,
    GRADERT,
    REISETILSKUDD,
}

data class DiagnoseDTO(
    val diagnosekode: String,
    val diagnosesystem: String,
    val diagnosetekst: String
)
