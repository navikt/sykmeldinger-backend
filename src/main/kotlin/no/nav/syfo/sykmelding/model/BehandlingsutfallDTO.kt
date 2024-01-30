package no.nav.syfo.sykmelding.model

data class BehandlingsutfallDTO(
    var status: RegelStatusDTO,
    val ruleHits: List<RegelinfoDTO>,
)
