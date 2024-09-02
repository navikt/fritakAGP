package no.nav.helse.fritakagp.koin

import com.zaxxer.hikari.HikariDataSource
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbService
import no.nav.hag.utils.bakgrunnsjobb.PostgresBakgrunnsjobbRepository
import no.nav.helse.fritakagp.Env
import no.nav.helse.fritakagp.MetrikkVarsler
import no.nav.helse.fritakagp.db.GravidKravRepository
import no.nav.helse.fritakagp.db.GravidSoeknadRepository
import no.nav.helse.fritakagp.db.IStatsRepo
import no.nav.helse.fritakagp.db.KroniskKravRepository
import no.nav.helse.fritakagp.db.KroniskSoeknadRepository
import no.nav.helse.fritakagp.db.PostgresGravidKravRepository
import no.nav.helse.fritakagp.db.PostgresGravidSoeknadRepository
import no.nav.helse.fritakagp.db.PostgresKroniskKravRepository
import no.nav.helse.fritakagp.db.PostgresKroniskSoeknadRepository
import no.nav.helse.fritakagp.db.StatsRepoImpl
import no.nav.helse.fritakagp.db.createHikariConfig
import no.nav.helse.fritakagp.domain.BeloepBeregning
import no.nav.helse.fritakagp.integration.altinn.message.Clients
import no.nav.helse.fritakagp.integration.brreg.BrregClient
import no.nav.helse.fritakagp.integration.brreg.MockBrregClient
import no.nav.helse.fritakagp.processing.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonProcessor
import no.nav.helse.fritakagp.processing.arbeidsgivernotifikasjon.ArbeidsgiverOppdaterNotifikasjonProcessor
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonProcessor
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonService
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravAltinnKvitteringSender
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravEndreProcessor
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravKvitteringProcessor
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravKvitteringSender
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravPDFGenerator
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravProcessor
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravSlettProcessor
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadAltinnKvitteringSender
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadKvitteringProcessor
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadKvitteringSender
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadPDFGenerator
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravAltinnKvitteringSender
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravEndreProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravKvitteringProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravKvitteringSender
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravPDFGenerator
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravSlettProcessor
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadAltinnKvitteringSender
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadKvitteringProcessor
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadKvitteringSender
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadPDFGenerator
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadProcessor
import no.nav.helse.fritakagp.service.PdlService
import no.nav.tms.varsel.action.Sensitivitet
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import javax.sql.DataSource

fun preprodConfig(env: Env.Preprod): Module = module {
    externalSystemClients(env, env.oauth2)

    single {
        HikariDataSource(
            createHikariConfig(
                env.databaseUrl,
                env.databaseUsername,
                env.databasePassword
            )
        )
    } bind DataSource::class

    single { PostgresGravidSoeknadRepository(get(), get()) } bind GravidSoeknadRepository::class
    single { PostgresKroniskSoeknadRepository(get(), get()) } bind KroniskSoeknadRepository::class
    single { PostgresGravidKravRepository(get(), get()) } bind GravidKravRepository::class
    single { PostgresKroniskKravRepository(get(), get()) } bind KroniskKravRepository::class

    single { PostgresBakgrunnsjobbRepository(get()) } bind BakgrunnsjobbRepository::class
    single { BakgrunnsjobbService(get(), bakgrunnsvarsler = MetrikkVarsler()) }

    single { GravidSoeknadProcessor(gravidSoeknadRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), pdlService = get(), bakgrunnsjobbRepo = get(), pdfGenerator = GravidSoeknadPDFGenerator(), om = get(), bucketStorage = get(), brregClient = get()) }
    single { GravidKravProcessor(gravidKravRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), pdlService = get(), bakgrunnsjobbRepo = get(), pdfGenerator = GravidKravPDFGenerator(), om = get(), bucketStorage = get(), brregClient = get()) }
    single { GravidKravSlettProcessor(gravidKravRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), pdlService = get(), pdfGenerator = GravidKravPDFGenerator(), om = get(), bucketStorage = get(), bakgrunnsjobbRepo = get()) }
    single { GravidKravEndreProcessor(gravidKravRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), pdlService = get(), pdfGenerator = GravidKravPDFGenerator(), om = get(), bucketStorage = get(), bakgrunnsjobbRepo = get()) }

    single { KroniskSoeknadProcessor(kroniskSoeknadRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), bakgrunnsjobbRepo = get(), pdlService = get(), pdfGenerator = KroniskSoeknadPDFGenerator(), om = get(), bucketStorage = get(), brregClient = get()) }
    single { KroniskKravProcessor(kroniskKravRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), pdlService = get(), bakgrunnsjobbRepo = get(), pdfGenerator = KroniskKravPDFGenerator(), om = get(), bucketStorage = get(), brregClient = get()) }
    single { KroniskKravSlettProcessor(kroniskKravRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), pdlService = get(), pdfGenerator = KroniskKravPDFGenerator(), om = get(), bucketStorage = get(), bakgrunnsjobbRepo = get()) }
    single { KroniskKravEndreProcessor(kroniskKravRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), pdlService = get(), pdfGenerator = KroniskKravPDFGenerator(), om = get(), bucketStorage = get(), bakgrunnsjobbRepo = get()) }

    single { Clients.iCorrespondenceExternalBasic(env.altinnMeldingUrl) }

    single {
        GravidSoeknadAltinnKvitteringSender(
            env.altinnMeldingServiceId,
            get(),
            env.altinnMeldingUsername,
            env.altinnMeldingPassword
        )
    } bind GravidSoeknadKvitteringSender::class

    single { GravidSoeknadKvitteringProcessor(get(), get(), get()) }

    single {
        GravidKravAltinnKvitteringSender(
            env.altinnMeldingServiceId,
            get(),
            env.altinnMeldingUsername,
            env.altinnMeldingPassword
        )
    } bind GravidKravKvitteringSender::class

    single { GravidKravKvitteringProcessor(get(), get(), get()) }

    single {
        KroniskSoeknadAltinnKvitteringSender(
            env.altinnMeldingServiceId,
            get(),
            env.altinnMeldingUsername,
            env.altinnMeldingPassword
        )
    } bind KroniskSoeknadKvitteringSender::class
    single { KroniskSoeknadKvitteringProcessor(get(), get(), get()) }

    single {
        KroniskKravAltinnKvitteringSender(
            env.altinnMeldingServiceId,
            get(),
            env.altinnMeldingUsername,
            env.altinnMeldingPassword
        )
    } bind KroniskKravKvitteringSender::class
    single { KroniskKravKvitteringProcessor(get(), get(), get()) }

    single { BrukernotifikasjonProcessor(get(), get()) }
    single { BrukernotifikasjonService(get(), get(), get(), get(), get(), Sensitivitet.Substantial, env.frontendUrl) }

    single { ArbeidsgiverNotifikasjonProcessor(get(), get(), get(), env.frontendUrl, get()) }
    single { ArbeidsgiverOppdaterNotifikasjonProcessor(get(), get(), get(), get()) }
    single { PdlService(get()) }

    single { MockBrregClient() } bind BrregClient::class

    single { BeloepBeregning(get()) }

    single { StatsRepoImpl(get()) } bind IStatsRepo::class
}
