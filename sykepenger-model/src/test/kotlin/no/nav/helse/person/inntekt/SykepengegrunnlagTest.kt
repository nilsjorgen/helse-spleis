package no.nav.helse.person.inntekt

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.Alder.Companion.alder
import no.nav.helse.Grunnbeløp
import no.nav.helse.april
import no.nav.helse.desember
import no.nav.helse.dsl.TestPerson
import no.nav.helse.erHelg
import no.nav.helse.etterlevelse.MaskinellJurist
import no.nav.helse.etterlevelse.SubsumsjonObserver
import no.nav.helse.etterlevelse.SubsumsjonObserver.Companion.NullObserver
import no.nav.helse.februar
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.nesteDag
import no.nav.helse.person.AbstractPersonTest.Companion.UNG_PERSON_FØDSELSDATO
import no.nav.helse.person.Opptjening
import no.nav.helse.person.Opptjening.ArbeidsgiverOpptjeningsgrunnlag.Arbeidsforhold
import no.nav.helse.person.Person
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_VV_1
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_VV_2
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_VV_8
import no.nav.helse.person.inntekt.Refusjonsopplysning.Refusjonsopplysninger
import no.nav.helse.person.inntekt.Refusjonsopplysning.Refusjonsopplysninger.RefusjonsopplysningerBuilder
import no.nav.helse.person.inntekt.Skatteopplysning.Inntekttype.LØNNSINNTEKT
import no.nav.helse.spleis.e2e.AbstractEndToEndTest.Companion.INNTEKT
import no.nav.helse.spleis.e2e.assertIngenVarsel
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.sykepengegrunnlag
import no.nav.helse.testhelpers.NAV
import no.nav.helse.testhelpers.NAVDAGER
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.testhelpers.tidslinjeOf
import no.nav.helse.utbetalingstidslinje.Begrunnelse
import no.nav.helse.yearMonth
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.properties.Delegates

internal class SykepengegrunnlagTest {
    private companion object {
        private val fødseldato67år =  1.februar(1954)
    }

    @Test
    fun equality() {
        val sykepengegrunnlag = INNTEKT.sykepengegrunnlag
        assertEquals(sykepengegrunnlag, sykepengegrunnlag)
        assertEquals(sykepengegrunnlag, INNTEKT.sykepengegrunnlag)
        assertEquals(INNTEKT.sykepengegrunnlag, INNTEKT.sykepengegrunnlag)
        assertNotEquals(INNTEKT.sykepengegrunnlag, INNTEKT.sykepengegrunnlag("annet orgnr"))
        assertNotEquals(INNTEKT.sykepengegrunnlag, INNTEKT.sykepengegrunnlag(31.desember))
    }

    @Test
    fun `minimum inntekt tom 67 år - må være 0,5 G`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.februar(2021)
        val halvG = Grunnbeløp.halvG.beløp(skjæringstidspunkt)

        var observer = MinsteinntektSubsumsjonObservatør()
        val sykepengegrunnlag = halvG.sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, observer)
        var aktivitetslogg = Aktivitetslogg()
        var validert = sykepengegrunnlag.valider(aktivitetslogg)
        assertTrue(validert)
        assertFalse(aktivitetslogg.harVarslerEllerVerre())
        assertTrue(sykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(halvG, sykepengegrunnlag.inspektør.minsteinntekt)
        assertTrue(observer.`§ 8-3 ledd 2 punktum 1`)

        observer = MinsteinntektSubsumsjonObservatør()
        aktivitetslogg = Aktivitetslogg()
        val forLiteSykepengegrunnlag = (halvG - 1.daglig).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, observer)
        validert = forLiteSykepengegrunnlag.valider(aktivitetslogg)
        assertFalse(forLiteSykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(halvG, forLiteSykepengegrunnlag.inspektør.minsteinntekt)
        assertFalse(validert)
        assertTrue(aktivitetslogg.harVarslerEllerVerre())
        assertFalse(observer.`§ 8-3 ledd 2 punktum 1`)
    }

    @Test
    fun `minimum inntekt etter 67 år - må være 2 G`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 2.februar(2021)
        val `2G` = Grunnbeløp.`2G`.beløp(skjæringstidspunkt)

        var observer = MinsteinntektSubsumsjonObservatør()
        var aktivitetslogg = Aktivitetslogg()
        val sykepengegrunnlag = (`2G`).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, observer)
        var validert = sykepengegrunnlag.valider(aktivitetslogg)
        assertTrue(sykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(`2G`, sykepengegrunnlag.inspektør.minsteinntekt)
        assertTrue(validert)
        assertFalse(aktivitetslogg.harVarslerEllerVerre())
        assertTrue(observer.`§ 8-51 ledd 2`)

        observer = MinsteinntektSubsumsjonObservatør()
        aktivitetslogg = Aktivitetslogg()
        val forLiteSykepengegrunnlag = (`2G` - 1.daglig).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, observer)
        validert = forLiteSykepengegrunnlag.valider(aktivitetslogg)
        assertFalse(forLiteSykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(`2G`, forLiteSykepengegrunnlag.inspektør.minsteinntekt)
        assertFalse(validert)
        assertTrue(aktivitetslogg.harVarslerEllerVerre())
        assertFalse(observer.`§ 8-51 ledd 2`)
    }

    @Test
    fun `kan ikke avvise dager på tidslinje som er før`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.februar(2021)
        val forLitenInntekt = Grunnbeløp.halvG.beløp(skjæringstidspunkt) - 1.daglig
        val sykepengegrunnlag = (forLitenInntekt).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, NullObserver)

        val tidslinje = tidslinjeOf(31.NAV, 28.NAV, startDato = 1.januar)
        val resultat = sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til LocalDate.MAX, skjæringstidspunkt til skjæringstidspunkt, MaskinellJurist())
        assertEquals(0, resultat.single().inspektør.avvistDagTeller)
    }

    @Test
    fun `minimum inntekt før fylte 67 år`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.januar(2021)
        val forLitenInntekt = Grunnbeløp.halvG.beløp(skjæringstidspunkt) - 1.daglig

        val sykepengegrunnlag = (forLitenInntekt).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, NullObserver)

        val tidslinje = tidslinjeOf(31.NAV, 28.NAV, startDato = skjæringstidspunkt, skjæringstidspunkter = listOf(skjæringstidspunkt))
        val resultat = sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til LocalDate.MAX, skjæringstidspunkt til skjæringstidspunkt, MaskinellJurist()).single()
        assertEquals(41, resultat.inspektør.avvistDagTeller)
        assertTrue((1.januar(2021) til 1.februar(2021))
            .filterNot { it.erHelg() }
            .all { dato ->
                resultat.inspektør.begrunnelse(dato).single() == Begrunnelse.MinimumInntekt
            }
        )
        assertTrue((2.februar(2021) til 28.februar(2021))
            .filterNot { it.erHelg() }
            .all { dato ->
                resultat.inspektør.begrunnelse(dato).single() == Begrunnelse.MinimumInntektOver67
            }
        )
    }

    @Test
    fun `minimum inntekt etter fylte 67 år`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.mars(2021)
        val `1G` = Grunnbeløp.`1G`.beløp(skjæringstidspunkt)

        val sykepengegrunnlag = (`1G`).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, NullObserver)

        val tidslinje = tidslinjeOf(20.NAVDAGER, startDato = skjæringstidspunkt, skjæringstidspunkter = listOf(skjæringstidspunkt))
        val resultat = sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til LocalDate.MAX, skjæringstidspunkt til skjæringstidspunkt, MaskinellJurist()).single()
        assertEquals(20, resultat.inspektør.avvistDagTeller)
        assertTrue(resultat.inspektør.avvistedatoer.all { dato ->
            resultat.inspektør.begrunnelse(dato).single() == Begrunnelse.MinimumInntektOver67
        })
    }

    @Test
    fun `minimum inntekt ved overgang til 67 år - var innenfor før fylte 67 år`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.januar(2021)
        val `1G` = Grunnbeløp.`1G`.beløp(skjæringstidspunkt)

        val sykepengegrunnlag = (`1G`).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, NullObserver)

        val tidslinje = tidslinjeOf(31.NAV, 28.NAVDAGER, startDato = skjæringstidspunkt, skjæringstidspunkter = listOf(skjæringstidspunkt))
        val resultat = sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til LocalDate.MAX, skjæringstidspunkt til skjæringstidspunkt, MaskinellJurist()).single()
        assertEquals(27, resultat.inspektør.avvistDagTeller)
        assertTrue(resultat.inspektør.avvistedatoer.all { dato ->
            resultat.inspektør.begrunnelse(dato).single() == Begrunnelse.MinimumInntektOver67
        })
    }

    @Test
    fun `avviser ikke dager utenfor skjæringstidspunktperioden`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.januar(2021)
        val `1G` = Grunnbeløp.`1G`.beløp(skjæringstidspunkt)

        val sykepengegrunnlag = (`1G`).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, NullObserver)

        val tidslinje = tidslinjeOf(31.NAV, 28.NAVDAGER, startDato = skjæringstidspunkt, skjæringstidspunkter = listOf(skjæringstidspunkt))
        val resultat = sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til 31.januar(2021), skjæringstidspunkt til skjæringstidspunkt, MaskinellJurist()).single()
        assertEquals(0, resultat.inspektør.avvistDagTeller)
    }

    @Test
    fun `mindre enn 2G, men skjæringstidspunkt er før virkningen av minsteinntekt`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 23.mai(2021)
        val `2G_2021` = Grunnbeløp.`2G`.beløp(skjæringstidspunkt)
        val `2G_2020` = Grunnbeløp.`2G`.beløp(30.april(2021))

        val observer = MinsteinntektSubsumsjonObservatør()
        var aktivitetslogg = Aktivitetslogg()
        val sykepengegrunnlag = (`2G_2021`).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, observer)
        var validert = sykepengegrunnlag.valider(aktivitetslogg)
        assertEquals(`2G_2020`, Grunnbeløp.`2G`.minsteinntekt(skjæringstidspunkt))
        assertTrue(sykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(`2G_2020`, sykepengegrunnlag.inspektør.minsteinntekt)
        assertTrue(validert)
        assertFalse(aktivitetslogg.harVarslerEllerVerre())
        assertTrue(observer.`§ 8-51 ledd 2`)

        aktivitetslogg = Aktivitetslogg()
        val forLiteSykepengegrunnlag = (`2G_2021` - 1.daglig).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, observer)
        validert = forLiteSykepengegrunnlag.valider(aktivitetslogg)
        assertTrue(forLiteSykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(`2G_2020`, forLiteSykepengegrunnlag.inspektør.minsteinntekt)
        assertTrue(validert)
        assertFalse(aktivitetslogg.harVarslerEllerVerre())
        assertTrue(observer.`§ 8-51 ledd 2`)
    }
    @Test
    fun `mindre enn 2G, og skjæringstidspunkt er etter virkningen av minsteinntekt`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 24.mai(2021)
        val `2G_2021` = Grunnbeløp.`2G`.beløp(skjæringstidspunkt)
        var aktivitetslogg = Aktivitetslogg()
        val sykepengegrunnlag = (`2G_2021`).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt)
        var validert = sykepengegrunnlag.valider(aktivitetslogg)
        assertEquals(`2G_2021`, Grunnbeløp.`2G`.minsteinntekt(skjæringstidspunkt))
        assertTrue(sykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(`2G_2021`, sykepengegrunnlag.inspektør.minsteinntekt)
        assertTrue(validert)
        assertFalse(aktivitetslogg.harVarslerEllerVerre())

        aktivitetslogg = Aktivitetslogg()
        val forLiteSykepengegrunnlag = (`2G_2021` - 1.daglig).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt)
        validert = forLiteSykepengegrunnlag.valider(aktivitetslogg)
        assertFalse(forLiteSykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(`2G_2021`, forLiteSykepengegrunnlag.inspektør.minsteinntekt)
        assertFalse(validert)
        assertTrue(aktivitetslogg.harVarslerEllerVerre())
    }

    @Test
    fun `begrunnelse tom 67 år - minsteinntekt oppfylt`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.februar(2021)
        val inntekt = Grunnbeløp.`2G`.beløp(skjæringstidspunkt)
        val sykepengegrunnlag = (inntekt).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt)

        val tidslinje = tidslinjeOf(31.NAV, 28.NAV, startDato = skjæringstidspunkt, skjæringstidspunkter = listOf(skjæringstidspunkt))
        val resultat = sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til LocalDate.MAX,skjæringstidspunkt til skjæringstidspunkt,  MaskinellJurist()).single()
        assertEquals(0, resultat.inspektør.avvistDagTeller)
    }

    @Test
    fun `begrunnelse tom 67 år`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.februar(2021)
        val halvG = Grunnbeløp.halvG.beløp(skjæringstidspunkt)

        val sykepengegrunnlag = (halvG - 1.daglig).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt)

        val tidslinje = tidslinjeOf(28.NAV, startDato = skjæringstidspunkt, skjæringstidspunkter = listOf(skjæringstidspunkt))
        val resultat = sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til LocalDate.MAX,skjæringstidspunkt til skjæringstidspunkt, MaskinellJurist()).single()
        assertEquals(20, resultat.inspektør.avvistDagTeller)
        assertTrue((1.februar(2021) til 1.februar(2021))
            .filterNot { it.erHelg() }
            .all { dato ->
                resultat.inspektør.begrunnelse(dato).single() == Begrunnelse.MinimumInntekt
            }
        )
        assertTrue((2.februar(2021) til 28.februar(2021))
            .filterNot { it.erHelg() }
            .all { dato ->
                resultat.inspektør.begrunnelse(dato).single() == Begrunnelse.MinimumInntektOver67
            }
        )
    }
    @Test
    fun `begrunnelse etter 67 år`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 2.februar(2021)
        val `2G` = Grunnbeløp.`2G`.beløp(skjæringstidspunkt)

        val sykepengegrunnlag = (`2G` - 1.daglig).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt)

        val tidslinje = tidslinjeOf(27.NAV, startDato = skjæringstidspunkt, skjæringstidspunkter = listOf(skjæringstidspunkt))
        val resultat = sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til LocalDate.MAX,skjæringstidspunkt til skjæringstidspunkt, MaskinellJurist()).single()
        assertEquals(19, resultat.inspektør.avvistDagTeller)
        assertTrue((2.februar(2021) til 28.februar(2021))
            .filterNot { it.erHelg() }
            .all { dato ->
                resultat.inspektør.begrunnelse(dato).single() == Begrunnelse.MinimumInntektOver67
            }
        )
    }

    @Test
    fun `justerer grunnbeløpet`() {
        val sykepengegrunnlag = 60000.månedlig.sykepengegrunnlag("orgnr", 1.mai(2020), 1.mai(2020))
        val justert = sykepengegrunnlag.grunnbeløpsregulering()
        assertNotEquals(sykepengegrunnlag, justert)
        assertNotEquals(sykepengegrunnlag.inspektør.sykepengegrunnlag, justert.inspektør.sykepengegrunnlag)
        assertNotEquals(sykepengegrunnlag.inspektør.`6G`, justert.inspektør.`6G`)
        assertTrue(sykepengegrunnlag.inspektør.`6G` < justert.inspektør.`6G`)
        assertTrue(sykepengegrunnlag.inspektør.sykepengegrunnlag < justert.inspektør.sykepengegrunnlag)
    }

    @Test
    fun `sykepengegrunnlaget skal ikke rundes av - 6g-begrenset`() {
        val `6G` = Grunnbeløp.`6G`.beløp(1.januar)
        val sykepengegrunnlag = `6G`.sykepengegrunnlag
        assertEquals(`6G`, sykepengegrunnlag.inspektør.sykepengegrunnlag)
    }
    @Test
    fun `sykepengegrunnlaget skal ikke rundes av - under 6`() {
        val daglig = 255.5.daglig
        val sykepengegrunnlag = daglig.sykepengegrunnlag
        assertEquals(daglig, sykepengegrunnlag.inspektør.sykepengegrunnlag)
    }

    @Test
    fun `tilkommen inntekt`() {
        val skjæringstidspunkt = 1.januar
        val a1Fom = skjæringstidspunkt
        val a2Fom = skjæringstidspunkt.plusDays(1)
        val a1Inntekt = 25000.månedlig
        val a2Inntekt = 5000.månedlig
        val inntekter = listOf(
            ArbeidsgiverInntektsopplysning("a1", a1Fom til LocalDate.MAX, Inntektsmelding(a1Fom, UUID.randomUUID(), a1Inntekt, LocalDateTime.now()), RefusjonsopplysningerBuilder().apply {
                leggTil(Refusjonsopplysning(UUID.randomUUID(), a1Fom, null, a1Inntekt), LocalDateTime.now())
            }.build()),
            ArbeidsgiverInntektsopplysning("a2", a2Fom til LocalDate.MAX, Inntektsmelding(a2Fom, UUID.randomUUID(), a2Inntekt, LocalDateTime.now()), RefusjonsopplysningerBuilder().apply {
                leggTil(Refusjonsopplysning(UUID.randomUUID(), a2Fom, null, INGEN), LocalDateTime.now())
            }.build()),
        )

        val sykepengegrunnlag = Sykepengegrunnlag(
            person = TestPerson(object:PersonObserver{}).person,
            alder = UNG_PERSON_FØDSELSDATO.alder,
            arbeidsgiverInntektsopplysninger = inntekter,
            skjæringstidspunkt = skjæringstidspunkt,
            sammenligningsgrunnlag = Sammenligningsgrunnlag(emptyList()),
            subsumsjonObserver = NullObserver
        )
        assertEquals(a1Inntekt, sykepengegrunnlag.inspektør.sykepengegrunnlag)
        assertEquals(a1Inntekt, sykepengegrunnlag.inspektør.beregningsgrunnlag)
    }

    @Test
    fun `overstyre inntekt og refusjon - endre til samme`() {
        val opprinnelig = listOf(
            ArbeidsgiverInntektsopplysning("a1", 1.januar til LocalDate.MAX, Inntektsmelding(1.januar, UUID.randomUUID(), 25000.månedlig, LocalDateTime.now()), RefusjonsopplysningerBuilder().apply {
                leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 25000.månedlig), LocalDateTime.now())
            }.build()),
            ArbeidsgiverInntektsopplysning("a2", 1.januar til LocalDate.MAX, Inntektsmelding(1.januar, UUID.randomUUID(), 5000.månedlig, LocalDateTime.now()), RefusjonsopplysningerBuilder().apply {
                leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, INGEN), LocalDateTime.now())
            }.build()),
        )
        val overstyring = Sykepengegrunnlag.ArbeidsgiverInntektsopplysningerOverstyringer(opprinnelig, null, NullObserver)
        val endretOpplysning = ArbeidsgiverInntektsopplysning("a1", 1.januar til LocalDate.MAX, Saksbehandler(1.januar, UUID.randomUUID(), 25000.månedlig, "", null, LocalDateTime.now()), RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 25000.månedlig), LocalDateTime.now())
        }.build())
        overstyring.leggTilInntekt(endretOpplysning)

        assertNull(overstyring.resultat())
    }
    @Test
    fun `overstyre inntekt og refusjon - endrer kun refusjon`() {
        val a1Inntektsopplysning = Inntektsmelding(1.januar, UUID.randomUUID(), 25000.månedlig, LocalDateTime.now())
        val a2Inntektsopplysning = Inntektsmelding(1.januar, UUID.randomUUID(), 5000.månedlig, LocalDateTime.now())
        val a2Refusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, INGEN), LocalDateTime.now())
        }.build()
        val a1Refusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 25000.månedlig), LocalDateTime.now())
        }.build()
        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", 1.januar til LocalDate.MAX, a1Inntektsopplysning, a1Refusjonsopplysninger)
        val a2Opplysning = ArbeidsgiverInntektsopplysning("a2", 1.januar til LocalDate.MAX, a2Inntektsopplysning, a2Refusjonsopplysninger)
        val opprinnelig = listOf(a1Opplysning, a2Opplysning)

        val overstyring = Sykepengegrunnlag.ArbeidsgiverInntektsopplysningerOverstyringer(opprinnelig, null, NullObserver)
        val a1EndretRefusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 2000.månedlig), LocalDateTime.now())
        }.build()
        val endretOpplysning = ArbeidsgiverInntektsopplysning("a1", 1.januar til LocalDate.MAX, Saksbehandler(1.januar, UUID.randomUUID(), 25000.månedlig, "", null, LocalDateTime.now()), a1EndretRefusjonsopplysninger)
        overstyring.leggTilInntekt(endretOpplysning)

        val forventetOpplysning = ArbeidsgiverInntektsopplysning("a1", 1.januar til LocalDate.MAX, a1Inntektsopplysning, a1EndretRefusjonsopplysninger)
        assertEquals(listOf(forventetOpplysning, a2Opplysning), overstyring.resultat())
    }
    @Test
    fun `overstyre inntekt og refusjon - endrer kun inntekt`() {
        val a1Inntektsopplysning = Inntektsmelding(1.januar, UUID.randomUUID(), 25000.månedlig, LocalDateTime.now())
        val a2Inntektsopplysning = Inntektsmelding(1.januar, UUID.randomUUID(), 5000.månedlig, LocalDateTime.now())
        val a2Refusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, INGEN), LocalDateTime.now())
        }.build()
        val a1Refusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 25000.månedlig), LocalDateTime.now())
        }.build()
        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", 1.januar til LocalDate.MAX, a1Inntektsopplysning, a1Refusjonsopplysninger)
        val a2Opplysning = ArbeidsgiverInntektsopplysning("a2", 1.januar til LocalDate.MAX, a2Inntektsopplysning, a2Refusjonsopplysninger)
        val opprinnelig = listOf(a1Opplysning, a2Opplysning)

        val overstyring = Sykepengegrunnlag.ArbeidsgiverInntektsopplysningerOverstyringer(opprinnelig, null, NullObserver)

        val a1EndretInntektsopplysning = Saksbehandler(1.januar, UUID.randomUUID(), 20000.månedlig, "", null, LocalDateTime.now())
        val a1EndretRefusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 25000.månedlig), LocalDateTime.now())
        }.build()

        val endretOpplysning = ArbeidsgiverInntektsopplysning("a1", 1.januar til LocalDate.MAX, a1EndretInntektsopplysning, a1EndretRefusjonsopplysninger)
        overstyring.leggTilInntekt(endretOpplysning)

        val forventetOpplysning = ArbeidsgiverInntektsopplysning("a1", 1.januar til LocalDate.MAX, a1EndretInntektsopplysning, a1Refusjonsopplysninger)
        assertEquals(listOf(forventetOpplysning, a2Opplysning), overstyring.resultat())
    }
    @Test
    fun `overstyre inntekt og refusjon - forsøker å endre Infotrygd-inntekt`() {
        val a1Inntektsopplysning = Infotrygd(UUID.randomUUID(), 1.januar, UUID.randomUUID(), 5000.månedlig, LocalDateTime.now())
        val a2Inntektsopplysning = Inntektsmelding(UUID.randomUUID(), 1.januar, UUID.randomUUID(), 5000.månedlig, LocalDateTime.now())
        val a2Refusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, INGEN), LocalDateTime.now())
        }.build()
        val a1Refusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 25000.månedlig), LocalDateTime.now())
        }.build()
        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", 1.januar til LocalDate.MAX, a1Inntektsopplysning, a1Refusjonsopplysninger)
        val a2Opplysning = ArbeidsgiverInntektsopplysning("a2", 1.januar til LocalDate.MAX, a2Inntektsopplysning, a2Refusjonsopplysninger)
        val opprinnelig = listOf(a1Opplysning, a2Opplysning)

        val overstyring = Sykepengegrunnlag.ArbeidsgiverInntektsopplysningerOverstyringer(opprinnelig, null, NullObserver)

        val a1EndretInntektsopplysning = Saksbehandler(1.januar, UUID.randomUUID(), 20000.månedlig, "", null, LocalDateTime.now())
        val a1EndretRefusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 25000.månedlig), LocalDateTime.now())
        }.build()

        val endretOpplysning = ArbeidsgiverInntektsopplysning("a1", 1.januar til LocalDate.MAX, a1EndretInntektsopplysning, a1EndretRefusjonsopplysninger)
        overstyring.leggTilInntekt(endretOpplysning)

        assertNull(overstyring.resultat())
    }
    @Test
    fun `overstyre inntekt og refusjon - forsøker å legge til ny arbeidsgiver`() {
        val a1Inntektsopplysning = Inntektsmelding(1.januar, UUID.randomUUID(), 25000.månedlig, LocalDateTime.now())
        val a2Inntektsopplysning = Inntektsmelding(1.januar, UUID.randomUUID(), 5000.månedlig, LocalDateTime.now())
        val a2Refusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, INGEN), LocalDateTime.now())
        }.build()
        val a1Refusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 25000.månedlig), LocalDateTime.now())
        }.build()
        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", 1.januar til LocalDate.MAX, a1Inntektsopplysning, a1Refusjonsopplysninger)
        val a2Opplysning = ArbeidsgiverInntektsopplysning("a2", 1.januar til LocalDate.MAX, a2Inntektsopplysning, a2Refusjonsopplysninger)
        val opprinnelig = listOf(a1Opplysning, a2Opplysning)

        val overstyring = Sykepengegrunnlag.ArbeidsgiverInntektsopplysningerOverstyringer(opprinnelig, null, NullObserver)

        val a3EndretInntektsopplysning = Saksbehandler(1.januar, UUID.randomUUID(), 20000.månedlig, "", null, LocalDateTime.now())
        val a3EndretRefusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 25000.månedlig), LocalDateTime.now())
        }.build()

        val a1EndretInntektsopplysning = Saksbehandler(1.januar, UUID.randomUUID(), 20000.månedlig, "", null, LocalDateTime.now())
        val a1EndretRefusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 25000.månedlig), LocalDateTime.now())
        }.build()

        val endretOpplysningA3 = ArbeidsgiverInntektsopplysning("a3", 1.januar til LocalDate.MAX, a3EndretInntektsopplysning, a3EndretRefusjonsopplysninger)
        overstyring.leggTilInntekt(endretOpplysningA3)

        val endretOpplysningA1 = ArbeidsgiverInntektsopplysning("a1", 1.januar til LocalDate.MAX, a1EndretInntektsopplysning, a1EndretRefusjonsopplysninger)
        overstyring.leggTilInntekt(endretOpplysningA1)


        val resultat = overstyring.resultat()
        assertNotNull(resultat)
        assertEquals(2, resultat.size)
        assertTrue(resultat.none{ it.inspektør.orgnummer == "a3" })
    }

    @Test
    fun `lager varsel dersom arbeidsforhold har opphørt`() {
        val a1 = "a1"
        val a2 = "a2"
        val skjæringstidspunkt = 1.mars
        val sluttdatoA1 = skjæringstidspunkt.minusMonths(1).withDayOfMonth(1)
        val startdatoA2 = skjæringstidspunkt.minusMonths(1).withDayOfMonth(2)

        val sykepengegrunnlag = Sykepengegrunnlag.ferdigSykepengegrunnlag(
            alder = UNG_PERSON_FØDSELSDATO.alder,
            skjæringstidspunkt = skjæringstidspunkt,
            arbeidsgiverInntektsopplysninger = listOf(
                ArbeidsgiverInntektsopplysning(
                    orgnummer = a1,
                    gjelder = skjæringstidspunkt til LocalDate.MAX,
                    inntektsopplysning = Inntektsmelding(
                        dato = skjæringstidspunkt,
                        hendelseId = UUID.randomUUID(),
                        beløp = 25000.månedlig,
                        tidsstempel = LocalDateTime.now()
                    ),
                    refusjonsopplysninger = Refusjonsopplysninger()
                ),
                ArbeidsgiverInntektsopplysning(
                    orgnummer = a2,
                    gjelder = skjæringstidspunkt til LocalDate.MAX,
                    inntektsopplysning = SkattSykepengegrunnlag(
                        hendelseId = UUID.randomUUID(),
                        dato = skjæringstidspunkt,
                        inntektsopplysninger = listOf(
                            Skatteopplysning(
                                hendelseId = UUID.randomUUID(),
                                beløp = 25000.månedlig,
                                måned = 1.januar.yearMonth,
                                type = LØNNSINNTEKT,
                                fordel = "",
                                beskrivelse = "",
                                tidsstempel = LocalDateTime.now()
                            )
                        ),
                        ansattPerioder = emptyList()
                    ),
                    refusjonsopplysninger = Refusjonsopplysninger()
                )
            ),
            sammenligningsgrunnlag = Sammenligningsgrunnlag(emptyList()),
            deaktiverteArbeidsforhold = emptyList(),
            vurdertInfotrygd = false,
            tilstand = Sykepengegrunnlag.FastsattEtterHovedregel
        )

        val opptjening = Opptjening.nyOpptjening(listOf(
            Opptjening.ArbeidsgiverOpptjeningsgrunnlag(a1, listOf(
                Arbeidsforhold(
                    ansattFom = LocalDate.EPOCH,
                    ansattTom = sluttdatoA1,
                    deaktivert = false
                )
            )),
            Opptjening.ArbeidsgiverOpptjeningsgrunnlag(a2, listOf(
                Arbeidsforhold(
                    ansattFom = startdatoA2,
                    ansattTom = null,
                    deaktivert = false
                )
            ))
        ), skjæringstidspunkt, NullObserver)

        Aktivitetslogg().also { aktivitetslogg ->
            sykepengegrunnlag.sjekkForNyArbeidsgiver(aktivitetslogg, opptjening, a1)
            aktivitetslogg.assertVarsel(RV_VV_8)
        }

        Aktivitetslogg().also { aktivitetslogg ->
            sykepengegrunnlag.sjekkForNyArbeidsgiver(aktivitetslogg, opptjening, a2)
            aktivitetslogg.assertIngenVarsel(RV_VV_8)
        }
    }

    @Test
    fun `lager varsel ved flere arbeidsgivere med ghost`() {
        val a1 = "a1"
        val a2 = "a2"
        val skjæringstidspunkt = 1.mars
        val førsteFraværsdagAG1 = skjæringstidspunkt
        val sykepengegrunnlag = Sykepengegrunnlag.ferdigSykepengegrunnlag(
            alder = UNG_PERSON_FØDSELSDATO.alder,
            skjæringstidspunkt = skjæringstidspunkt,
            arbeidsgiverInntektsopplysninger = listOf(
                ArbeidsgiverInntektsopplysning(
                    orgnummer = a1,
                    gjelder = skjæringstidspunkt til LocalDate.MAX,
                    inntektsopplysning = Inntektsmelding(
                        dato = førsteFraværsdagAG1,
                        hendelseId = UUID.randomUUID(),
                        beløp = 25000.månedlig,
                        tidsstempel = LocalDateTime.now()
                    ),
                    refusjonsopplysninger = Refusjonsopplysninger()
                ),
                ArbeidsgiverInntektsopplysning(
                    orgnummer = a2,
                    gjelder = skjæringstidspunkt til LocalDate.MAX,
                    inntektsopplysning = SkattSykepengegrunnlag(
                        hendelseId = UUID.randomUUID(),
                        dato = skjæringstidspunkt,
                        inntektsopplysninger = listOf(
                            Skatteopplysning(
                                hendelseId = UUID.randomUUID(),
                                beløp = 25000.månedlig,
                                måned = 1.januar.yearMonth,
                                type = LØNNSINNTEKT,
                                fordel = "",
                                beskrivelse = "",
                                tidsstempel = LocalDateTime.now()
                            )
                        ),
                        ansattPerioder = emptyList()
                    ),
                    refusjonsopplysninger = Refusjonsopplysninger()
                )
            ),
            deaktiverteArbeidsforhold = emptyList(),
            sammenligningsgrunnlag = Sammenligningsgrunnlag(emptyList()),
            vurdertInfotrygd = false,
            tilstand = Sykepengegrunnlag.FastsattEtterHovedregel
        )


        Aktivitetslogg().also { aktivitetslogg ->
            sykepengegrunnlag.markerFlereArbeidsgivere(aktivitetslogg)
            aktivitetslogg.assertVarsel(RV_VV_2)
        }
    }

    @Test
    fun `lager varsel ved flere arbeidsgivere med ulik startdato`() {
        val a1 = "a1"
        val a2 = "a2"
        val skjæringstidspunkt = 1.mars
        val førsteFraværsdagAG1 = skjæringstidspunkt
        val førsteFraværsdagAG2 = skjæringstidspunkt.nesteDag
        val sykepengegrunnlag = Sykepengegrunnlag.ferdigSykepengegrunnlag(
            alder = UNG_PERSON_FØDSELSDATO.alder,
            skjæringstidspunkt = skjæringstidspunkt,
            arbeidsgiverInntektsopplysninger = listOf(
                ArbeidsgiverInntektsopplysning(
                    orgnummer = a1,
                    gjelder = skjæringstidspunkt til LocalDate.MAX,
                    inntektsopplysning = Inntektsmelding(
                        dato = førsteFraværsdagAG1,
                        hendelseId = UUID.randomUUID(),
                        beløp = 25000.månedlig,
                        tidsstempel = LocalDateTime.now()
                    ),
                    refusjonsopplysninger = Refusjonsopplysninger()
                ),
                ArbeidsgiverInntektsopplysning(
                    orgnummer = a2,
                    gjelder = skjæringstidspunkt til LocalDate.MAX,
                    inntektsopplysning = Inntektsmelding(
                        dato = førsteFraværsdagAG2,
                        hendelseId = UUID.randomUUID(),
                        beløp = 25000.månedlig,
                        tidsstempel = LocalDateTime.now()
                    ),
                    refusjonsopplysninger = Refusjonsopplysninger()
                )
            ),
            deaktiverteArbeidsforhold = emptyList(),
            sammenligningsgrunnlag = Sammenligningsgrunnlag(emptyList()),
            vurdertInfotrygd = false,
            tilstand = Sykepengegrunnlag.FastsattEtterHovedregel
        )


        Aktivitetslogg().also { aktivitetslogg ->
            sykepengegrunnlag.markerFlereArbeidsgivere(aktivitetslogg)
            aktivitetslogg.assertVarsel(RV_VV_2)
        }
    }

    @Test
    fun `lager varsel dersom en arbeidsgiver i sykepengegrunnlaget ikke har registrert opptjening`() {
        val a1 = "a1"
        val a2 = "a2"
        val skjæringstidspunkt = 1.mars
        val sykepengegrunnlag = Sykepengegrunnlag.ferdigSykepengegrunnlag(
            alder = UNG_PERSON_FØDSELSDATO.alder,
            skjæringstidspunkt = skjæringstidspunkt,
            arbeidsgiverInntektsopplysninger = listOf(
                ArbeidsgiverInntektsopplysning(
                    orgnummer = a1,
                    gjelder = skjæringstidspunkt til LocalDate.MAX,
                    inntektsopplysning = Inntektsmelding(
                        dato = skjæringstidspunkt,
                        hendelseId = UUID.randomUUID(),
                        beløp = 25000.månedlig,
                        tidsstempel = LocalDateTime.now()
                    ),
                    refusjonsopplysninger = Refusjonsopplysninger()
                ),
                ArbeidsgiverInntektsopplysning(
                    orgnummer = a2,
                    gjelder = skjæringstidspunkt til LocalDate.MAX,
                    inntektsopplysning = Inntektsmelding(
                        dato = skjæringstidspunkt,
                        hendelseId = UUID.randomUUID(),
                        beløp = 25000.månedlig,
                        tidsstempel = LocalDateTime.now()
                    ),
                    refusjonsopplysninger = Refusjonsopplysninger()
                )
            ),
            deaktiverteArbeidsforhold = emptyList(),
            sammenligningsgrunnlag = Sammenligningsgrunnlag(emptyList()),
            vurdertInfotrygd = false,
            tilstand = Sykepengegrunnlag.FastsattEtterHovedregel
        )

        val opptjeningUtenA2 = Opptjening.nyOpptjening(listOf(
            Opptjening.ArbeidsgiverOpptjeningsgrunnlag(a1, listOf(
                Arbeidsforhold(
                    ansattFom = LocalDate.EPOCH,
                    ansattTom = null,
                    deaktivert = false
                )
            ))
        ), skjæringstidspunkt, NullObserver)
        val opptjeningMedA2 = Opptjening.nyOpptjening(listOf(
            Opptjening.ArbeidsgiverOpptjeningsgrunnlag(a1, listOf(
                Arbeidsforhold(
                    ansattFom = LocalDate.EPOCH,
                    ansattTom = null,
                    deaktivert = false
                )
            )),
            Opptjening.ArbeidsgiverOpptjeningsgrunnlag(a2, listOf(
                Arbeidsforhold(
                    ansattFom = LocalDate.EPOCH,
                    ansattTom = null,
                    deaktivert = false
                )
            ))
        ), skjæringstidspunkt, NullObserver)

        Aktivitetslogg().also { aktivitetslogg ->
            sykepengegrunnlag.måHaRegistrertOpptjeningForArbeidsgivere(aktivitetslogg, opptjeningUtenA2)
            aktivitetslogg.assertVarsel(RV_VV_1)
        }

        Aktivitetslogg().also { aktivitetslogg ->
            sykepengegrunnlag.måHaRegistrertOpptjeningForArbeidsgivere(aktivitetslogg, opptjeningMedA2)
            aktivitetslogg.assertIngenVarsel(RV_VV_1)
        }
    }

    @Test
    fun `ikke varsel ved flere arbeidsgivere med samme startdato`() {
        val a1 = "a1"
        val a2 = "a2"
        val skjæringstidspunkt = 1.mars
        val førsteFraværsdagAG1 = skjæringstidspunkt
        val førsteFraværsdagAG2 = skjæringstidspunkt
        val sykepengegrunnlag = Sykepengegrunnlag.ferdigSykepengegrunnlag(
            alder = UNG_PERSON_FØDSELSDATO.alder,
            skjæringstidspunkt = skjæringstidspunkt,
            arbeidsgiverInntektsopplysninger = listOf(
                ArbeidsgiverInntektsopplysning(
                    orgnummer = a1,
                    gjelder = skjæringstidspunkt til LocalDate.MAX,
                    inntektsopplysning = Inntektsmelding(
                        dato = førsteFraværsdagAG1,
                        hendelseId = UUID.randomUUID(),
                        beløp = 25000.månedlig,
                        tidsstempel = LocalDateTime.now()
                    ),
                    refusjonsopplysninger = Refusjonsopplysninger()
                ),
                ArbeidsgiverInntektsopplysning(
                    orgnummer = a2,
                    gjelder = skjæringstidspunkt til LocalDate.MAX,
                    inntektsopplysning = Inntektsmelding(
                        dato = førsteFraværsdagAG2,
                        hendelseId = UUID.randomUUID(),
                        beløp = 25000.månedlig,
                        tidsstempel = LocalDateTime.now()
                    ),
                    refusjonsopplysninger = Refusjonsopplysninger()
                )
            ),
            deaktiverteArbeidsforhold = emptyList(),
            sammenligningsgrunnlag = Sammenligningsgrunnlag(emptyList()),
            vurdertInfotrygd = false,
            tilstand = Sykepengegrunnlag.FastsattEtterHovedregel
        )


        Aktivitetslogg().also { aktivitetslogg ->
            sykepengegrunnlag.markerFlereArbeidsgivere(aktivitetslogg)
            aktivitetslogg.assertIngenVarsel(RV_VV_2)
        }
    }

    @Test
    fun equals() {
        val inntektID = UUID.randomUUID()
        val hendelseId = UUID.randomUUID()
        val tidsstempel = LocalDateTime.now()
        val sykepengegrunnlag1 = Sykepengegrunnlag.ferdigSykepengegrunnlag(
            alder = UNG_PERSON_FØDSELSDATO.alder,
            skjæringstidspunkt = 1.januar,
            arbeidsgiverInntektsopplysninger = listOf(
                ArbeidsgiverInntektsopplysning(
                    orgnummer = "orgnummer",
                    gjelder = 1.januar til LocalDate.MAX,
                    inntektsopplysning = Infotrygd(
                        id = inntektID,
                        dato = 1.januar,
                        hendelseId = hendelseId,
                        beløp = 25000.månedlig,
                        tidsstempel = tidsstempel
                    ),
                    refusjonsopplysninger = Refusjonsopplysninger()
                )
            ),
            deaktiverteArbeidsforhold = emptyList(),
            sammenligningsgrunnlag = Sammenligningsgrunnlag(emptyList()),
            vurdertInfotrygd = false,
            tilstand = Sykepengegrunnlag.FastsattEtterHovedregel
        )

        assertEquals(sykepengegrunnlag1, sykepengegrunnlag1.grunnbeløpsregulering()) { "grunnbeløpet trenger ikke justering" }
        assertNotEquals(
            sykepengegrunnlag1,
            Sykepengegrunnlag.ferdigSykepengegrunnlag(
                alder = UNG_PERSON_FØDSELSDATO.alder,
                skjæringstidspunkt = 1.januar,
                arbeidsgiverInntektsopplysninger = listOf(
                    ArbeidsgiverInntektsopplysning(
                        orgnummer = "orgnummer",
                        gjelder = 1.januar til LocalDate.MAX,
                        inntektsopplysning = Infotrygd(
                            id = inntektID,
                            dato = 1.januar,
                            hendelseId = hendelseId,
                            beløp = 25000.månedlig,
                            tidsstempel = tidsstempel
                        ),
                        refusjonsopplysninger = Refusjonsopplysninger()
                    )
                ),
                sammenligningsgrunnlag = Sammenligningsgrunnlag(emptyList()),
                deaktiverteArbeidsforhold = listOf(
                    ArbeidsgiverInntektsopplysning(
                        orgnummer = "orgnummer",
                        gjelder = 1.januar til LocalDate.MAX,
                        inntektsopplysning = Infotrygd(
                            id = inntektID,
                            dato = 1.januar,
                            hendelseId = hendelseId,
                            beløp = 25000.månedlig,
                            tidsstempel = tidsstempel
                        ),
                        refusjonsopplysninger = Refusjonsopplysninger()
                    )
                ),
                vurdertInfotrygd = false,
                tilstand = Sykepengegrunnlag.FastsattEtterHovedregel
            )
        )
    }

    private class MinsteinntektSubsumsjonObservatør : SubsumsjonObserver {
        var `§ 8-3 ledd 2 punktum 1` by Delegates.notNull<Boolean>()
        var `§ 8-51 ledd 2` by Delegates.notNull<Boolean>()

        override fun `§ 8-3 ledd 2 punktum 1`(
            oppfylt: Boolean,
            skjæringstidspunkt: LocalDate,
            beregningsgrunnlagÅrlig: Double,
            minimumInntektÅrlig: Double
        ) {
            this.`§ 8-3 ledd 2 punktum 1` = oppfylt
        }

        override fun `§ 8-51 ledd 2`(
            oppfylt: Boolean,
            skjæringstidspunkt: LocalDate,
            alderPåSkjæringstidspunkt: Int,
            beregningsgrunnlagÅrlig: Double,
            minimumInntektÅrlig: Double
        ) {
            this.`§ 8-51 ledd 2` = oppfylt
        }
    }
}