package no.nav.helse.fritakagp.processing.kronisk.krav

import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.schemas.services.serviceengine.correspondence._2010._10.ExternalContentV2
import no.altinn.schemas.services.serviceengine.correspondence._2010._10.InsertCorrespondenceV2
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasicInsertCorrespondenceBasicV2AltinnFaultFaultFaultMessage
import no.nav.helse.fritakagp.domain.KroniskKrav
import no.nav.helse.fritakagp.domain.sladdFnr
import no.nav.helse.fritakagp.domain.TIMESTAMP_FORMAT_MED_KL
import no.nav.helse.fritakagp.domain.Arbeidsgiverperiode
import no.nav.helse.fritakagp.domain.ArbeidsgiverperiodeNy
import no.nav.helse.fritakagp.domain.DATE_FORMAT
import kotlin.math.roundToInt

interface KroniskKravKvitteringSender {
    fun send(kvittering: KroniskKrav)
}

class KroniskKravKvitteringSenderDummy : KroniskKravKvitteringSender {
    override fun send(kvittering: KroniskKrav) {
        println("Sender kvittering for krav kronisk: ${kvittering.id}")
    }
}

class KroniskKravAltinnKvitteringSender(
    private val altinnTjenesteKode: String,
    private val iCorrespondenceAgencyExternalBasic: ICorrespondenceAgencyExternalBasic,
    private val username: String,
    private val password: String
) : KroniskKravKvitteringSender {

    companion object {
        const val SYSTEM_USER_CODE = "NAV_HELSEARBEIDSGIVER"
    }

    override fun send(kvittering: KroniskKrav) {
        try {
            val receiptExternal = iCorrespondenceAgencyExternalBasic.insertCorrespondenceBasicV2(
                username, password, SYSTEM_USER_CODE, kvittering.id.toString(), mapKvitteringTilInsertCorrespondence(kvittering)
            )
            if (receiptExternal.receiptStatusCode != ReceiptStatusEnum.OK) {
                throw RuntimeException("Fikk uventet statuskode fra Altinn: ${receiptExternal.receiptStatusCode} ${receiptExternal.receiptText}")
            }
        } catch (e: ICorrespondenceAgencyExternalBasicInsertCorrespondenceBasicV2AltinnFaultFaultFaultMessage) {
            throw RuntimeException("Feil fra altinn: ${e.faultInfo}", e)
        }
    }

    fun mapKvitteringTilInsertCorrespondence(kvittering: KroniskKrav): InsertCorrespondenceV2 {
        val sladdetFnr = sladdFnr(kvittering.identitetsnummer)
        val tittel = "$sladdetFnr - Kvittering for mottatt refusjonskrav fra arbeidsgiverperioden grunnet kronisk sykdom"

        val innhold = """
        <html>
           <head>
               <meta charset="UTF-8">
           </head>
           <body>
               <div class="melding">
            <p>Kvittering for mottatt krav om fritak fra arbeidsgiverperioden grunnet risiko for høyt sykefravær knyttet til kronisk sykdom.</p>
            <p>Virksomhetsnummer: ${kvittering.virksomhetsnummer}</p>
            <p>${kvittering.opprettet.format(TIMESTAMP_FORMAT_MED_KL)}</p>
            <p>Kravet vil bli behandlet fortløpende. Ved behov vil NAV innhente ytterligere dokumentasjon.
             Har dere spørsmål, ring NAVs arbeidsgivertelefon 55 55 33 36.</p>
            <p>Dere har innrapportert følgende:</p>
            <ul>
                <li>Navn: ${kvittering.navn} </li>
                <li>Dokumentasjon vedlagt: ${if (kvittering.harVedlegg) "Ja" else "Nei"} </li>
                <li>Mottatt:  ${kvittering.opprettet.format(TIMESTAMP_FORMAT_MED_KL)}  </li>
                <li>Innrapportert av: ${kvittering.sendtAvNavn}</li>
                <li>Perioder: </li>
                <ul> ${lagrePerioder(kvittering.perioder)}</ul>
            </ul>
               </div>
           </body>
        </html>
        """.trimIndent()

        val meldingsInnhold = ExternalContentV2().withLanguageCode("1044").withMessageTitle(tittel).withMessageBody(innhold).withMessageSummary("Kvittering for krav om refusjon av arbeidsgiverperioden ifbm kronisk sykdom")

        return InsertCorrespondenceV2().withAllowForwarding(false).withReportee(kvittering.virksomhetsnummer).withMessageSender("NAV (Arbeids- og velferdsetaten)").withServiceCode(altinnTjenesteKode).withServiceEdition("1").withContent(meldingsInnhold)
    }
}

fun lagrePerioder(perioder: List<ArbeidsgiverperiodeNy>): String {
    val head = """
            <table style="width:50%">
              <tr>
                <th>Perioder</th>
                <th>Sykmeldingsgrad</th>
                <th>Dager med refusjon</th>
                <th>Beregnet månedsinntekt (NOK)</th>
                <th>Dagsats (NOK)</th>
                <th>Beløp (NOK)</th>
              </tr>"""

    val tail = "</table>"
    var rader = ""
    rader = perioder.mapIndexed { index, periode ->
        {
            lagePeriod(periode, index == perioder.size - 1)
        }
    }.joinToString()

    return head + rader + tail
}

fun lagePeriod(periode: ArbeidsgiverperiodeNy, erSisteElement: Boolean): String {
    val gradering = (periode.gradering * 100).toString() + "%"
    val delPerioder = periode.perioder!!
        .sortedBy { it.fom }
        .joinToString("<br/>") { "${it.fom} - ${it.tom}" }
    val lineSeparatorCss = if (erSisteElement) "" else "style=\"border-bottom:1px solid dimgray\""
    return """<tr $lineSeparatorCss>
                <td style="text-align:center;white-space:nowrap">
               $delPerioder
                </td>
                <td style="text-align:center">$gradering</td>
                <td style="text-align:center">${periode.antallDagerMedRefusjon}</td>
                <td style="text-align:center">${periode.månedsinntekt}</td>
                <td style="text-align:center">${periode.dagsats.roundToInt()}</td>
                <td style="text-align:center">${periode.belop.roundToInt()}</td>
            </tr>"""
}
