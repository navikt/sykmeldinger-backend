package no.nav.syfo.sykmeldingstatus.soknadstatus

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.sykmeldingstatus.soknadstatus.client.RSSoknadstatus
import no.nav.syfo.sykmeldingstatus.soknadstatus.client.RSSykepengesoknad
import no.nav.syfo.sykmeldingstatus.soknadstatus.client.SyfosoknadClient
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SoknadstatusServiceTest : Spek({
    val token = "token"
    val sykmeldingId = "sykmeldingId"
    val syfosoknadClient = mockk<SyfosoknadClient>()
    val soknadstatusService = SoknadstatusService(syfosoknadClient)

    beforeEachTest {
        clearAllMocks()
    }

    describe("SoknadstatusService") {
        it("Returnerer false hvis bruker ikke har søknader") {
            coEvery { syfosoknadClient.getSoknader(any(), any()) } returns emptyList()

            runBlocking {
                soknadstatusService.finnesSendtSoknadForSykmelding(token = token, sykmeldingId = sykmeldingId) shouldEqual false
            }
        }
        it("Returnerer false hvis bruker har sendt søknad knyttet til annen sykmelding") {
            coEvery { syfosoknadClient.getSoknader(any(), any()) } returns listOf(RSSykepengesoknad("id", "sykmeldingId2", RSSoknadstatus.SENDT))

            runBlocking {
                soknadstatusService.finnesSendtSoknadForSykmelding(token = token, sykmeldingId = sykmeldingId) shouldEqual false
            }
        }
        it("Returnerer false hvis bruker har åpen søknad knyttet til denne sykmeldingen") {
            coEvery { syfosoknadClient.getSoknader(any(), any()) } returns listOf(RSSykepengesoknad("id", sykmeldingId, RSSoknadstatus.NY))

            runBlocking {
                soknadstatusService.finnesSendtSoknadForSykmelding(token = token, sykmeldingId = sykmeldingId) shouldEqual false
            }
        }
        it("Returnerer true hvis bruker har sendt søknad knyttet til denne sykmeldingen") {
            coEvery { syfosoknadClient.getSoknader(any(), any()) } returns listOf(RSSykepengesoknad("id", sykmeldingId, RSSoknadstatus.SENDT))

            runBlocking {
                soknadstatusService.finnesSendtSoknadForSykmelding(token = token, sykmeldingId = sykmeldingId) shouldEqual true
            }
        }
    }
})
