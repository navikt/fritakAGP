package no.nav.helse.fritakagp.web.api.resreq

import no.nav.helse.GravidTestData
import no.nav.helse.KroniskTestData
import no.nav.helse.fritakagp.domain.Omplassering
import no.nav.helse.fritakagp.domain.OmplasseringAarsak
import no.nav.helse.fritakagp.domain.Tiltak
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class GravidSoknadRequestTest {
    val navn = "Personliga Person"
    val sendtAv = "123"
    val sendtAvNavn = "Ola M Avsender"

    @Test
    internal fun `Gyldig FNR er påkrevd`() {
        validationShouldFailFor(GravidSoknadRequest::identitetsnummer) {
            GravidTestData.fullValidSoeknadRequest.copy(identitetsnummer = "01020312345").validate(true)
        }
    }

    @Test
    internal fun `Gyldig OrgNr er påkrevd dersom det er oppgitt`() {
        validationShouldFailFor(GravidSoknadRequest::virksomhetsnummer) {
            GravidTestData.fullValidSoeknadRequest.copy(virksomhetsnummer = "098765432").validate(true)
        }
    }

    @Test
    fun `Må være på virksomhetsnummer`() {
        validationShouldFailFor(GravidSoknadRequest::virksomhetsnummer) {
            GravidTestData.fullValidSoeknadRequest.copy().validate(false)
        }
    }

    @Test
    fun `Når tiltak inneholder ANNET må tiltaksbeskrivelse ha innhold`() {
        validationShouldFailFor(GravidSoknadRequest::tiltakBeskrivelse) {
            GravidTestData.fullValidSoeknadRequest.copy(
                tiltak = listOf(Tiltak.ANNET),
                tiltakBeskrivelse = "",
            ).validate(true)
        }

        GravidTestData.fullValidSoeknadRequest.copy(
            tiltak = listOf(Tiltak.ANNET),
            tiltakBeskrivelse = "dette går bra",
        ).validate(true)
    }

    @Test
    fun `Om tiltak ikke inneholder ANNET er ikke tiltaksbeskrivelse påkrevd`() {
        GravidTestData.fullValidSoeknadRequest.copy(
            tiltak = listOf(Tiltak.TILPASSET_ARBEIDSTID),
            tiltakBeskrivelse = null
        ).validate(true)
    }

    @Test
    internal fun `Bekreftelse av egenerklæring er påkrevd`() {
        validationShouldFailFor(GravidSoknadRequest::bekreftet) {
            GravidTestData.fullValidSoeknadRequest.copy(bekreftet = false).validate(true)
        }
    }

    @Test
    internal fun `mapping til domenemodell tar med harVedleggflagg`() {
        Assertions.assertThat(GravidTestData.gravidSoknadMedFil.toDomain(sendtAv, sendtAvNavn, navn).harVedlegg).isTrue()
        Assertions.assertThat(GravidTestData.fullValidSoeknadRequest.toDomain(sendtAv, sendtAvNavn, navn).harVedlegg).isFalse()
    }

    @Test
    fun `Dersom omplassering ikke er mulig må det finnes en årsak`() {
        validationShouldFailFor(GravidSoknadRequest::omplasseringAarsak) {
            GravidTestData.fullValidSoeknadRequest.copy(
                omplassering = Omplassering.IKKE_MULIG,
                omplasseringAarsak = null
            ).validate(true)
        }

        GravidTestData.fullValidSoeknadRequest.copy(
            omplassering = Omplassering.IKKE_MULIG,
            omplasseringAarsak = OmplasseringAarsak.FAAR_IKKE_KONTAKT
        ).validate(true)
    }
}
