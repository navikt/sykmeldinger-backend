package no.nav.syfo.metrics

import io.kotest.core.spec.style.FunSpec
import java.util.UUID
import org.amshove.kluent.shouldBeEqualTo

class HttpRequestMonitorInterceptorSpec :
    FunSpec({
        context("Test av at bytting av UUID i path fungerer som forventet") {
            test("UUID byttes ut") {
                val uuid = UUID.randomUUID().toString()
                val pathMedUuid = "/api/v1/sykmeldinger/$uuid/bekreft"

                REGEX.replace(pathMedUuid, ":id") shouldBeEqualTo "/api/v1/sykmeldinger/:id/bekreft"
            }

            test("String som ikke er UUID byttes ikke ut") {
                val pathUtenUuid = "/api/v1/sykmeldinger/123-testparam/bekreft"

                REGEX.replace(pathUtenUuid, ":id") shouldBeEqualTo pathUtenUuid
            }
        }
    })
