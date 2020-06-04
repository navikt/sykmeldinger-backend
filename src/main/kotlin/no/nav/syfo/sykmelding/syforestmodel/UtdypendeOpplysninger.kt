package no.nav.syfo.sykmelding.syforestmodel

data class UtdypendeOpplysninger(
    // Disse skal bort når syfofront / modiasyfofront er ute i ny versjon
    @Deprecated("")
    val sykehistorie: String? = null,
    @Deprecated("")
    val paavirkningArbeidsevne: String? = null,
    @Deprecated("")
    val resultatAvBehandling: String? = null,
    @Deprecated("")
    val henvisningUtredningBehandling: String? = null,
    val grupper: List<Sporsmalsgruppe> = ArrayList()
)
