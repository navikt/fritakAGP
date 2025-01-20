package no.nav.helse.fritakagp.web.auth

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.nav.helse.fritakagp.Env
import no.nav.helse.fritakagp.Issuers
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.security.mock.oauth2.MockOAuth2Server

private val logger = "LocalAuthTokenDispenser".logger()

fun Application.localAuthTokenDispenser(env: Env) {
    logger.info("Starter OAuth2-mock")

    val server = MockOAuth2Server().apply { start(port = 6666) }

    logger.info("Startet OAuth2-mock på ${server.jwksUrl(Issuers.TOKENX)}")
    routing {
        get("/local/token-please") {
            logger.warn("token-please skal kun kalles lokalt!")
            val token = server.issueToken(
                subject = call.request.queryParameters["subject"].toString(),
                issuerId = Issuers.TOKENX,
                audience = env.tokenxAcceptedAudience
            )
            call.respondText(token.serialize(), ContentType.Text.Plain, HttpStatusCode.OK)
        }
    }
}
