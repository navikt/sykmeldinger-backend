package no.nav.syfo.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Histogram

const val METRICS_NS = "sykmeldingerbackend"

val HTTP_HISTOGRAM: Histogram =
    Histogram.Builder()
        .labelNames("path")
        .name("requests_duration_seconds")
        .help("http requests durations for incoming requests in seconds")
        .register()

val BEKREFTET_AV_BRUKER_COUNTER: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("bekreftet_counter")
        .help("Antall sykmeldinger bekreftet av bruker")
        .register()

val BEKREFTET_AVVIST_AV_BRUKER_COUNTER: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("bekreftet_avvist_counter")
        .help("Antall sykmeldinger bekreftet avvist av bruker")
        .register()

val AVBRUTT_AV_BRUKER_COUNTER: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("avbrutt_counter")
        .help("Antall sykmeldinger avbrutt av bruker")
        .register()

val GJENAPNET_AV_BRUKER_COUNTER: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("gjenapnet_counter")
        .help("Antall sykmeldinger gjenåpnet eller endret av bruker")
        .register()

val SENDT_AV_BRUKER_COUNTER: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("sendt_counter")
        .help("Antall sykmeldinger sendt av bruker")
        .register()

val HTTP_CLIENT_HISTOGRAM: Histogram =
    Histogram.Builder()
        .labelNames("path")
        .name("client_calls_duration_seconds")
        .help("durations for outgoing requests in seconds")
        .register()

val CLIENT_ASSERTION_HISTOGRAM: Histogram =
    Histogram.Builder()
        .name("client_assertions_duration_seconds")
        .help("durations for creating signed client assertions in seconds")
        .register()

val MISSING_DATA_COUNTER: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("missing_data")
        .labelNames("data")
        .help("Manglende data som gjør at bruker får 404")
        .register()
