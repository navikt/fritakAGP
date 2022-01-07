package no.nav.helse.fritakagp.web.api.resreq.validation

import no.nav.helse.arbeidsgiver.web.validation.OrganisasjonsnummerConstraint
import no.nav.helse.arbeidsgiver.web.validation.OrganisasjonsnummerValidator
import no.nav.helse.fritakagp.domain.Arbeidsgiverperiode
import no.nav.helse.fritakagp.domain.FravaerData
import no.nav.helse.fritakagp.domain.KroniskSoeknad
import no.nav.helse.fritakagp.domain.GodkjenteFiletyper
import org.valiktor.Constraint
import org.valiktor.Validator
import java.time.LocalDate
import java.time.temporal.ChronoUnit

interface CustomConstraint : Constraint {
    override val messageBundle: String
        get() = "validation/validation-messages"
}

class RefusjonsdagerKanIkkeOverstigePeriodelengdenConstraint : CustomConstraint
fun <E> Validator<E>.Property<Int?>.refusjonsDagerIkkeOverstigerPeriodelengde(ap: Arbeidsgiverperiode) =
    this.validate(RefusjonsdagerKanIkkeOverstigePeriodelengdenConstraint()) {
        return@validate ChronoUnit.DAYS.between(ap.fom, ap.tom.plusDays(1)) >= it!!
    }

class MåVæreVirksomhetContraint : CustomConstraint
fun <E> Validator<E>.Property<String?>.isVirksomhet(erVirksomhet: Boolean) =
    this.validate(MåVæreVirksomhetContraint()) { erVirksomhet }

class FraDatoKanIkkeKommeEtterTomDato : CustomConstraint
fun <E> Validator<E>.Property<LocalDate?>.datoerHarRiktigRekkefolge(tom: LocalDate) =
    this.validate(FraDatoKanIkkeKommeEtterTomDato()) { fom -> fom!!.isEqual(tom) || fom!!.isBefore(tom) }

class MaanedsInntektErStorreEnTiMil : CustomConstraint
fun <E> Validator<E>.Property<Double?>.maanedsInntektErMellomNullOgTiMil() =
    this.validate(MaanedsInntektErStorreEnTiMil()) {
        it!! > 0.0 && it!! <= TiMil
    }

class AntallPerioderErMellomNullOgTreHundre() : CustomConstraint
fun <E> Validator<E>.Property<Iterable<KroniskSoeknad>?>.antallPerioderErMellomNullOgTreHundre() =
    this.validate(AntallPerioderErMellomNullOgTreHundre()) { ps ->
        ps!!.any { p -> p.antallPerioder >= 1 && p.antallPerioder < 300 }
    }

class DataUrlExtensionConstraints : CustomConstraint
fun <E> Validator<E>.Property<String?>.isGodskjentFiletyper() =
    this.validate(DataUrlExtensionConstraints()) {
        return@validate enumContains<GodkjenteFiletyper>(extractFilExtDel(it!!.uppercase()))
    }

class DataUrlBase64Constraints : CustomConstraint
fun <E> Validator<E>.Property<String?>.isNotStorreEnn(maxSize: Long) =
    this.validate(DataUrlBase64Constraints()) {
        return@validate extractBase64Del(it!!).toByteArray().size <= maxSize
    }

class MaxAgeFravaersDataConstraint : CustomConstraint
fun <E> Validator<E>.Property<Iterable<FravaerData>?>.ingenDataEldreEnn(aar: Long) =
    this.validate(MaxAgeFravaersDataConstraint()) { ps ->
        val minDate = LocalDate.now().minusYears(aar).withDayOfMonth(1)
        return@validate !ps!!.any {
            LocalDate.parse("${it.yearMonth}-01").isBefore(minDate)
        }
    }

class NoFutureFravaersDataConstraint : CustomConstraint
fun <E> Validator<E>.Property<Iterable<FravaerData>?>.ingenDataFraFremtiden() =
    this.validate(NoFutureFravaersDataConstraint()) { ps ->
        val maxDate = LocalDate.now().withDayOfMonth(1)
        return@validate !ps!!.any {
            LocalDate.parse("${it.yearMonth}-01").isAfter(maxDate)
        }
    }

class MaxNumFravaersdagFravaersDataConstraint : CustomConstraint
fun <E> Validator<E>.Property<Iterable<FravaerData>?>.ikkeFlereFravaersdagerEnnDagerIMaanden() =
    this.validate(MaxNumFravaersdagFravaersDataConstraint()) { ps ->
        return@validate !ps!!.any {
            LocalDate.parse("${it.yearMonth}-01").lengthOfMonth() < it.antallDagerMedFravaer
        }
    }

inline fun <reified T : Enum<T>> enumContains(name: String): Boolean {
    return enumValues<T>().any { it.name == name }
}

fun extractBase64Del(dataUrl: String): String = dataUrl.substringAfter("base64,")
fun extractFilExtDel(dataUrl: String): String = dataUrl.substring(0, dataUrl.indexOf(';')).substringAfter('/')

class VirusCheckConstraint : CustomConstraint

const val TiMil = 10000000.0
