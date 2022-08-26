package no.nav.helse.fritakagp.web.api

import io.ktor.application.application
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import no.nav.helse.arbeidsgiver.integrasjoner.altinn.AltinnBrukteForLangTidException
import no.nav.helse.arbeidsgiver.web.auth.AltinnOrganisationsRepository
import no.nav.helse.fritakagp.web.auth.hentIdentitetsnummerFraLoginToken

fun Route.altinnRoutes(authRepo: AltinnOrganisationsRepository) {
    route("/arbeidsgivere") {
        get("/") {
            val id = hentIdentitetsnummerFraLoginToken(application.environment.config, call.request)
            try {
                val rettigheter = authRepo.hentOrgMedRettigheterForPerson(id)
                call.respond(rettigheter)
            } catch (ae: AltinnBrukteForLangTidException) {
                call.respond(HttpStatusCode.ExpectationFailed, "Altinn brukte for lang tid på å svare, prøv igjen om litt")
            }
        }
    }
}
