package no.nav.helse.fritakagp.web.auth

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.config.ApplicationConfig
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpStatusCode
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import no.nav.security.mock.oauth2.MockOAuth2Server

import java.net.InetAddress


@KtorExperimentalAPI
fun Application.localCookieDispenser(config: ApplicationConfig) {
    val server = MockOAuth2Server()
    server.start(InetAddress.getLocalHost(), 6666)

    routing {
        get("/local/cookie-please") {
            val cookieName = config.configList("no.nav.security.jwt.issuers")[0].property("cookie_name").getString()
            val issuerName = config.configList("no.nav.security.jwt.issuers")[0].property("issuer_name").getString()
            val audience = config.configList("no.nav.security.jwt.issuers")[0].property("accepted_audience").getString()
            val token = server.issueToken(
                subject = call.request.queryParameters["subject"].toString(),
                issuerId = issuerName,
                audience = audience
            )

            call.response.cookies.append(Cookie(cookieName, token.serialize(), CookieEncoding.RAW, domain = "localhost", path = "/"))

            if (call.request.queryParameters["redirect"] != null) {
                call.respondText("<script>window.location.href='" + call.request.queryParameters["redirect"] + "';</script>", ContentType.Text.Html, HttpStatusCode.OK)
            } else {
                call.respondText("Cookie Set", ContentType.Text.Plain, HttpStatusCode.OK)
            }
        }
    }
}