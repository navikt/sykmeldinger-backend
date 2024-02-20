package no.nav.syfo.metrics

import java.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class HttpRequestMonitorInterceptorSpec {

    @Test
    fun `UUID byttes ut`() {
        val uuid = UUID.randomUUID().toString()
        val pathMedUuid = "/api/v1/sykmeldinger/$uuid/bekreft"

        REGEX.replace(pathMedUuid, ":id") shouldBeEqualTo "/api/v1/sykmeldinger/:id/bekreft"
    }

    @Test
    fun `String som ikke er UUID byttes ikke ut`() {
        val pathUtenUuid = "/api/v1/sykmeldinger/123-testparam/bekreft"

        REGEX.replace(pathUtenUuid, ":id") shouldBeEqualTo pathUtenUuid
    }
}
