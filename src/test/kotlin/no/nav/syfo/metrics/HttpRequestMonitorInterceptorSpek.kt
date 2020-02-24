package no.nav.syfo.metrics

import java.util.UUID
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class HttpRequestMonitorInterceptorSpek : Spek({

    describe("Test av at bytting av UUID i path fungerer som forventet") {
        it("UUID byttes ut") {
            val uuid = UUID.randomUUID().toString()
            val pathMedUuid = "/api/v1/sykmeldinger/$uuid/bekreft"

            REGEX.replace(pathMedUuid, ":id") shouldEqual "/api/v1/sykmeldinger/:id/bekreft"
        }

        it("String som ikke er UUID byttes ikke ut") {
            val pathUtenUuid = "/api/v1/sykmeldinger/123-testparam/bekreft"

            REGEX.replace(pathUtenUuid, ":id") shouldEqual pathUtenUuid
        }
    }
})
