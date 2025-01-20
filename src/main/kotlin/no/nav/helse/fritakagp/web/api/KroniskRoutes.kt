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
import no.nav.helse.fritakagp.KroniskKravMetrics
import no.nav.helse.fritakagp.KroniskSoeknadMetrics
import no.nav.helse.fritakagp.auth.AuthClient
import no.nav.helse.fritakagp.db.KroniskKravRepository
import no.nav.helse.fritakagp.db.KroniskSoeknadRepository
import no.nav.helse.fritakagp.domain.BeloepBeregning
import no.nav.helse.fritakagp.domain.KravStatus
import no.nav.helse.fritakagp.integration.brreg.BrregClient
import no.nav.helse.fritakagp.integration.gcp.BucketStorage
import no.nav.helse.fritakagp.integration.virusscan.VirusScanner
import no.nav.helse.fritakagp.processing.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravEndreProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravKvitteringProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravSlettProcessor
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadKvitteringProcessor
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadProcessor
import no.nav.helse.fritakagp.service.PdlService
import no.nav.helse.fritakagp.web.api.resreq.KroniskKravRequest
import no.nav.helse.fritakagp.web.api.resreq.KroniskSoknadRequest
import no.nav.helse.fritakagp.web.auth.authorize
import no.nav.helse.fritakagp.web.auth.hentIdentitetsnummerFraLoginToken
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.altinn.Altinn3OBOClient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.utils.log.logger
import java.time.LocalDateTime
import java.util.UUID

fun Route.kroniskRoutes(
    breegClient: BrregClient,
    kroniskSoeknadRepo: KroniskSoeknadRepository,
    kroniskKravRepo: KroniskKravRepository,
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
    val logger = "kroniskRoutes".logger()

    route("/kronisk") {
        route("/soeknad") {
            get("/{id}") {
                logger.info("KSG: Hent kronisk søknad.")
                val innloggetFnr = hentIdentitetsnummerFraLoginToken(call.request)

                logger.info("KSG: Hent søknad fra db.")
                val form = kroniskSoeknadRepo.getById(UUID.fromString(call.parameters["id"]))

                if (form == null || form.identitetsnummer != innloggetFnr) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    logger.info("KSG: Hent personinfo fra pdl.")
                    form.sendtAvNavn = form.sendtAvNavn ?: pdlService.hentNavn(innloggetFnr)
                    form.navn = form.navn ?: pdlService.hentNavn(form.identitetsnummer)

                    call.respond(HttpStatusCode.OK, form)
                }
            }

            post {
                logger.info("KSP: Send inn kronisk søknad.")
                val request = call.receive<KroniskSoknadRequest>()

                val isVirksomhet = if (application.environment.config.property("koin.profile").getString() == "PREPROD") {
                    true
                } else {
                    logger.info("KSP: Hent virksomhet fra brreg.")
                    breegClient.erVirksomhet(request.virksomhetsnummer)
                }
                request.validate(isVirksomhet)

                val innloggetFnr = hentIdentitetsnummerFraLoginToken(call.request)

                logger.info("KSP: Hent personinfo fra pdl.")
                val sendtAvNavn = pdlService.hentNavn(innloggetFnr)
                val navn = pdlService.hentNavn(request.identitetsnummer)

                val soeknad = request.toDomain(innloggetFnr, sendtAvNavn, navn)

                logger.info("KSP: Prosesser dokument for GCP-lagring.")
                processDocumentForGCPStorage(request.dokumentasjon, virusScanner, bucket, soeknad.id)

                logger.info("KSP: Lagre søknad i db.")
                kroniskSoeknadRepo.insert(soeknad)
                bakgunnsjobbService.opprettJobb<KroniskSoeknadProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(KroniskSoeknadProcessor.JobbData(soeknad.id))
                )
                bakgunnsjobbService.opprettJobb<KroniskSoeknadKvitteringProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(KroniskSoeknadKvitteringProcessor.Jobbdata(soeknad.id))
                )

                call.respond(HttpStatusCode.Created, soeknad)
                KroniskSoeknadMetrics.tellMottatt()
            }
        }

        route("/krav") {
            get("/{id}") {
                logger.info("KKG: Hent kronisk krav.")
                val innloggetFnr = hentIdentitetsnummerFraLoginToken(call.request)

                logger.info("KKG: Hent krav fra db.")
                val form = kroniskKravRepo.getById(UUID.fromString(call.parameters["id"]))

                val slettet = call.request.queryParameters.contains("slettet")
                if (form == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else if (!slettet && form.status == KravStatus.SLETTET) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    if (form.identitetsnummer != innloggetFnr) {
                        authorize(authorizer, authClient, fagerScope, form.virksomhetsnummer)
                    }

                    logger.info("KKG: Hent personinfo fra pdl.")
                    form.sendtAvNavn = form.sendtAvNavn ?: pdlService.hentNavn(innloggetFnr)
                    form.navn = form.navn ?: pdlService.hentNavn(form.identitetsnummer)

                    call.respond(HttpStatusCode.OK, form)
                }
            }

            post {
                logger.info("KKPo: Send inn kronisk krav.")

                val request = call.receive<KroniskKravRequest>()
                authorize(authorizer, authClient, fagerScope, request.virksomhetsnummer)

                logger.info("KKPo: Hent arbeidsforhold fra aareg.")
                val arbeidsforhold = aaregClient
                    .hentArbeidsforhold(request.identitetsnummer, UUID.randomUUID().toString())
                    .filter { it.arbeidsgiver.organisasjonsnummer == request.virksomhetsnummer }

                request.validate(arbeidsforhold)

                val innloggetFnr = hentIdentitetsnummerFraLoginToken(call.request)

                logger.info("KKPo: Hent personinfo fra pdl.")
                val sendtAvNavn = pdlService.hentNavn(innloggetFnr)
                val navn = pdlService.hentNavn(request.identitetsnummer)

                val krav = request.toDomain(innloggetFnr, sendtAvNavn, navn)

                logger.info("KKPo: Hent grunnbeløp.")
                belopBeregning.beregnBeløpKronisk(krav)

                logger.info("KKPo: Legg til krav i db.")
                kroniskKravRepo.insert(krav)
                bakgunnsjobbService.opprettJobb<KroniskKravProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(KroniskKravProcessor.JobbData(krav.id))
                )
                bakgunnsjobbService.opprettJobb<KroniskKravKvitteringProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(KroniskKravKvitteringProcessor.Jobbdata(krav.id))
                )
                bakgunnsjobbService.opprettJobb<ArbeidsgiverNotifikasjonProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(ArbeidsgiverNotifikasjonProcessor.JobbData(krav.id, ArbeidsgiverNotifikasjonProcessor.JobbData.SkjemaType.KroniskKrav))
                )

                call.respond(HttpStatusCode.Created, krav)
                KroniskKravMetrics.tellMottatt()
            }

            patch("/{id}") {
                logger.info("KKPa: Oppdater kronisk krav.")

                val request = call.receive<KroniskKravRequest>()

                authorize(authorizer, authClient, fagerScope, request.virksomhetsnummer)

                val innloggetFnr = hentIdentitetsnummerFraLoginToken(call.request)
                val sendtAvNavn = pdlService.hentNavn(innloggetFnr)
                val navn = pdlService.hentNavn(request.identitetsnummer)

                logger.info("KKPa: Hent arbeidsforhold fra aareg.")
                val arbeidsforhold = aaregClient
                    .hentArbeidsforhold(request.identitetsnummer, UUID.randomUUID().toString())
                    .filter { it.arbeidsgiver.organisasjonsnummer == request.virksomhetsnummer }

                request.validate(arbeidsforhold)

                val kravId = UUID.fromString(call.parameters["id"])

                logger.info("KKPa: Hent gammelt krav fra db.")
                val forrigeKrav = kroniskKravRepo.getById(kravId)
                    ?: return@patch call.respond(HttpStatusCode.NotFound)

                if (forrigeKrav.virksomhetsnummer != request.virksomhetsnummer) {
                    return@patch call.respond(HttpStatusCode.Forbidden)
                }

                val kravTilOppdatering = request.toDomain(innloggetFnr, sendtAvNavn, navn)
                belopBeregning.beregnBeløpKronisk(kravTilOppdatering)
                if (forrigeKrav.isDuplicate(kravTilOppdatering)) {
                    return@patch call.respond(HttpStatusCode.Conflict)
                }
                kravTilOppdatering.status = KravStatus.OPPDATERT

                forrigeKrav.status = KravStatus.ENDRET
                forrigeKrav.slettetAv = innloggetFnr
                forrigeKrav.slettetAvNavn = sendtAvNavn
                forrigeKrav.endretDato = LocalDateTime.now()
                forrigeKrav.endretTilId = kravTilOppdatering.id

                // Sletter gammelt krav
                logger.info("KKPa: Slett sak om gammelt krav i arbeidsgivernotifikasjon.")
                forrigeKrav.arbeidsgiverSakId?.let {
                    runBlocking { arbeidsgiverNotifikasjonKlient.hardDeleteSak(it) }
                }

                logger.info("KKPa: Oppdater gammelt krav til status: ${KravStatus.ENDRET} i db.")
                kroniskKravRepo.update(forrigeKrav)

                // Oppretter nytt krav
                logger.info("KKPa: Legg til nytt krav i db med status: ${KravStatus.OPPDATERT}.")
                kroniskKravRepo.insert(kravTilOppdatering)
                bakgunnsjobbService.opprettJobb<KroniskKravEndreProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(KroniskKravProcessor.JobbData(forrigeKrav.id))
                )
                bakgunnsjobbService.opprettJobb<KroniskKravKvitteringProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(KroniskKravKvitteringProcessor.Jobbdata(kravTilOppdatering.id))
                )
                bakgunnsjobbService.opprettJobb<ArbeidsgiverNotifikasjonProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(ArbeidsgiverNotifikasjonProcessor.JobbData(kravTilOppdatering.id, ArbeidsgiverNotifikasjonProcessor.JobbData.SkjemaType.KroniskKrav))
                )

                call.respond(HttpStatusCode.OK, kravTilOppdatering)
            }

            delete("/{id}") {
                logger.info("KKD: Slett kronisk krav.")

                val innloggetFnr = hentIdentitetsnummerFraLoginToken(call.request)
                val slettetAv = pdlService.hentNavn(innloggetFnr)
                val kravId = UUID.fromString(call.parameters["id"])
                val form = kroniskKravRepo.getById(kravId)
                    ?: return@delete call.respond(HttpStatusCode.NotFound)

                authorize(authorizer, authClient, fagerScope, form.virksomhetsnummer)

                logger.info("KKD: Slett sak i arbeidsgivernotifikasjon.")
                form.arbeidsgiverSakId?.let {
                    runBlocking { arbeidsgiverNotifikasjonKlient.hardDeleteSak(it) }
                }
                form.status = KravStatus.SLETTET
                form.slettetAv = innloggetFnr
                form.slettetAvNavn = slettetAv
                form.endretDato = LocalDateTime.now()

                logger.info("KKD: Oppdater krav til slettet i db.")
                kroniskKravRepo.update(form)
                bakgunnsjobbService.opprettJobb<KroniskKravSlettProcessor>(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(KroniskKravProcessor.JobbData(form.id))
                )
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
