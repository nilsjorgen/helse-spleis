package no.nav.helse.person

import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.til
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.testhelpers.*
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import no.nav.helse.økonomi.Økonomi
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.math.roundToInt

internal class InfotrygdhistorikkElementTest {

    private companion object {
        private const val ORGNUMMER = "987654321"
        private val kilde = TestEvent.testkilde
    }

    private lateinit var aktivitetslogg: Aktivitetslogg

    @BeforeEach
    fun setup() {
        aktivitetslogg = Aktivitetslogg()
    }

    @Test
    fun `lik historikk`() {
        val perioder = listOf(
            Infotrygdhistorikk.Friperiode(1.januar til 31.januar),
            Infotrygdhistorikk.Utbetalingsperiode("orgnr", 1.februar til 28.februar, 100.prosent, 25000.månedlig)
        )
        val inntekter = listOf(
            Infotrygdhistorikk.Inntektsopplysning("orgnr", 1.januar, 25000.månedlig, true)
        )
        val arbeidskategorikoder = mapOf(
            "01" to 1.januar
        )
        assertEquals(historikkelement().hashCode(), historikkelement().hashCode())
        assertNotEquals(historikkelement().hashCode(), historikkelement(perioder).hashCode())
        assertEquals(historikkelement(perioder).hashCode(), historikkelement(perioder).hashCode())
        assertEquals(historikkelement(inntekter = inntekter).hashCode(), historikkelement(inntekter = inntekter).hashCode())
        assertNotEquals(historikkelement(perioder, inntekter).hashCode(), historikkelement(inntekter = inntekter).hashCode())
        assertEquals(historikkelement(perioder, inntekter, arbeidskategorikoder).hashCode(), historikkelement(perioder, inntekter, arbeidskategorikoder).hashCode())
        assertNotEquals(historikkelement(perioder, inntekter).hashCode(), historikkelement(perioder, inntekter, arbeidskategorikoder).hashCode())
    }

    @Test
    fun `like perioder`() {
        val ferie = Infotrygdhistorikk.Friperiode(1.januar til 31.januar)
        val ukjent = Infotrygdhistorikk.Ukjent(1.januar til 31.januar)
        val utbetalingAG1 = Infotrygdhistorikk.Utbetalingsperiode("ag1", 1.februar til 28.februar, 100.prosent, 25000.månedlig)
        val utbetalingAG2 = Infotrygdhistorikk.Utbetalingsperiode("ag2", 1.februar til 28.februar, 100.prosent, 25000.månedlig)
        assertEquals(ferie, ferie)
        assertEquals(ukjent, ukjent)
        assertNotEquals(ferie, ukjent)
        assertNotEquals(ferie.hashCode(), ukjent.hashCode())
        assertNotEquals(ferie, utbetalingAG1)
        assertNotEquals(ferie.hashCode(), utbetalingAG1.hashCode())
        assertNotEquals(utbetalingAG1, utbetalingAG2)
        assertNotEquals(utbetalingAG1.hashCode(), utbetalingAG2.hashCode())
        assertEquals(utbetalingAG1, utbetalingAG1)
        assertEquals(utbetalingAG1.hashCode(), utbetalingAG1.hashCode())
    }

    @Test
    fun `utbetalingstidslinje - ferie`() {
        val ferie = Infotrygdhistorikk.Friperiode(1.januar til 10.januar)
        val inspektør = UtbetalingstidslinjeInspektør(ferie.utbetalingstidslinje())
        assertEquals(10, inspektør.fridagTeller)
    }

    @Test
    fun `utbetalingstidslinje - ukjent`() {
        val ferie = Infotrygdhistorikk.Ukjent(1.januar til 10.januar)
        val inspektør = UtbetalingstidslinjeInspektør(ferie.utbetalingstidslinje())
        assertEquals(0, inspektør.size)
    }

    @Test
    fun `utbetalingstidslinje - utbetaling`() {
        val utbetaling = Infotrygdhistorikk.Utbetalingsperiode("ag1", 1.januar til 10.januar, 100.prosent, 25000.månedlig)
        val inspektør = UtbetalingstidslinjeInspektør(utbetaling.utbetalingstidslinje())
        assertEquals(8, inspektør.navDagTeller)
        assertEquals(2, inspektør.navHelgDagTeller)
    }

    @Test
    fun `sykdomstidslinje - ferie`() {
        val ferie = Infotrygdhistorikk.Friperiode(1.januar til 10.januar)
        val inspektør = SykdomstidslinjeInspektør(ferie.sykdomstidslinje(kilde))
        assertTrue(inspektør.dager.values.all { it is Dag.Feriedag })
        assertEquals(10, inspektør.dager.size)
    }

    @Test
    fun `sykdomstidslinje - ukjent`() {
        val ferie = Infotrygdhistorikk.Ukjent(1.januar til 10.januar)
        val inspektør = SykdomstidslinjeInspektør(ferie.sykdomstidslinje(kilde))
        assertTrue(inspektør.dager.isEmpty())
    }

    @Test
    fun `sykdomstidslinje - utbetaling`() {
        val utbetaling = Infotrygdhistorikk.Utbetalingsperiode("ag1", 1.januar til 10.januar, 100.prosent, 25000.månedlig)
        val inspektør = SykdomstidslinjeInspektør(utbetaling.sykdomstidslinje(kilde))
        assertTrue(inspektør.dager.values.all { it is Dag.Sykedag || it is Dag.SykHelgedag })
        assertEquals(10, inspektør.dager.size)
    }

    @Test
    fun `skjæringstidspunkt lik null resulterer i passert validering av redusert utbetaling`() {
        val arbeidskategorikoder = mapOf("01" to 1.januar)
        val element = historikkelement(arbeidskategorikoder = arbeidskategorikoder)
        assertTrue(element.valider(aktivitetslogg, Periode(2.januar, 31.januar), null))
        assertFalse(aktivitetslogg.hasWarningsOrWorse())
    }

    @Test
    fun `validering skal feile når bruker har redusert utbetaling og skjæringstidspunkt i Infotrygd`() {
        val arbeidskategorikoder = mapOf("07" to 1.januar)
        val element = historikkelement(arbeidskategorikoder = arbeidskategorikoder)
        assertFalse(element.valider(aktivitetslogg, Periode(6.januar, 23.januar), 1.januar))
        assertTrue(aktivitetslogg.hasErrorsOrWorse())
    }

    @Test
    fun `validering feiler ikke når det ikke er redusert utbetaling i Infotrygd, men skjæringstidspunkt i Infotrygd`() {
        val arbeidskategorikoder = mapOf("01" to 1.januar)
        val element = historikkelement(arbeidskategorikoder = arbeidskategorikoder)
        assertTrue(element.valider(aktivitetslogg, Periode(6.januar, 23.januar), 1.januar))
        assertFalse(aktivitetslogg.hasErrorsOrWorse())
    }

    @Test
    fun `validering skal ikke feile når bruker har redusert utbetaling i Infotrygd, men skjæringstidspunkt i Spleis`() {
        val arbeidskategorikoder = mapOf("07" to 1.januar)
        val utbetalinger = listOf(
            Infotrygdhistorikk.Utbetalingsperiode(ORGNUMMER, 1.januar til 5.januar, 100.prosent, 1234.daglig)
        )
        val element = historikkelement(perioder = utbetalinger, arbeidskategorikoder = arbeidskategorikoder)
        assertTrue(element.valider(aktivitetslogg, Periode(7.januar, 23.januar), 7.januar))
        assertFalse(aktivitetslogg.hasErrorsOrWorse())
    }

    @Test
    fun `validering skal feile når bruker har redusert utbetaling og skjæringstidspunkt i Infotrygd  - flere arbeidsgivere`() {
        val arbeidskategorikoder = mapOf("01" to 1.januar, "07" to 6.januar)
        val element = historikkelement(arbeidskategorikoder = arbeidskategorikoder)
        assertFalse(element.valider(aktivitetslogg, Periode(11.januar, 23.januar), 1.januar))
        assertTrue(aktivitetslogg.hasErrorsOrWorse())
    }

    @Test
    fun `validering skal ikke feile når bruker ikke har redusert utbetaling og skjæringstidspunkt i Infotrygd  - flere arbeidsgivere`() {
        val arbeidskategorikoder = mapOf("01" to 1.januar, "01" to 6.januar)
        val element = historikkelement(arbeidskategorikoder = arbeidskategorikoder)
        assertTrue(element.valider(aktivitetslogg, Periode(11.januar, 23.januar), 1.januar))
        assertFalse(aktivitetslogg.hasErrorsOrWorse())
    }

    @Test
    fun `validering skal ikke feile når utbetalingshistorikken er tom`() {
        val element = historikkelement()
        assertTrue(element.valider(aktivitetslogg, Periode(11.januar, 23.januar), 1.januar))
        assertFalse(aktivitetslogg.hasErrorsOrWorse())
    }

    @Test
    fun `direkteutbetaling til bruker støttes ikke ennå`() {
        val utbetalinger = listOf(
            Infotrygdhistorikk.Utbetalingsperiode(ORGNUMMER, 1.januar til 5.januar, 100.prosent, 1234.daglig)
        )
        val element = historikkelement(
            perioder = utbetalinger,
            inntekter = listOf(
                Infotrygdhistorikk.Inntektsopplysning("123456789", 1.januar, 1234.månedlig, false)
            )
        )
        assertFalse(element.valider(aktivitetslogg, Periode(6.januar, 31.januar), 1.januar))
        assertTrue(aktivitetslogg.hasErrorsOrWorse())
    }

    @Test
    fun `forlengelser fra infotrygd med tilstøtende periode med samme orgnr er ok`() {
        val utbetalinger = listOf(
            Infotrygdhistorikk.Utbetalingsperiode("1234", 1.januar til 3.januar, 100.prosent, 1234.daglig),
            Infotrygdhistorikk.Utbetalingsperiode(ORGNUMMER, 1.januar til 5.januar, 100.prosent, 1234.daglig)
        )
        val element = historikkelement(utbetalinger)

        assertTrue(element.valider(aktivitetslogg, Periode(6.januar, 31.januar), 1.januar))
        assertFalse(aktivitetslogg.hasErrorsOrWorse())
    }

    @Test
    fun `flere inntektsopplysninger på samme orgnr er ok`() {
        val utbetalinger = listOf(
            Infotrygdhistorikk.Utbetalingsperiode(ORGNUMMER, 1.januar til 5.januar, 100.prosent, 1234.daglig)
        )
        val element = historikkelement(
            perioder = utbetalinger,
            inntekter = listOf(
                Infotrygdhistorikk.Inntektsopplysning("123456789", 1.februar, 1234.månedlig, true),
                Infotrygdhistorikk.Inntektsopplysning("123456789", 1.januar, 1234.månedlig, true)
            )
        )

        assertTrue(element.valider(aktivitetslogg, Periode(6.januar, 31.januar), 1.januar))
        assertFalse(aktivitetslogg.hasErrorsOrWorse())
    }

    @Test
    fun `flere inntektsopplysninger gir ikke feil dersom de er gamle`() {
        val utbetalinger = listOf(
            Infotrygdhistorikk.Utbetalingsperiode(ORGNUMMER, 1.januar til 5.januar, 100.prosent, 1234.daglig)
        )
        val element = historikkelement(
            perioder = utbetalinger,
            inntekter = listOf(
                Infotrygdhistorikk.Inntektsopplysning("123456789", 1.januar, 1234.månedlig, true),
                Infotrygdhistorikk.Inntektsopplysning("987654321", 1.januar.minusYears(1), 1234.månedlig, true)
            )
        )

        assertTrue(element.valider(aktivitetslogg, Periode(6.januar, 31.januar), 1.januar))
        assertFalse(aktivitetslogg.hasErrorsOrWorse())
    }

    @Test
    fun `lager ikke warning når dagsats endrer seg i en sammenhengende periode som følge av Grunnbeløpjustering`() {
        val utbetalinger = listOf(
            Infotrygdhistorikk.Utbetalingsperiode(ORGNUMMER, 1.april til 30.april, 100.prosent, 2161.daglig),
            Infotrygdhistorikk.Utbetalingsperiode(ORGNUMMER, 1.mai til 31.mai, 100.prosent, 2236.daglig)
        )
        val element = historikkelement(utbetalinger)
        assertTrue(element.valider(aktivitetslogg, Periode(1.juni, 30.juni), 1.april))
        assertFalse(aktivitetslogg.hasWarningsOrWorse())
    }

    @Test
    fun `lager ikke warning når dagsats endres pga gradering i en sammenhengende periode`() {
        val gradering = .5
        val dagsats = 2468
        val utbetalinger = listOf(
            Infotrygdhistorikk.Utbetalingsperiode(
                ORGNUMMER, 1.januar til 31.januar, (100 * gradering).roundToInt().prosent, (dagsats * gradering).roundToInt().daglig
            ),
            Infotrygdhistorikk.Utbetalingsperiode(ORGNUMMER, 1.februar til 28.februar, 100.prosent, dagsats.daglig)
        )
        val element = historikkelement(utbetalinger)
        assertTrue(element.valider(aktivitetslogg, Periode(1.april, 30.april), 1.april))
        assertFalse(aktivitetslogg.hasWarningsOrWorse())
    }

    @Test
    fun `lager ikke warning når dagsats ikke endrer seg i en sammenhengende periode`() {
        val utbetalinger = listOf(
            Infotrygdhistorikk.Utbetalingsperiode(ORGNUMMER, 1.januar til 31.januar, 100.prosent, 1234.daglig),
            Infotrygdhistorikk.Utbetalingsperiode(ORGNUMMER, 1.februar til 28.februar, 100.prosent, 1234.daglig)
        )
        val element = historikkelement(utbetalinger)
        assertTrue(element.valider(aktivitetslogg, Periode(1.april, 30.april), 1.april))
        assertFalse(aktivitetslogg.hasWarningsOrWorse())
    }

    @Test
    fun `RefusjonTilArbeidsgiver mappes til utbetalingstidslinje`() {
        val utbetalinger = listOf(
            Infotrygdhistorikk.Utbetalingsperiode(ORGNUMMER, 1.januar til 10.januar, 100.prosent, 1234.daglig),
            Infotrygdhistorikk.Utbetalingsperiode(ORGNUMMER, 1.januar til 10.januar, 100.prosent, 1234.daglig)
        )

        val tidslinje = utbetalinger.map { it.utbetalingstidslinje() }.reduce(Utbetalingstidslinje::plus)

        assertFalse(aktivitetslogg.hasWarningsOrWorse())

        val inspektør = Inspektør().apply { tidslinje.accept(this) }
        assertEquals(1.januar, inspektør.førsteDag)
        assertEquals(10.januar, inspektør.sisteDag)
        assertEquals(8, inspektør.navDagTeller)
    }

    @Test
    fun `RefusjonTilArbeidsgiver regnes som utbetalingsdag selv om den overlapper med ferie`() {
        val utbetalinger = listOf(
            Infotrygdhistorikk.Utbetalingsperiode(ORGNUMMER, 1.januar til 10.januar, 100.prosent, 1234.daglig),
            Infotrygdhistorikk.Friperiode(5.januar til 20.januar),
            Infotrygdhistorikk.Utbetalingsperiode(ORGNUMMER, 15.januar til 25.januar, 100.prosent, 1234.daglig)
        )

        val tidslinje = utbetalinger.map { it.utbetalingstidslinje() }.reduce(Utbetalingstidslinje::plus)

        assertFalse(aktivitetslogg.hasWarningsOrWorse())

        val inspektør = Inspektør().apply { tidslinje.accept(this) }
        assertEquals(1.januar, inspektør.førsteDag)
        assertEquals(25.januar, inspektør.sisteDag)
        assertEquals(17, inspektør.navDagTeller)
    }

    @Test
    fun `Feiler ikke selv om ukjent dag overlappes helt av ReduksjonArbeidsgiverRefusjon`() {
        val utbetalinger = listOf(
            Infotrygdhistorikk.Utbetalingsperiode(ORGNUMMER, 1.januar til 10.januar, 100.prosent, 1234.daglig),
            Infotrygdhistorikk.Ukjent(5.januar til 5.januar)
        )

        val tidslinje = utbetalinger.map { it.utbetalingstidslinje() }.reduce(Utbetalingstidslinje::plus)

        val inspektør = Inspektør().apply { tidslinje.accept(this) }
        assertEquals(1.januar, inspektør.førsteDag)
        assertEquals(10.januar, inspektør.sisteDag)
    }

    @Test
    fun `Validerer ok hvis det ikke finnes noen utbetalinger fra Infotrygd`() {
        val element = historikkelement()
        assertTrue(element.valider(aktivitetslogg, Periode(1.januar, 1.januar), 1.januar))
        assertFalse(aktivitetslogg.hasWarningsOrWorse()) { aktivitetslogg.toString() }
    }

    @Test
    fun `Utbetalinger i Infotrygd som overlapper med tidslinjen`() {
        val utbetalinger = listOf(
            Infotrygdhistorikk.Utbetalingsperiode(ORGNUMMER, 1.januar til 10.januar, 100.prosent, 1234.daglig)
        )
        val element = historikkelement(utbetalinger)
        assertFalse(element.valider(aktivitetslogg, Periode(1.januar, 1.januar), 1.januar))
        assertTrue(aktivitetslogg.hasErrorsOrWorse()) { aktivitetslogg.toString() }
    }

    @Test
    fun `Utbetalinger i Infotrygd som er nærmere enn 18 dager fra tidslinjen`() {
        val utbetalinger = listOf(
            Infotrygdhistorikk.Utbetalingsperiode(ORGNUMMER, 1.januar til 10.januar, 100.prosent, 1234.daglig)
        )
        val element = historikkelement(utbetalinger)
        assertTrue(element.valider(aktivitetslogg, Periode(28.januar, 28.januar), 28.januar))
        assertFalse(aktivitetslogg.hasWarningsOrWorse())
    }

    @Test
    fun `Utbetalinger i Infotrygd som er eldre enn 18 dager fra tidslinjen`() {
        val utbetalinger = listOf(
            Infotrygdhistorikk.Utbetalingsperiode(ORGNUMMER, 1.januar til 10.januar, 100.prosent, 1234.daglig)
        )
        val element = historikkelement(utbetalinger)
        assertTrue(element.valider(aktivitetslogg, Periode(29.januar, 29.januar), 29.januar))
        assertFalse(aktivitetslogg.hasWarningsOrWorse())
    }

    @Test
    fun `Validerer ok hvis perioder er eldre enn 26 uker før første fraværsdag`() {
        val utbetalinger = listOf(
            Infotrygdhistorikk.Utbetalingsperiode(ORGNUMMER, 1.januar til 10.januar, 100.prosent, 1234.daglig),
            Infotrygdhistorikk.Ukjent(1.januar til 10.januar)
        )
        val element = historikkelement(utbetalinger)
        assertTrue(element.valider(aktivitetslogg, Periode(1.august, 1.august), 1.august))
        assertFalse(aktivitetslogg.hasWarningsOrWorse()) { aktivitetslogg.toString() }
    }

    @Test
    fun `Validering ignorerer maksdato hvis perioder er eldre enn 26 uker før første fraværsdag`() {
        val utbetalinger = listOf(
            Infotrygdhistorikk.Utbetalingsperiode(ORGNUMMER, 1.januar til 10.januar, 100.prosent, 1234.daglig)
        )
        val element = historikkelement(utbetalinger)
        assertTrue(element.valider(aktivitetslogg, Periode(1.august, 1.august), 1.august))
        assertFalse(aktivitetslogg.hasWarningsOrWorse()) { aktivitetslogg.toString() }
    }

    @Test
    fun `validering av inntektsopplysninger feiler ikke for skjæringstidspunkt null`() {
        val utbetalinger = listOf(
            Infotrygdhistorikk.Utbetalingsperiode(ORGNUMMER, 1.januar til 5.januar, 100.prosent, 1234.daglig)
        )
        val element = historikkelement(utbetalinger, listOf(
            Infotrygdhistorikk.Inntektsopplysning(ORGNUMMER, 1.januar, 1234.månedlig, true),
        ))
        assertTrue(element.valider(aktivitetslogg, Periode(10.januar, 31.januar), null))
        assertFalse(aktivitetslogg.hasErrorsOrWorse())
    }

    @Test
    fun `validering gir warning hvis vi har to inntekter for samme arbeidsgiver på samme dato`() {
        val element = historikkelement(inntekter = listOf(
            Infotrygdhistorikk.Inntektsopplysning(ORGNUMMER, 1.januar, 1234.månedlig, true),
            Infotrygdhistorikk.Inntektsopplysning(ORGNUMMER, 1.januar, 4321.månedlig, true),
        ))

        assertTrue(element.valider(aktivitetslogg, Periode(1.januar, 31.januar), null))
        assertTrue(aktivitetslogg.hasWarningsOrWorse())
        assertTrue(element.valider(aktivitetslogg, Periode(1.januar, 31.januar), null))
        assertFalse(aktivitetslogg.hasErrorsOrWorse())
    }

    @Test
    fun `validering gir ikke warning hvis vi har to inntekter for samme arbeidsgiver på forskjellig dato`() {
        val element = historikkelement(inntekter = listOf(
            Infotrygdhistorikk.Inntektsopplysning(ORGNUMMER, 2.januar, 1234.månedlig, true),
            Infotrygdhistorikk.Inntektsopplysning(ORGNUMMER, 1.januar, 4321.månedlig, true),
        ))
        assertTrue(element.valider(aktivitetslogg, Periode(1.januar, 31.januar), null))
        assertFalse(aktivitetslogg.hasWarningsOrWorse())
    }

    @Test
    fun `validering gir ikke warning hvis vi har to inntekter for samme arbeidsgiver på samme dato, men dato er 12 måneder før perioden`() {
        val element = historikkelement(inntekter = listOf(
            Infotrygdhistorikk.Inntektsopplysning(ORGNUMMER, 1.januar(2018), 1234.månedlig, true),
            Infotrygdhistorikk.Inntektsopplysning(ORGNUMMER, 1.januar(2018), 4321.månedlig, true),
        ))
        assertTrue(element.valider(aktivitetslogg, Periode(1.februar(2019), 28.februar(2019)), null))
        assertFalse(aktivitetslogg.hasWarningsOrWorse())
    }

    @Test
    fun `validering gir ikke warning hvis vi har to inntekter for samme arbeidsgiver på samme dato, men dato er før skjæringstidspunkt`() {
        val element = historikkelement(inntekter = listOf(
            Infotrygdhistorikk.Inntektsopplysning(ORGNUMMER, 1.januar, 1234.månedlig, true),
            Infotrygdhistorikk.Inntektsopplysning(ORGNUMMER, 1.januar, 4321.månedlig, true),
        ))
        assertTrue(element.valider(aktivitetslogg, Periode(2.januar, 31.januar), 2.januar))
        assertFalse(aktivitetslogg.hasWarningsOrWorse())
    }

    @Test
    fun `legger til siste inntekt først i inntektshistorikk`() {
        val inntektshistorikk = Inntektshistorikk()
        Infotrygdhistorikk.Inntektsopplysning.lagreInntekter(listOf(
            Infotrygdhistorikk.Inntektsopplysning(ORGNUMMER, 1.januar, 1234.månedlig, true),
            Infotrygdhistorikk.Inntektsopplysning(ORGNUMMER, 1.januar, 4321.månedlig, true),
        ), inntektshistorikk, UUID.randomUUID())
        assertEquals(1234.månedlig, inntektshistorikk.grunnlagForSykepengegrunnlag(1.januar))
    }

    private fun historikkelement(
        perioder: List<Infotrygdhistorikk.Infotrygdperiode> = emptyList(),
        inntekter: List<Infotrygdhistorikk.Inntektsopplysning> = emptyList(),
        arbeidskategorikoder: Map<String, LocalDate> = emptyMap(),
        hendelseId: UUID = UUID.randomUUID(),
        tidsstempel: LocalDateTime = LocalDateTime.now()
    ) =
        Infotrygdhistorikk.Element.opprett(
            tidsstempel = tidsstempel,
            hendelseId = hendelseId,
            perioder = perioder,
            inntekter = inntekter,
            arbeidskategorikoder = arbeidskategorikoder
        )

    private class Inspektør : UtbetalingsdagVisitor {
        var førsteDag: LocalDate? = null
        var sisteDag: LocalDate? = null
        var navDagTeller: Int = 0

        private fun visitDag(dag: Utbetalingstidslinje.Utbetalingsdag) {
            førsteDag = førsteDag ?: dag.dato
            sisteDag = dag.dato
        }

        override fun visit(
            dag: Utbetalingstidslinje.Utbetalingsdag.ArbeidsgiverperiodeDag,
            dato: LocalDate,
            økonomi: Økonomi
        ) {
            visitDag(dag)
        }

        override fun visit(
            dag: Utbetalingstidslinje.Utbetalingsdag.NavDag,
            dato: LocalDate,
            økonomi: Økonomi
        ) {
            navDagTeller += 1
            visitDag(dag)
        }

        override fun visit(
            dag: Utbetalingstidslinje.Utbetalingsdag.NavHelgDag,
            dato: LocalDate,
            økonomi: Økonomi
        ) {
            visitDag(dag)
        }

        override fun visit(
            dag: Utbetalingstidslinje.Utbetalingsdag.Arbeidsdag,
            dato: LocalDate,
            økonomi: Økonomi
        ) {
            visitDag(dag)
        }

        override fun visit(
            dag: Utbetalingstidslinje.Utbetalingsdag.Fridag,
            dato: LocalDate,
            økonomi: Økonomi
        ) {
            visitDag(dag)
        }

        override fun visit(
            dag: Utbetalingstidslinje.Utbetalingsdag.AvvistDag,
            dato: LocalDate,
            økonomi: Økonomi
        ) {
            visitDag(dag)
        }

        override fun visit(
            dag: Utbetalingstidslinje.Utbetalingsdag.ForeldetDag,
            dato: LocalDate,
            økonomi: Økonomi
        ) {
            visitDag(dag)
        }

        override fun visit(
            dag: Utbetalingstidslinje.Utbetalingsdag.UkjentDag,
            dato: LocalDate,
            økonomi: Økonomi
        ) {
            visitDag(dag)
        }
    }
}
