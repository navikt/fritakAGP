package no.nav.helse.fritakagp.web.api

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.runBlocking
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbService
import no.nav.helse.fritakagp.GravidKravMetrics
import no.nav.helse.fritakagp.GravidSoeknadMetrics
import no.nav.helse.fritakagp.auth.AuthClient
import no.nav.helse.fritakagp.db.GravidKravRepository
import no.nav.helse.fritakagp.db.GravidSoeknadRepository
import no.nav.helse.fritakagp.domain.BeloepBeregning
import no.nav.helse.fritakagp.domain.KravStatus
import no.nav.helse.fritakagp.domain.decodeBase64File
import no.nav.helse.fritakagp.integration.brreg.BrregClient
import no.nav.helse.fritakagp.integration.gcp.BucketStorage
import no.nav.helse.fritakagp.integration.virusscan.VirusScanner
import no.nav.helse.fritakagp.processing.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonProcessor
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravEndreProcessor
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravKvitteringProcessor
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravProcessor
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravSlettProcessor
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadKvitteringProcessor
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadProcessor
import no.nav.helse.fritakagp.service.PdlService
import no.nav.helse.fritakagp.web.api.resreq.GravidKravRequest
import no.nav.helse.fritakagp.web.api.resreq.GravidSoknadRequest
import no.nav.helse.fritakagp.web.api.resreq.validation.VirusCheckConstraint
import no.nav.helse.fritakagp.web.api.resreq.validation.extractBase64Del
import no.nav.helse.fritakagp.web.api.resreq.validation.extractFilExtDel
import no.nav.helse.fritakagp.web.auth.authorize
import no.nav.helse.fritakagp.web.auth.hentIdentitetsnummerFraLoginToken
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.altinn.Altinn3OBOClient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import org.valiktor.ConstraintViolationException
import org.valiktor.DefaultConstraintViolation
import java.time.LocalDateTime
import java.util.UUID

fun Route.gravidRoutes(
    breegClient: BrregClient,
    gravidSoeknadRepo: GravidSoeknadRepository,
    gravidKravRepo: GravidKravRepository,
    bakgunnsjobbService: BakgrunnsjobbService,
    om: ObjectMapper,
    virusScanner: VirusScanner,
    bucket: BucketStorage,
    authorizer: Altinn3OBOClient,
    belopBeregning: BeloepBeregning,
    aaregClient: AaregClient,
    pdlService: PdlService,
    arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    authClient: AuthClient,
    fagerScope: String
) {
    route("/gravid") {
        route("/soeknad") {
            get("/{id}") {
                val innloggetFnr = hentIdentitetsnummerFraLoginToken(call.request)
                val form = gravidSoeknadRepo.getById(UUID.fromString(call.parameters["id"]))
                if (form == null || form.identitetsnummer != innloggetFnr) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    form.sendtAvNavn = form.sendtAvNavn ?: pdlService.hentNavn(innloggetFnr)
                    form.navn = form.navn ?: pdlService.hentNavn(form.identitetsnummer)

                    call.respond(HttpStatusCode.OK, form)
                }
            }

            post {
                val innloggetFnr = hentIdentitetsnummerFraLoginToken(call.request)
                val request = call.receive<GravidSoknadRequest>()

                val isVirksomhet = if (application.environment.config.property("koin.profile").getString() == "PREPROD") true else breegClient.erVirksomhet(request.virksomhetsnummer)
                request.validate(isVirksomhet)

                val sendtAvNavn = pdlService.hentNavn(innloggetFnr)
                val navn = pdlService.hentNavn(request.identitetsnummer)

                val soeknad = request.toDomain(innloggetFnr, sendtAvNavn, navn)

                processDocumentForGCPStorage(request.dokumentasjon, virusScanner, bucket, soeknad.id)

                gravidSoeknadRepo.insert(soeknad)
                bakgunnsjobbService.opprettJobb<GravidSoeknadProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(GravidSoeknadProcessor.JobbData(soeknad.id))
                )
                bakgunnsjobbService.opprettJobb<GravidSoeknadKvitteringProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(GravidSoeknadKvitteringProcessor.Jobbdata(soeknad.id))
                )

                call.respond(HttpStatusCode.Created, soeknad)
                GravidSoeknadMetrics.tellMottatt()
            }
        }

        route("/krav") {
            get("/{id}") {
                val innloggetFnr = hentIdentitetsnummerFraLoginToken(call.request)
                val form = gravidKravRepo.getById(UUID.fromString(call.parameters["id"]))
                val slettet = call.request.queryParameters.contains("slettet")
                if (form == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else if (!slettet && form.status == KravStatus.SLETTET) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    if (form.identitetsnummer != innloggetFnr) {
                        authorize(authorizer, authClient, fagerScope, form.virksomhetsnummer)
                    }
                    form.sendtAvNavn = form.sendtAvNavn ?: pdlService.hentNavn(innloggetFnr)
                    form.navn = form.navn ?: pdlService.hentNavn(form.identitetsnummer)

                    call.respond(HttpStatusCode.OK, form)
                }
            }

            post {
                val request = call.receive<GravidKravRequest>()
                authorize(authorizer, authClient, fagerScope, request.virksomhetsnummer)
                val arbeidsforhold = aaregClient
                    .hentArbeidsforhold(request.identitetsnummer, UUID.randomUUID().toString())
                    .filter { it.arbeidsgiver.organisasjonsnummer == request.virksomhetsnummer }

                request.validate(arbeidsforhold)

                val innloggetFnr = hentIdentitetsnummerFraLoginToken(call.request)
                val sendtAvNavn = pdlService.hentNavn(innloggetFnr)
                val navn = pdlService.hentNavn(request.identitetsnummer)

                val krav = request.toDomain(innloggetFnr, sendtAvNavn, navn)
                belopBeregning.beregnBeløpGravid(krav)

                gravidKravRepo.insert(krav)
                bakgunnsjobbService.opprettJobb<GravidKravProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(GravidKravProcessor.JobbData(krav.id))
                )
                bakgunnsjobbService.opprettJobb<GravidKravKvitteringProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(GravidKravKvitteringProcessor.Jobbdata(krav.id))
                )
                bakgunnsjobbService.opprettJobb<ArbeidsgiverNotifikasjonProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(ArbeidsgiverNotifikasjonProcessor.JobbData(krav.id, ArbeidsgiverNotifikasjonProcessor.JobbData.SkjemaType.GravidKrav))
                )

                call.respond(HttpStatusCode.Created, krav)
                GravidKravMetrics.tellMottatt()
            }

            patch("/{id}") {
                val request = call.receive<GravidKravRequest>()

                authorize(authorizer, authClient, fagerScope, request.virksomhetsnummer)

                val innloggetFnr = hentIdentitetsnummerFraLoginToken(call.request)
                val sendtAvNavn = pdlService.hentNavn(innloggetFnr)
                val navn = pdlService.hentNavn(request.identitetsnummer)

                val arbeidsforhold = aaregClient
                    .hentArbeidsforhold(request.identitetsnummer, UUID.randomUUID().toString())
                    .filter { it.arbeidsgiver.organisasjonsnummer == request.virksomhetsnummer }

                request.validate(arbeidsforhold)

                val kravId = UUID.fromString(call.parameters["id"])
                val forrigeKrav = gravidKravRepo.getById(kravId)
                    ?: return@patch call.respond(HttpStatusCode.NotFound)

                if (forrigeKrav.virksomhetsnummer != request.virksomhetsnummer) {
                    return@patch call.respond(HttpStatusCode.Forbidden)
                }

                val kravTilOppdatering = request.toDomain(innloggetFnr, sendtAvNavn, navn)
                belopBeregning.beregnBeløpGravid(kravTilOppdatering)

                if (forrigeKrav.isDuplicate(kravTilOppdatering)) {
                    return@patch call.respond(HttpStatusCode.Conflict)
                }

                forrigeKrav.status = KravStatus.ENDRET
                forrigeKrav.slettetAv = innloggetFnr
                forrigeKrav.slettetAvNavn = sendtAvNavn
                forrigeKrav.endretDato = LocalDateTime.now()
                forrigeKrav.endretTilId = kravTilOppdatering.id

                // Sletter gammelt krav
                forrigeKrav.arbeidsgiverSakId?.let {
                    runBlocking { arbeidsgiverNotifikasjonKlient.hardDeleteSak(it) }
                }

                gravidKravRepo.update(forrigeKrav)
                // Oppretter nytt krav
                gravidKravRepo.insert(kravTilOppdatering)
                bakgunnsjobbService.opprettJobb<GravidKravEndreProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(GravidKravProcessor.JobbData(forrigeKrav.id))
                )
                bakgunnsjobbService.opprettJobb<GravidKravKvitteringProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(GravidKravKvitteringProcessor.Jobbdata(kravTilOppdatering.id))
                )
                bakgunnsjobbService.opprettJobb<ArbeidsgiverNotifikasjonProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(ArbeidsgiverNotifikasjonProcessor.JobbData(kravTilOppdatering.id, ArbeidsgiverNotifikasjonProcessor.JobbData.SkjemaType.GravidKrav))
                )

                call.respond(HttpStatusCode.OK, kravTilOppdatering)
            }

            delete("/{id}") {
                val innloggetFnr = hentIdentitetsnummerFraLoginToken(call.request)
                val slettetAv = pdlService.hentNavn(innloggetFnr)
                val kravId = UUID.fromString(call.parameters["id"])
                val form = gravidKravRepo.getById(kravId)
                    ?: return@delete call.respond(HttpStatusCode.NotFound)

                authorize(authorizer, authClient, fagerScope, form.virksomhetsnummer)

                form.arbeidsgiverSakId?.let {
                    runBlocking { arbeidsgiverNotifikasjonKlient.hardDeleteSak(it) }
                }
                form.status = KravStatus.SLETTET
                form.slettetAv = innloggetFnr
                form.slettetAvNavn = slettetAv
                form.endretDato = LocalDateTime.now()
                gravidKravRepo.update(form)
                bakgunnsjobbService.opprettJobb<GravidKravSlettProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(GravidKravProcessor.JobbData(form.id))
                )
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}

suspend fun processDocumentForGCPStorage(doc: String?, virusScanner: VirusScanner, bucket: BucketStorage, id: UUID) {
    if (!doc.isNullOrEmpty()) {
        val fileContent = extractBase64Del(doc)
        val fileExt = extractFilExtDel(doc)
        if (!virusScanner.scanDoc(decodeBase64File(fileContent))) {
            throw ConstraintViolationException(
                setOf(
                    DefaultConstraintViolation(
                        "dokumentasjon",
                        constraint = VirusCheckConstraint()
                    )
                )
            )
        }
        bucket.uploadDoc(id, fileContent, fileExt)
    }
}
