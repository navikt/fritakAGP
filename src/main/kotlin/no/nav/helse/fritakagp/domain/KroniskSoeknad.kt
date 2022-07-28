package no.nav.helse.fritakagp.domain

import no.nav.helse.fritakagp.db.SimpleJsonbEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class KroniskSoeknad(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: LocalDateTime = LocalDateTime.now(),

    override val virksomhetsnummer: String,
    val identitetsnummer: String,
    // Må være null for tidligere verdier er lagret med null
    var navn: String? = null,
    val fravaer: Set<FravaerData>,
    val ikkeHistoriskFravaer: Boolean = false,
    val antallPerioder: Int,
    val bekreftet: Boolean,
    val harVedlegg: Boolean = false,

    val sendtAv: String,
    var virksomhetsnavn: String? = null,

    /**
     * ID fra joark etter arkivering
     */
    var journalpostId: String? = null,

    /**
     * ID fra oppgave etter opprettelse av oppgave
     */
    var oppgaveId: String? = null,
    // Må være null for tidligere verdier er lagret med null
    var sendtAvNavn: String? = null
) : SimpleJsonbEntity

data class FravaerData(
    val yearMonth: String,
    val antallDagerMedFravaer: Float
) {
    fun toLocalDate() = LocalDate.parse("$yearMonth-01")
}
