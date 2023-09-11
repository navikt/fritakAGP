package no.nav.helse.fritakagp.koin


import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.jackson.JacksonSerializer
import io.ktor.http.ContentType
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import no.nav.helse.arbeidsgiver.kubernetes.KubernetesProbeManager
import no.nav.helse.fritakagp.Env
import no.nav.helse.fritakagp.customObjectMapper
import org.koin.core.module.Module
import org.koin.dsl.module

fun profileModules(env: Env): List<Module> {
    val envModule = when (env) {
        is Env.Prod -> prodConfig(env)
        is Env.Preprod -> preprodConfig(env)
        is Env.Local -> localConfig(env)
    }

    return listOf(common, envModule)
}

private val common = module {
    single { customObjectMapper() }

    single {
        HttpClient(Apache) {
        }
    }

    single { KubernetesProbeManager() }
}
