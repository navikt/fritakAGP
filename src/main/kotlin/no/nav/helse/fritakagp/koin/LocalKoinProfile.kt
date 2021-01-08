package no.nav.helse.fritakagp.koin

import com.zaxxer.hikari.HikariDataSource
import io.ktor.config.*
import io.ktor.util.*
import no.nav.helse.arbeidsgiver.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.helse.arbeidsgiver.bakgrunnsjobb.BakgrunnsjobbService
import no.nav.helse.arbeidsgiver.bakgrunnsjobb.PostgresBakgrunnsjobbRepository
import no.nav.helse.fritakagp.db.*
import no.nav.helse.fritakagp.processing.gravid.GravidSoeknadPDFGenerator
import no.nav.helse.fritakagp.processing.gravid.SoeknadGravidProcessor
import no.nav.helse.fritakagp.processing.kvittering.DummyKvitteringSender
import no.nav.helse.fritakagp.processing.kvittering.KvitteringProcessor
import no.nav.helse.fritakagp.processing.kvittering.KvitteringSender
import org.koin.dsl.bind
import org.koin.dsl.module
import javax.sql.DataSource


@KtorExperimentalAPI
fun localDevConfig(config: ApplicationConfig) = module {

    this.mockExternalDependecies()

    single { HikariDataSource(createHikariConfig(config.getjdbcUrlFromProperties(), config.getString("database.username"), config.getString("database.password"))) } bind DataSource::class
    single { PostgresGravidSoeknadRepository(get(), get()) } bind GravidSoeknadRepository::class
    single { PostgresKroniskSoeknadRepository(get(), get()) } bind KroniskSoeknadRepository::class

    single { PostgresBakgrunnsjobbRepository(get()) } bind BakgrunnsjobbRepository::class
    single { BakgrunnsjobbService(get()) }

    single { SoeknadGravidProcessor(get(), get(), get(), get(), GravidSoeknadPDFGenerator(), get(), get())}

    single { DummyKvitteringSender() } bind KvitteringSender::class
    single { KvitteringProcessor(get(), get(), get())}
}
