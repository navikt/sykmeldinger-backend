package no.nav.syfo.metrics

import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

class HttpRequestMonitorInterceptorSpek : Spek({

    describe("Test av at bytting av UUID i path fungerer som forventet") {
        it("UUID byttes ut") {
            val uuid = UUID.randomUUID().toString()
            val pathMedUuid = "/api/v1/sykmeldinger/$uuid/bekreft"

            REGEX.replace(pathMedUuid, ":id") shouldBeEqualTo "/api/v1/sykmeldinger/:id/bekreft"
        }

        it("String som ikke er UUID byttes ikke ut") {
            val pathUtenUuid = "/api/v1/sykmeldinger/123-testparam/bekreft"

            REGEX.replace(pathUtenUuid, ":id") shouldBeEqualTo pathUtenUuid
        }
    }
})
