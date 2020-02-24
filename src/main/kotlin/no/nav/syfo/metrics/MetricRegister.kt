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
    .name("bereftet_counter")
    .help("Antall sykmeldinger bekreftet av bruker")
    .register()
