package no.nav.helse.fritakagp.web.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.ApplicationRequest
import io.ktor.util.pipeline.PipelineContext
import no.nav.helse.fritakagp.auth.AuthClient
import no.nav.helse.fritakagp.auth.fetchOboToken
import no.nav.helse.fritakagp.integration.altinn.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.altinn.Altinn3OBOClient
import no.nav.security.token.support.core.jwt.JwtToken
import java.time.Instant
import java.util.Date

suspend fun PipelineContext<Unit, ApplicationCall>.authorize(authorizer: Altinn3OBOClient, authClient: AuthClient, scope: String, orgnr: String) {
    val fnr = hentIdentitetsnummerFraLoginToken(call.request)
    val userTokenString = getTokenString(call.request)
    val getToken = authClient.fetchOboToken(scope, userTokenString)
    if (!authorizer.harTilgangTilOrganisasjon(fnr, orgnr, getToken)) {
        throw ManglerAltinnRettigheterException()
    }
}

fun hentIdentitetsnummerFraLoginToken(request: ApplicationRequest): String {
    val tokenString = getTokenString(request)
    val pid = JwtToken(tokenString).jwtTokenClaims.get("pid")
    return pid?.toString() ?: JwtToken(tokenString).subject
}

fun hentUtløpsdatoFraLoginToken(request: ApplicationRequest): Date {
    val tokenString = getTokenString(request)
    return JwtToken(tokenString).jwtTokenClaims.expirationTime ?: Date.from(Instant.MIN)
}

fun getTokenString(request: ApplicationRequest): String {
    return request.headers["Authorization"]?.replaceFirst("Bearer ", "")
        ?: throw IllegalAccessException("Du må angi et identitetstoken i Authorization-headeren")
}
