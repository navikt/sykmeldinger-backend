package no.nav.syfo.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Histogram

const val METRICS_NS = "sykmeldingerbackend"

val HTTP_HISTOGRAM: Histogram = Histogram.Builder()
    .labelNames("path")
    .name("requests_duration_seconds")
    .help("http requests durations for incoming requests in seconds")
    .register()

val BEKREFTET_AV_BRUKER_COUNTER: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("bekreftet_counter")
    .help("Antall sykmeldinger bekreftet av bruker")
    .register()

val AVBRUTT_AV_BRUKER_COUNTER: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("avbrutt_counter")
    .help("Antall sykmeldinger avbrutt av bruker")
    .register()

val GJENAPNET_AV_BRUKER_COUNTER: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("gjenapnet_counter")
    .help("Antall sykmeldinger gjenåpnet eller endret av bruker")
    .register()
