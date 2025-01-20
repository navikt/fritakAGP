package no.nav.helse.fritakagp.web

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.CORS
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.helse.fritakagp.Env
import no.nav.helse.fritakagp.Issuers
import no.nav.helse.fritakagp.customObjectMapper
import no.nav.helse.fritakagp.web.api.altinnRoutes
import no.nav.helse.fritakagp.web.api.configureExceptionHandling
import no.nav.helse.fritakagp.web.api.gravidRoutes
import no.nav.helse.fritakagp.web.api.kroniskRoutes
import no.nav.helse.fritakagp.web.api.swaggerRoutes
import no.nav.helse.fritakagp.web.api.systemRoutes
import no.nav.security.token.support.v2.tokenValidationSupport
import org.koin.ktor.ext.get

fun Application.fritakModule(env: Env) {
    install(IgnoreTrailingSlash)
    install(Authentication) {
        tokenValidationSupport(name = Issuers.IDPORTEN, config = env.idportenConfig)
        tokenValidationSupport(name = Issuers.TOKENX, config = env.tokenxConfig)
    }

    configureCORSAccess(env)
    configureExceptionHandling()

    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(customObjectMapper()))
        jackson {
            registerModule(JavaTimeModule())
        }
    }

    routing {
        route("${env.ktorBasepath}/api/v1") {
            authenticate("idporten-issuer", "tokenx-issuer") {
                systemRoutes()
                kroniskRoutes(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), env.altinnTilgangerScope)
                gravidRoutes(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), env.altinnTilgangerScope)
                altinnRoutes(get(), get(), get(), env.altinnTilgangerScope)
            }
        }
        swaggerRoutes(env.ktorBasepath)
    }
}

private fun Application.configureCORSAccess(env: Env) {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        when (env) {
            is Env.Prod -> allowHost("arbeidsgiver.nav.no", schemes = listOf("https"))
            is Env.Preprod -> allowHost("arbeidsgiver.intern.dev.nav.no", schemes = listOf("https"))
            is Env.Local -> anyHost()
        }
        allowCredentials = true
        allowNonSimpleContentTypes = true
    }
}
