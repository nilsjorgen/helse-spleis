package no.nav.helse.spleis.e2e

import no.nav.helse.Toggles
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.til
import no.nav.helse.person.TilstandType
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.testhelpers.*
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

internal class OverstyrInntektTest : AbstractEndToEndTest() {

    @BeforeEach
    fun setup() {
        Toggles.RevurderTidligerePeriode.enable()
        Toggles.RevurderInntekt.enable()
    }

    @AfterEach
    fun tearDown() {
        Toggles.RevurderTidligerePeriode.pop()
        Toggles.RevurderInntekt.pop()
    }

    @Test
    fun `overstyr inntekt happy case`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)

        val tidligereInntektInnslagId = inspektør.inntektInspektør.sisteInnslag?.innslagId

        håndterOverstyring(inntekt = 32000.månedlig, skjæringstidspunkt = 1.januar, ident = "N123456")
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode)

        assertTilstander(
            0,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_SØKNAD_FERDIG_GAP,
            TilstandType.AVVENTER_UTBETALINGSGRUNNLAG,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_VILKÅRSPRØVING,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_SIMULERING,
            TilstandType.AVVENTER_GODKJENNING,
            TilstandType.TIL_UTBETALING,
            TilstandType.AVSLUTTET,
            TilstandType.AVVENTER_VILKÅRSPRØVING_REVURDERING,
            TilstandType.AVVENTER_HISTORIKK_REVURDERING,
            TilstandType.AVVENTER_SIMULERING_REVURDERING,
            TilstandType.AVVENTER_GODKJENNING_REVURDERING,
            TilstandType.TIL_UTBETALING,
            TilstandType.AVSLUTTET,
        )

        assertEquals(15741, inspektør.utbetalinger.first().arbeidsgiverOppdrag().nettoBeløp())
        assertEquals(506, inspektør.utbetalinger.last().arbeidsgiverOppdrag().nettoBeløp())

        assertEquals(2, inspektør.vilkårsgrunnlagHistorikk.size)
        assertEquals(3, inspektør.vilkårsgrunnlagHistorikk[0].second.avviksprosent?.roundToInt())

        val tidligereBeregning = inspektør.utbetalingstidslinjeberegningData.first()
        assertEquals(tidligereBeregning.inntektshistorikkInnslagId, tidligereInntektInnslagId)

        val beregning = inspektør.utbetalingstidslinjeberegningData.last()
        assertEquals(beregning.inntektshistorikkInnslagId, inspektør.inntektInspektør.sisteInnslag?.innslagId)

        assertTrue(beregning.vilkårsgrunnlagHistorikkInnslagId == person.vilkårsgrunnlagHistorikk.sisteId())

        assertTrue(inspektør.inntektInspektør.sisteInnslag?.opplysninger?.any { it.kilde == Kilde.SAKSBEHANDLER } ?: false)
    }

    @Test
    fun `overstyr inntekt flere ganger`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        håndterOverstyring(inntekt = 32000.månedlig, skjæringstidspunkt = 1.januar, ident = "N123456")

        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode)

        håndterOverstyring(inntekt = 31000.månedlig, skjæringstidspunkt = 1.januar, ident = "N123456")
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode)

        assertEquals(15741, inspektør.utbetalinger[0].arbeidsgiverOppdrag().nettoBeløp())
        assertEquals(506, inspektør.utbetalinger[1].arbeidsgiverOppdrag().nettoBeløp())
        assertEquals(-506, inspektør.utbetalinger[2].arbeidsgiverOppdrag().nettoBeløp())

        val inntektFraSaksbehandler = inspektør.inntektInspektør.sisteInnslag?.opplysninger?.filter { it.kilde == Kilde.SAKSBEHANDLER }!!
        assertEquals(1, inntektFraSaksbehandler.size)
        assertEquals(31000.månedlig, inntektFraSaksbehandler.first().sykepengegrunnlag)
    }

    @Test
    fun `overstyr inntekt ukjent skjæringstidspunkt`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        håndterOverstyring(inntekt = 32000.månedlig, skjæringstidspunkt = 2.januar, ident = "N123456")

        assertTilstander(
            0,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_SØKNAD_FERDIG_GAP,
            TilstandType.AVVENTER_UTBETALINGSGRUNNLAG,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_VILKÅRSPRØVING,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_SIMULERING,
            TilstandType.AVVENTER_GODKJENNING,
            TilstandType.TIL_UTBETALING,
            TilstandType.AVSLUTTET,
        )

        assertEquals(1, inspektør.utbetalinger.size)
    }

    @Test
    fun `overstyr inntekt tidligere skjæringstidspunkt`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        nyttVedtak(1.mars, 31.mars, 100.prosent)

        håndterOverstyring(inntekt = 32000.månedlig, skjæringstidspunkt = 1.januar, ident = "N123456")

        assertTilstander(
            0,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_SØKNAD_FERDIG_GAP,
            TilstandType.AVVENTER_UTBETALINGSGRUNNLAG,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_VILKÅRSPRØVING,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_SIMULERING,
            TilstandType.AVVENTER_GODKJENNING,
            TilstandType.TIL_UTBETALING,
            TilstandType.AVSLUTTET,
        )

        assertTilstander(
            1,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_SØKNAD_FERDIG_GAP,
            TilstandType.AVVENTER_UTBETALINGSGRUNNLAG,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_VILKÅRSPRØVING,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_SIMULERING,
            TilstandType.AVVENTER_GODKJENNING,
            TilstandType.TIL_UTBETALING,
            TilstandType.AVSLUTTET,
        )

        assertEquals(2, inspektør.utbetalinger.size)
    }

    @Test
    fun `overstyr inntekt to vedtak med kort opphold`() {
        nyttVedtak(1.januar, 26.januar, 100.prosent)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 14.februar, 100.prosent))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.februar, 14.februar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.februar)
        håndterUtbetalingsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt(2.vedtaksperiode)

        håndterOverstyring(inntekt = 32000.månedlig, skjæringstidspunkt = 1.januar, ident = "N123456")

        håndterYtelser(1.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt(2.vedtaksperiode)

        assertTilstander(
            0,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_SØKNAD_FERDIG_GAP,
            TilstandType.AVVENTER_UTBETALINGSGRUNNLAG,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_VILKÅRSPRØVING,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_SIMULERING,
            TilstandType.AVVENTER_GODKJENNING,
            TilstandType.TIL_UTBETALING,
            TilstandType.AVSLUTTET,
            TilstandType.AVVENTER_VILKÅRSPRØVING_REVURDERING,
            TilstandType.AVVENTER_HISTORIKK_REVURDERING,
            TilstandType.AVVENTER_GJENNOMFØRT_REVURDERING,
            TilstandType.AVSLUTTET,

        )

        assertTilstander(
            1,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP,
            TilstandType.AVVENTER_UTBETALINGSGRUNNLAG,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_VILKÅRSPRØVING,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_SIMULERING,
            TilstandType.AVVENTER_GODKJENNING,
            TilstandType.TIL_UTBETALING,
            TilstandType.AVSLUTTET,
            TilstandType.AVVENTER_ARBEIDSGIVERE_REVURDERING,
            TilstandType.AVVENTER_HISTORIKK_REVURDERING,
            TilstandType.AVVENTER_SIMULERING_REVURDERING,
            TilstandType.AVVENTER_GODKJENNING_REVURDERING,
            TilstandType.TIL_UTBETALING,
            TilstandType.AVSLUTTET,
        )

        assertEquals(3, inspektør.utbetalinger.filter { it.erUtbetalt() }.size)
    }

    @Test
    fun `overstyr inntekt avvik over 25 prosent reduksjon`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        håndterOverstyring(inntekt = 7000.månedlig, skjæringstidspunkt = 1.januar, ident = "N123456")

        assertTilstander(
            0,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_SØKNAD_FERDIG_GAP,
            TilstandType.AVVENTER_UTBETALINGSGRUNNLAG,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_VILKÅRSPRØVING,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_SIMULERING,
            TilstandType.AVVENTER_GODKJENNING,
            TilstandType.TIL_UTBETALING,
            TilstandType.AVSLUTTET,
            TilstandType.AVVENTER_VILKÅRSPRØVING_REVURDERING,
            TilstandType.AVVENTER_HISTORIKK_REVURDERING
        )

        assertWarningTekst(inspektør, "Har mer enn 25 % avvik")
        assertEquals(1, inspektør.utbetalinger.size)
    }

    @Test
    fun `overstyr inntekt avvik over 25 prosent økning`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        håndterOverstyring(inntekt = 70000.månedlig, skjæringstidspunkt = 1.januar, ident = "N123456")

        assertTilstander(
            0,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_SØKNAD_FERDIG_GAP,
            TilstandType.AVVENTER_UTBETALINGSGRUNNLAG,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_VILKÅRSPRØVING,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_SIMULERING,
            TilstandType.AVVENTER_GODKJENNING,
            TilstandType.TIL_UTBETALING,
            TilstandType.AVSLUTTET,
            TilstandType.AVVENTER_VILKÅRSPRØVING_REVURDERING,
            TilstandType.AVVENTER_HISTORIKK_REVURDERING
        )

        assertWarningTekst(inspektør, "Har mer enn 25 % avvik")
        assertEquals(1, inspektør.utbetalinger.size)
    }

    @Test
    fun `overstyr inntekt ny inntekt under en halv G`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        håndterOverstyring(inntekt = 3000.månedlig, skjæringstidspunkt = 1.januar, ident = "N123456")
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertTilstander(
            0,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_SØKNAD_FERDIG_GAP,
            TilstandType.AVVENTER_UTBETALINGSGRUNNLAG,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_VILKÅRSPRØVING,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_SIMULERING,
            TilstandType.AVVENTER_GODKJENNING,
            TilstandType.TIL_UTBETALING,
            TilstandType.AVSLUTTET,
            TilstandType.AVVENTER_VILKÅRSPRØVING_REVURDERING,
            TilstandType.AVVENTER_HISTORIKK_REVURDERING,
            TilstandType.AVVENTER_SIMULERING_REVURDERING,
            TilstandType.AVVENTER_GODKJENNING_REVURDERING
        )

        val utbetalingTilRevurdering = inspektør.utbetalinger.last()
        assertEquals(2, inspektør.utbetalinger.size)
        assertEquals(-15741, utbetalingTilRevurdering.arbeidsgiverOppdrag().nettoBeløp())

        assertWarningTekst(inspektør, "Har mer enn 25 % avvik", "Perioden er avslått på grunn av at inntekt er under krav til minste sykepengegrunnlag")
        assertFalse(utbetalingTilRevurdering.utbetalingstidslinje().harUtbetalinger())
    }

    @Test
    fun `skjæringstidspunkt hos infotrygd`() {
        val historikk1 = listOf(
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 29.januar(2018), 18.februar(2018), 100.prosent, 1000.daglig),
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 19.februar(2018), 18.mars(2018), 100.prosent, 1000.daglig),
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 19.mars(2018), 2.april(2018), 100.prosent, 1000.daglig),
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 3.april(2018), 14.mai(2018), 100.prosent, 1000.daglig),
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 15.mai(2018), 3.juni(2018), 100.prosent, 1000.daglig),
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 4.juni(2018), 22.juni(2018), 100.prosent, 1000.daglig),
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 18.mars(2020), 31.mars(2020), 100.prosent, 1000.daglig),
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.april(2020), 30.april(2020), 100.prosent, 1000.daglig),
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.mai(2020), 31.mai(2020), 100.prosent, 1000.daglig)
        )
        val inntektsopplysning1 = listOf(
            Inntektsopplysning(ORGNUMMER, 18.mars(2020), INNTEKT, true),
            Inntektsopplysning(ORGNUMMER, 29.januar(2018), INNTEKT, true)
        )

        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 30.juni(2020), 100.prosent))
        håndterSøknadMedValidering(1.vedtaksperiode, Søknad.Søknadsperiode.Sykdom(1.juni(2020), 30.juni(2020), 100.prosent))
        håndterUtbetalingshistorikk(1.vedtaksperiode, *historikk1.toTypedArray(), inntektshistorikk = inntektsopplysning1)
        håndterUtbetalingsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt(1.vedtaksperiode)

        forlengVedtak(1.juli(2020), 30.juli(2020))
        håndterOverstyring(inntekt = 35000.månedlig, skjæringstidspunkt = 18.mars(2020), ident = "N123456")

        assertTilstander(
            0,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP,
            TilstandType.AVVENTER_UTBETALINGSGRUNNLAG,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_SIMULERING,
            TilstandType.AVVENTER_GODKJENNING,
            TilstandType.TIL_UTBETALING,
            TilstandType.AVSLUTTET,
        )

        assertTilstander(
            1,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_FORLENGELSE,
            TilstandType.AVVENTER_UTBETALINGSGRUNNLAG,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_SIMULERING,
            TilstandType.AVVENTER_GODKJENNING,
            TilstandType.TIL_UTBETALING,
            TilstandType.AVSLUTTET,
        )
        assertErrors(inspektør)
    }
}
