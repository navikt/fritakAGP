package no.nav.helse.fritakagp.processing.kronisk.soeknad

import no.nav.helse.fritakagp.domain.KroniskSoeknad
import no.nav.helse.fritakagp.domain.TIMESTAMP_FORMAT
import org.apache.commons.lang3.text.WordUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType0Font
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.Month

class KroniskSoeknadPDFGenerator {
    private val FONT_SIZE = 11f
    private val LINE_HEIGHT = 15f
    private val MARGIN_X = 40f
    private val MARGIN_Y = 40f
    private val FONT_NAME = "fonts/ARIALUNI.TTF"

    fun lagPDF(soeknad: KroniskSoeknad): ByteArray {
        val doc = PDDocument()
        val font = PDType0Font.load(doc, this::class.java.classLoader.getResource(FONT_NAME).openStream())

        val curPage = PDPage()
        doc.addPage(curPage)

        val content = PDPageContentStream(doc, curPage)
        content.beginText()
        val mediaBox = curPage.mediaBox
        val startX = mediaBox.lowerLeftX + MARGIN_X
        val startY = mediaBox.upperRightY - MARGIN_Y
        content.newLineAtOffset(startX, startY)
        content.setFont(font, FONT_SIZE + 4)
        content.showText(KroniskSoeknad.tittel)
        content.setFont(font, FONT_SIZE)

        content.writeTextWrapped("Mottatt: ${soeknad.opprettet.format(TIMESTAMP_FORMAT)}", 4)
        content.writeTextWrapped("Sendt av: ${soeknad.sendtAvNavn}")
        content.writeTextWrapped("Person navn: ${soeknad.navn}")
        content.writeTextWrapped("Arbeidsgiver oppgitt i søknad: ${soeknad.virksomhetsnavn} (${soeknad.virksomhetsnummer})")

        if (soeknad.ikkeHistoriskFravaer) {
            content.writeTextWrapped("Det finnes ikke historisk fravær på grunn av nyansettelse, lengre permisjon eller annet.", 2)
        } else {
            val totaltAntallDager = soeknad.fravaer.map { it.antallDagerMedFravaer }.sum()
            content.writeTextWrapped("Totalt antall fraværsdager siste 2 år: $totaltAntallDager", 2)

            content.writeTextWrapped("Fraværsdager per måned siste 2 år: ", 2)
            content.writeTextWrapped("Antall fraværsperioder siste 2 år: ${soeknad.antallPerioder}")
            val yearlyFravaer = soeknad.fravaer.sortedByDescending { it.yearMonth }.groupBy { it.yearMonth.substring(0, 4) }

            yearlyFravaer.forEach { yearGroup ->
                content.writeTextWrapped(yearGroup.key)
                content.newLineAtOffset(0F, -LINE_HEIGHT)
                yearGroup.value.sortedBy { it.yearMonth }.forEach {
                    content.showText("${it.toLocalDate().toName()}: ${it.antallDagerMedFravaer}   ")
                }
                content.newLineAtOffset(0F, -LINE_HEIGHT)
            }
        }

        content.endText()
        content.close()
        val out = ByteArrayOutputStream()
        doc.save(out)
        val ba = out.toByteArray()
        doc.close()
        return ba
    }

    private fun LocalDate.toName(): String {
        return when (this.month) {
            Month.JANUARY -> "Jan"
            Month.FEBRUARY -> "Feb"
            Month.MARCH -> "Mar"
            Month.APRIL -> "Apr"
            Month.MAY -> "Mai"
            Month.JUNE -> "Jun"
            Month.JULY -> "Jul"
            Month.AUGUST -> "Aug"
            Month.SEPTEMBER -> "Sep"
            Month.OCTOBER -> "Okt"
            Month.NOVEMBER -> "Nov"
            Month.DECEMBER -> "Des"
        }
    }

    private fun PDPageContentStream.writeTextWrapped(text: String, spacing: Int = 1) {
        WordUtils.wrap(text, 100).split('\n').forEach {
            this.newLineAtOffset(0F, -LINE_HEIGHT * spacing)
            this.showText(it)
        }
    }
}
