package no.nav.helse.spleis.e2e

import no.nav.helse.hendelser.*
import no.nav.helse.person.TilstandType.*
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.testhelpers.*
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ForkastingTest : AbstractEndToEndTest() {

    @Test
    fun `forlengelse av infotrygd uten inntektsopplysninger`() {
        håndterSykmelding(Sykmeldingsperiode(1.februar, 23.februar, 100.prosent))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.februar, 23.februar, 100.prosent))
        håndterUtbetalingshistorikk(
            1.vedtaksperiode,
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.januar,  31.januar, 100.prosent, INNTEKT),
            inntektshistorikk = emptyList()
        )
        håndterYtelser(1.vedtaksperiode)
        assertTrue(inspektør.utbetalinger.isEmpty())
        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP,
            AVVENTER_HISTORIKK,
            TIL_INFOTRYGD
        )
    }

    @Test
    fun `når utbetaling er ikke godkjent skal påfølgende perioder også kastes ut`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100.prosent))
        håndterSøknadMedValidering(1.vedtaksperiode, Søknad.Søknadsperiode.Sykdom(3.januar, 26.januar, 100.prosent))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(3.januar, 18.januar)))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterSykmelding(Sykmeldingsperiode(29.januar, 23.februar, 100.prosent))
        håndterSøknadMedValidering(2.vedtaksperiode, Søknad.Søknadsperiode.Sykdom(29.januar, 23.februar, 100.prosent))
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, false)
        assertEquals(Utbetaling.IkkeGodkjent, inspektør.utbetalingtilstand(0))
        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_INFOTRYGD
        )
        assertForkastetPeriodeTilstander(
            2.vedtaksperiode,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            TIL_INFOTRYGD
        )
    }

    @Test
    fun `kan ikke forlenge en periode som er gått TilInfotrygd`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100.prosent))
        håndterSøknadMedValidering(1.vedtaksperiode, Søknad.Søknadsperiode.Sykdom(3.januar, 26.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(29.januar, 23.februar, 100.prosent))
        håndterSøknadMedValidering(2.vedtaksperiode, Søknad.Søknadsperiode.Sykdom(29.januar, 23.februar, 100.prosent))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(3.januar, 18.januar)))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, false) // går til TilInfotrygd

        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_INFOTRYGD
        )
        assertForkastetPeriodeTilstander(
            2.vedtaksperiode,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            TIL_INFOTRYGD
        )
    }

    @Test
    fun `Sykmelding i omvendt rekkefølge kaster ut etterfølgende som ikke er avsluttet — uten replay`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 2.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(10.januar, 20.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(3.januar, 5.januar, 100.prosent))
        assertTilstander(1.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP)
        assertTilstander(2.vedtaksperiode, START, MOTTATT_SYKMELDING_UFERDIG_GAP)
        assertTilstander(3.vedtaksperiode, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE)
    }

    @Test
    fun `søknad med papirsykmelding`() {
        håndterSykmelding(Sykmeldingsperiode(21.januar, 28.februar, 100.prosent))
        håndterSøknad(
            Søknad.Søknadsperiode.Sykdom(1.februar, 28.februar, 100.prosent),
            Søknad.Søknadsperiode.Papirsykmelding(1.januar, 20.januar)
        )
        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            TIL_INFOTRYGD
        )
    }

    @Test
    fun `refusjon opphører i perioden`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100.prosent))
        håndterInntektsmeldingMedValidering(
            1.vedtaksperiode, listOf(Periode(3.januar, 18.januar)), refusjon = Triple(
                14.januar,
                INNTEKT, emptyList()
            )
        )
        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            TIL_INFOTRYGD
        )
    }

    @Test
    fun `refusjon endres i perioden`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100.prosent))
        håndterInntektsmeldingMedValidering(
            1.vedtaksperiode, listOf(Periode(3.januar, 18.januar)), refusjon = Triple(
                null,
                INNTEKT, listOf(14.januar)
            )
        )
        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            TIL_INFOTRYGD
        )
    }

    @Test
    fun `Håndterer ny sykmelding som ligger tidligere i tid med forlengelse`() {
        håndterSykmelding(Sykmeldingsperiode(23.mars(2020), 29.mars(2020), 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(30.mars(2020), 2.april(2020), 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(10.april(2020), 20.april(2020), 100.prosent))
        assertTilstander(1.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP)
        assertTilstander(2.vedtaksperiode, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE)
        assertTilstander(3.vedtaksperiode, START, MOTTATT_SYKMELDING_UFERDIG_GAP)

        håndterSykmelding(Sykmeldingsperiode(19.mars(2020), 22.mars(2020), 100.prosent))
        assertTilstander(1.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE)
        assertTilstander(2.vedtaksperiode, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE)
        assertTilstander(3.vedtaksperiode, START, MOTTATT_SYKMELDING_UFERDIG_GAP)
        assertTilstander(4.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP)
    }

    @Test
    fun `forkaster ikke påfølgende periode når tilstøtende forkastet periode ble avsluttet`() {
        nyttVedtak(29.august, 25.september)
        håndterAnnullerUtbetaling(fagsystemId = inspektør.fagsystemId(1.vedtaksperiode))
        håndterUtbetalt(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(26.september, 23.oktober, 100.prosent))

        assertTilstander(2.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP)
    }

    @Test
    fun `forkaster ikke påfølgende periode når den forkastede ikke var avsluttet`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 21.januar, 100.prosent))
        håndterSøknadMedValidering(1.vedtaksperiode, Søknad.Søknadsperiode.Sykdom(1.januar, 21.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode, 55555.månedlig)

        håndterSykmelding(Sykmeldingsperiode(22.januar, 31.januar, 100.prosent))

        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            TIL_INFOTRYGD
        )
        assertTilstander(2.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP)
    }


    @Test
    fun `forkaster ikke i til utbetaling ved overlapp`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100.prosent))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(1.vedtaksperiode, Søknad.Søknadsperiode.Sykdom(3.januar, 26.januar, 100.prosent))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100.prosent))
        håndterUtbetalt(1.vedtaksperiode, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertEquals(Utbetaling.Utbetalt, inspektør.utbetalingtilstand(0))
        assertTilstander(
            1.vedtaksperiode, START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `forkaster i avventer godkjenning ved overlapp`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100.prosent))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(1.vedtaksperiode, Søknad.Søknadsperiode.Sykdom(3.januar, 26.januar, 100.prosent))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100.prosent))

        assertEquals(Utbetaling.Forkastet, inspektør.utbetalingtilstand(0))
        assertForkastetPeriodeTilstander(
            1.vedtaksperiode, START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_INFOTRYGD
        )
    }
}
