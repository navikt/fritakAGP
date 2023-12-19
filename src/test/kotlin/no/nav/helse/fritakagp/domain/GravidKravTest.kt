package no.nav.helse.fritakagp.domain

import no.nav.helse.GravidTestData
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class GravidKravTest {

    @Test
    fun `Duplikatsjekk - orginal skal være lik seg selv`() {
        val krav = GravidTestData.gravidKrav
        assertTrue(krav.isDuplicate(krav))
    }

    @Test
    fun `Duplikatsjekk - forskjellige verdier i virksomhetsnummer gir ingen duplikat-treff`() {
        val orginalKrav = GravidTestData.gravidKrav
        val nyttKrav = orginalKrav.copy(virksomhetsnummer = "noe helt forskjellig")
        assertFalse(orginalKrav.isDuplicate(nyttKrav))
    }

    @Test
    fun `Duplikatsjekk - forskjellige verdier i identititetsnummer gir ingen duplikat-treff`() {
        val orginalKrav = GravidTestData.gravidKrav
        val nyttKrav = orginalKrav.copy(identitetsnummer = "noe helt forskjellig")
        assertFalse(orginalKrav.isDuplicate(nyttKrav))
    }

    @Test
    fun `Duplikatsjekk - forskjellige perioder i identititetsnummer gir ingen duplikat-treff`() {
        val agp1 = Arbeidsgiverperiode(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 16), 10, 1000.0, 1.0)
        val endretFraDato = agp1.copy(fom = agp1.fom.plusDays(1))
        val endretTilDato = agp1.copy(tom = agp1.tom.plusDays(1))
        val endretRefusjon = agp1.copy(antallDagerMedRefusjon = 11)
        val endretInntekt = agp1.copy(månedsinntekt = 1001.0)
        val endretGradering = agp1.copy(gradering = 0.8)
        val perioder = listOf(agp1, endretFraDato, endretTilDato, endretRefusjon, endretInntekt, endretGradering)

        perioder.allPairs().forEach {
            assertFalse(it.first.equals(it.second))
        }
    }

    @Test
    fun `Duplikatsjekk - forskjellige verdier i kontrolldager gir ingen duplikat-treff`() {
        val orginalKrav = GravidTestData.gravidKrav.copy(kontrollDager = 14)
        val nyttKrav = orginalKrav.copy(kontrollDager = 3)
        assertFalse(orginalKrav.isDuplicate(nyttKrav))
    }

    @Test
    fun `Duplikatsjekk - forskjellige verdier i antallDager gir ingen duplikat-treff`() {
        val orginalKrav = GravidTestData.gravidKrav.copy(antallDager = 1)
        val nyttKrav = orginalKrav.copy(antallDager = 2)
        assertFalse(orginalKrav.isDuplicate(nyttKrav))
    }

    fun <T> List<T>.allPairs(): List<Pair<T, T>> {
        val pairs = mutableListOf<Pair<T, T>>()
        for (i in 0 until size) {
            for (j in i + 1 until size) {
                pairs.add(this[i] to this[j])
            }
        }
        return pairs
    }
}
