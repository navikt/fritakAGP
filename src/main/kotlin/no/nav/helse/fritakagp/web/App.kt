package no.nav.helse.fritakagp.web

import com.typesafe.config.ConfigFactory
import io.ktor.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.arbeidsgiver.kubernetes.KubernetesProbeManager
import no.nav.helse.arbeidsgiver.kubernetes.LivenessComponent
import no.nav.helse.arbeidsgiver.kubernetes.ReadynessComponent
import no.nav.helse.arbeidsgiver.system.AppEnv
import no.nav.helse.arbeidsgiver.system.getEnvironment
import no.nav.helse.fritakagp.koin.getAllOfType
import no.nav.helse.fritakagp.web.auth.localCookieDispenser
import org.koin.ktor.ext.getKoin
import org.slf4j.LoggerFactory


val mainLogger = LoggerFactory.getLogger("main")

@KtorExperimentalAPI
fun main() {

    Thread.currentThread().setUncaughtExceptionHandler { thread, err ->
        mainLogger.error("uncaught exception in thread ${thread.name}: ${err.message}", err)
    }

    embeddedServer(Netty, createApplicationEnvironment()).let { app ->

        val environment = app.environment.config.getEnvironment()
        if(environment == AppEnv.PREPROD || environment == AppEnv.PROD) {
            mainLogger.info("Sover i 30s i påvente av SQL proxy sidecar")
            Thread.sleep(30000)
        }

        app.start(wait = false)
        val koin = app.application.getKoin()
        runBlocking { autoDetectProbeableComponents(koin) }
        mainLogger.info("La til probeable komponenter")


        Runtime.getRuntime().addShutdownHook(Thread {
            app.stop(1000, 1000)
        })


    }
}

private suspend fun autoDetectProbeableComponents(koin: org.koin.core.Koin) {
    val kubernetesProbeManager = koin.get<KubernetesProbeManager>()

    koin.getAllOfType<LivenessComponent>()
            .forEach { kubernetesProbeManager.registerLivenessComponent(it) }

    koin.getAllOfType<ReadynessComponent>()
            .forEach { kubernetesProbeManager.registerReadynessComponent(it) }
}


@KtorExperimentalAPI
fun createApplicationEnvironment() = applicationEngineEnvironment {
    config = HoconApplicationConfig(ConfigFactory.load())

    connector {
        port = 8080
    }

    module {

        if (config.getEnvironment() != AppEnv.PROD) {
            localCookieDispenser(config)
        }

        fritakModule(config)
    }
}

