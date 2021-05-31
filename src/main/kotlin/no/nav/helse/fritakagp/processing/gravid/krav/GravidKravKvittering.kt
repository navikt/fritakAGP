package no.nav.helse.fritakagp.processing.gravid.krav

import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.schemas.services.serviceengine.correspondence._2010._10.ExternalContentV2
import no.altinn.schemas.services.serviceengine.correspondence._2010._10.InsertCorrespondenceV2
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasicInsertCorrespondenceBasicV2AltinnFaultFaultFaultMessage
import no.nav.helse.fritakagp.domain.Arbeidsgiverperiode
import no.nav.helse.fritakagp.domain.GravidKrav
import no.nav.helse.fritakagp.processing.kronisk.krav.lagrePerioder
import java.time.format.DateTimeFormatter

interface GravidKravKvitteringSender {
    fun send(kvittering: GravidKrav)
}

class GravidKravKvitteringSenderDummy: GravidKravKvitteringSender {
    override fun send(kvittering: GravidKrav) {
        println("Sender kvittering for krav gravid: ${kvittering.id}")
    }
}

class GravidKravAltinnKvitteringSender(
    private val altinnTjenesteKode: String,
    private val iCorrespondenceAgencyExternalBasic: ICorrespondenceAgencyExternalBasic,
    private val username: String,
    private val password: String) : GravidKravKvitteringSender {

    companion object {
        const val SYSTEM_USER_CODE = "NAV_HELSEARBEIDSGIVER"
    }

    override fun send(kvittering: GravidKrav) {
        try {
            val receiptExternal = iCorrespondenceAgencyExternalBasic.insertCorrespondenceBasicV2(
                    username, password,
                    SYSTEM_USER_CODE, kvittering.id.toString(),
                    mapKvitteringTilInsertCorrespondence(kvittering)
            )
            if (receiptExternal.receiptStatusCode != ReceiptStatusEnum.OK) {
                throw RuntimeException("Fikk uventet statuskode fra Altinn: ${receiptExternal.receiptStatusCode} ${receiptExternal.receiptText}")
            }
        } catch (e: ICorrespondenceAgencyExternalBasicInsertCorrespondenceBasicV2AltinnFaultFaultFaultMessage) {
            throw RuntimeException("Feil fra altinn: ${e.faultInfo}", e)
        }
    }

    fun mapKvitteringTilInsertCorrespondence(kvittering: GravidKrav): InsertCorrespondenceV2 {
        val dateTimeFormatterMedKl = DateTimeFormatter.ofPattern("dd.MM.yyyy 'kl.' HH:mm")
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val tittel = "Kvittering for mottatt refusjonskrav fra arbeidsgiverperioden grunnet graviditet"

        val innhold = """
        <html>
           <head>
               <meta charset="UTF-8">
           </head>
           <body>
               <div class="melding">
            <p>Kvittering for mottatt krav om fritak fra arbeidsgiverperioden grunnet risiko for høyt sykefravær knyttet til graviditet.</p>
            <p>Virksomhetsnummer: ${kvittering.virksomhetsnummer}</p>
            <p>${kvittering.opprettet.format(dateTimeFormatterMedKl)}</p>
            <p>Kravet vil bli behandlet fortløpende. Ved behov vil NAV innhente ytterligere dokumentasjon.
             Har dere spørsmål, ring NAVs arbeidsgivertelefon 55 55 33 36.</p>
            <p>Dere har innrapportert følgende:</p>
            <ul>
                <li>Fødselsnummer: ${kvittering.identitetsnummer}</li>
                <li>Dokumentasjon vedlagt: ${if (kvittering.harVedlegg) "Ja" else "Nei"}</li>                      
                <li>Mottatt: ${kvittering.opprettet.format(dateTimeFormatterMedKl)}</li>
                <li>Innrapportert av: ${kvittering.sendtAv}</li>
                <li>Perioder: </li>
                <ul> ${lagrePerioder(kvittering.perioder)}</ul>
            </ul>
               </div>
           </body>
        </html>
    """.trimIndent()

        val meldingsInnhold = ExternalContentV2()
            .withLanguageCode("1044")
            .withMessageTitle(tittel)
            .withMessageBody(innhold)
            .withMessageSummary("Kvittering for krav om refusjon av arbeidsgiverperioden ifbm graviditetsrelatert fravær")


        return InsertCorrespondenceV2()
            .withAllowForwarding(false)
            .withReportee(kvittering.virksomhetsnummer)
            .withMessageSender("NAV (Arbeids- og velferdsetaten)")
            .withServiceCode(altinnTjenesteKode)
            .withServiceEdition("1")
            .withContent(meldingsInnhold)
    }

}