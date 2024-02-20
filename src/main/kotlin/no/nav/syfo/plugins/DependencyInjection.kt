package no.nav.syfo.plugins

import io.ktor.server.application.*
import no.nav.syfo.application.database.Database
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.arbeidsgivere.db.ArbeidsforholdDb
import no.nav.syfo.arbeidsgivere.narmesteleder.db.NarmestelederDb
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.db.SykmeldingStatusDb
import no.nav.syfo.sykmeldingstatus.kafka.KafkaFactory.Companion.getSykmeldingStatusKafkaProducer
import no.nav.syfo.utils.Environment
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureModules() {
    install(Koin) {
        slf4jLogger()

        modules(
            environmentModule,
            authModule,
            applicationStateModule,
            databaseModule,
            arbeidsgiverModule,
            sykmeldingModule,
            sykmeldingStatusModule,
        )
    }
}

val environmentModule = module { single { Environment() } }

val applicationStateModule = module { single { ApplicationState() } }

val databaseModule = module {
    single<DatabaseInterface> { Database(get()).initializeDatasource().runFlywayMigrations() }
}

val authModule = module { single { getProductionAuthConfig(get()) } }

val arbeidsgiverModule = module {
    single { NarmestelederDb(get()) }
    single { ArbeidsforholdDb(get()) }
    single { ArbeidsgiverService(get(), get()) }
}

val sykmeldingModule = module {
    single { SykmeldingDb(get()) }
    single { SykmeldingService(get()) }
}

val sykmeldingStatusModule = module {
    single { getSykmeldingStatusKafkaProducer(get()) }
    single { SykmeldingStatusDb(get()) }
    single { SykmeldingStatusService(get(), get(), get()) }
}
