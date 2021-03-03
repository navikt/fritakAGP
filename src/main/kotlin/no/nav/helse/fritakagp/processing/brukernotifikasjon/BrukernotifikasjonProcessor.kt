package no.nav.helse.fritakagp.processing.brukernotifikasjon

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.builders.BeskjedBuilder
import no.nav.brukernotifikasjon.schemas.builders.NokkelBuilder
import no.nav.helse.arbeidsgiver.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.helse.arbeidsgiver.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.helse.fritakagp.BrukernotifikasjonerMetrics
import no.nav.helse.fritakagp.db.GravidKravRepository
import no.nav.helse.fritakagp.db.GravidSoeknadRepository
import no.nav.helse.fritakagp.db.KroniskKravRepository
import no.nav.helse.fritakagp.db.KroniskSoeknadRepository
import no.nav.helse.fritakagp.integration.kafka.BrukernotifikasjonBeskjedSender
import java.net.URL
import java.time.LocalDateTime
import java.util.*

class BrukernotifikasjonProcessor(
    private val gravidKravRepo: GravidKravRepository,
    private val gravidSoeknadRepo: GravidSoeknadRepository,
    private val kroniskKravRepo: KroniskKravRepository,
    private val kroniskSoeknadRepo: KroniskSoeknadRepository,
    private val om: ObjectMapper,
    private val kafkaProducerFactory: BrukernotifikasjonBeskjedSender,
    private val serviceuserUsername: String,
    private val frontendAppBaseUrl: String = "https://arbeidsgiver.nav.no/fritakagp"
) : BakgrunnsjobbProsesserer {

    companion object {
        val JOB_TYPE = "brukernotifikasjon"
    }
    override val type: String get() = JOB_TYPE

    override fun prosesser(jobb: Bakgrunnsjobb) {
        val jobbData = om.readValue<Jobbdata>(jobb.data)
        val beskjed = map(jobbData)

        val nokkel = NokkelBuilder()
            .withEventId(UUID.randomUUID().toString())
            .withSystembruker(serviceuserUsername)
            .build()

        kafkaProducerFactory.sendMessage(nokkel, beskjed)

        BrukernotifikasjonerMetrics.labels(jobbData.skjemaType.name).inc()
    }

    private fun map(jobbData: Jobbdata): Beskjed {
        return when(jobbData.skjemaType) {
            Jobbdata.SkjemaType.KroniskKrav -> {
                val skjema = kroniskKravRepo.getById(jobbData.skjemaId) ?: throw IllegalArgumentException("Fant ikke $jobbData")
                buildBeskjed(skjema.id, "$frontendAppBaseUrl/kronisk/krav/${skjema.id}", skjema.identitetsnummer, skjema.opprettet)
            }

            Jobbdata.SkjemaType.KroniskSøknad -> {
                val skjema = kroniskSoeknadRepo.getById(jobbData.skjemaId) ?: throw IllegalArgumentException("Fant ikke $jobbData")
                buildBeskjed(skjema.id, "$frontendAppBaseUrl/kronisk/soeknad/${skjema.id}", skjema.identitetsnummer, skjema.opprettet)
            }

            Jobbdata.SkjemaType.GravidKrav -> {
                val skjema = gravidKravRepo.getById(jobbData.skjemaId) ?: throw IllegalArgumentException("Fant ikke $jobbData")
                buildBeskjed(skjema.id, "$frontendAppBaseUrl/gravid/krav/${skjema.id}", skjema.identitetsnummer, skjema.opprettet)
            }
            Jobbdata.SkjemaType.GravidSøknad -> {
                val skjema = gravidSoeknadRepo.getById(jobbData.skjemaId) ?: throw IllegalArgumentException("Fant ikke $jobbData")
                buildBeskjed(skjema.id, "$frontendAppBaseUrl/gravid/soeknad/${skjema.id}", skjema.identitetsnummer, skjema.opprettet)
            }
        }
    }

    private fun buildBeskjed(
        id: UUID,
        linkUrl: String,
        identitetsnummer: String,
        hendselstidspunkt: LocalDateTime,

        ): Beskjed {

        val synligFremTil =  LocalDateTime.now().plusDays(31)
        val beskjed = BeskjedBuilder()
            .withGrupperingsId(id.toString())
            .withFodselsnummer(identitetsnummer)
            .withLink(URL(linkUrl))
            .withSikkerhetsnivaa(4)
            .withSynligFremTil(synligFremTil)
            .withTekst("Arbeisdgiveren din har søkt om utvidet støtte fra NAV angående sykepenger til deg.")
            .withEksternVarsling(false)
            .withTidspunkt(hendselstidspunkt)
            .build()

        return beskjed
    }


    data class Jobbdata(
        val skjemaId: UUID,
        val skjemaType: SkjemaType
    ) {
        enum class SkjemaType {
            KroniskKrav,
            KroniskSøknad,
            GravidKrav,
            GravidSøknad
        }
    }
}
