package no.nav.helse.fritakagp.koin

import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.OppgaveKlient
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.OppgaveKlientImpl
import no.nav.helse.fritakagp.Env
import no.nav.helse.fritakagp.EnvOauth2
import no.nav.helse.fritakagp.auth.AuthClient
import no.nav.helse.fritakagp.auth.IdentityProvider
import no.nav.helse.fritakagp.auth.fetchToken
import no.nav.helse.fritakagp.integration.GrunnbeloepClient
import no.nav.helse.fritakagp.integration.gcp.BucketStorage
import no.nav.helse.fritakagp.integration.gcp.BucketStorageImpl
import no.nav.helse.fritakagp.integration.kafka.BrukernotifikasjonKafkaProducer
import no.nav.helse.fritakagp.integration.kafka.BrukernotifikasjonSender
import no.nav.helse.fritakagp.integration.kafka.brukernotifikasjonKafkaProps
import no.nav.helse.fritakagp.integration.virusscan.ClamavVirusScannerImp
import no.nav.helse.fritakagp.integration.virusscan.VirusScanner
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.altinn.Altinn3OBOClient
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.altinn.CacheConfig
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.pdl.Behandlingsgrunnlag
import no.nav.helsearbeidsgiver.pdl.PdlClient
import org.koin.core.module.Module
import org.koin.dsl.bind
import java.time.Duration
import kotlin.time.toKotlinDuration

fun Module.externalSystemClients(env: Env, envOauth2: EnvOauth2) {
    single {
        val maskinportenAuthClient: AuthClient = get()
        AltinnClient(
            url = env.altinnServiceOwnerUrl,
            serviceCode = env.altinnServiceOwnerServiceId,
            getToken = maskinportenAuthClient.fetchToken(IdentityProvider.MASKINPORTEN, env.altinnScope),
            altinnApiKey = env.altinnServiceOwnerApiKey,
            cacheConfig = CacheConfig(Duration.ofMinutes(60).toKotlinDuration(), 100)
        )
    } bind AltinnClient::class

    single {
        Altinn3OBOClient(
            baseUrl = env.altinnTilgangerBaseUrl,
            serviceCode = env.altinnServiceOwnerServiceId,
            cacheConfig = CacheConfig(Duration.ofMinutes(60).toKotlinDuration(), 100)
        )
    } bind Altinn3OBOClient::class

    single { GrunnbeloepClient(env.grunnbeloepUrl, get()) }

    single {
        val azureAuthClient: AuthClient = get()
        PdlClient(env.pdlUrl, Behandlingsgrunnlag.FRITAKAGP, azureAuthClient.fetchToken(IdentityProvider.AZURE_AD, envOauth2.scopePdl))
    } bind PdlClient::class

    single {
        val azureAuthClient: AuthClient = get()
        AaregClient(env.aaregUrl, azureAuthClient.fetchToken(IdentityProvider.AZURE_AD, envOauth2.scopeAareg))
    } bind AaregClient::class

    single {
        val azureAuthClient: AuthClient = get()
        DokArkivClient(env.dokarkivUrl, 3, azureAuthClient.fetchToken(IdentityProvider.AZURE_AD, envOauth2.scopeDokarkiv))
    }

    single {
        val azureAuthClient: AuthClient = get()
        OppgaveKlientImpl(env.oppgavebehandlingUrl, azureAuthClient.fetchToken(IdentityProvider.AZURE_AD, envOauth2.scopeOppgave), get())
    } bind OppgaveKlient::class

    single {
        val azureAuthClient: AuthClient = get()
        ArbeidsgiverNotifikasjonKlient(env.arbeidsgiverNotifikasjonUrl, azureAuthClient.fetchToken(IdentityProvider.AZURE_AD, envOauth2.scopeArbeidsgivernotifikasjon))
    }

    single {
        ClamavVirusScannerImp(
            get(),
            env.clamAvUrl
        )
    } bind VirusScanner::class

    single {
        BucketStorageImpl(
            env.gcpBucketName,
            env.gcpProjectId
        )
    } bind BucketStorage::class

    single {
        BrukernotifikasjonKafkaProducer(
            brukernotifikasjonKafkaProps(),
            env.kafkaTopicNameBrukernotifikasjon
        )
    } bind BrukernotifikasjonSender::class

    single { AuthClient(tokenEndpoint = env.tokenEndpoint, tokenExchangeEndpoint = env.tokenExchangeEndpoint, tokenIntrospectionEndpoint = env.tokenIntrospectionEndpoint) }
}
