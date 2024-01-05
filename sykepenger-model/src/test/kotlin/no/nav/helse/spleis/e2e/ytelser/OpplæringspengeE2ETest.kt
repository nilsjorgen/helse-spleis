package no.nav.helse.spleis.e2e.ytelser

import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertIngenFunksjonelleFeil
import no.nav.helse.spleis.e2e.assertIngenVarsler
import no.nav.helse.spleis.e2e.assertTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Test

internal class OpplæringspengeE2ETest : AbstractEndToEndTest() {

    @Test
    fun `Opplæringspenger starter mindre enn 4 uker før sykefraværstilfellet`() {
        håndterSykmelding(Sykmeldingsperiode(3.mars, 19.mars))
        håndterSøknad(Sykdom(3.mars, 19.mars, 100.prosent))
        håndterInntektsmelding(listOf(3.mars til 18.mars),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, opplæringspenger = listOf(3.februar til 20.februar))
        assertVarsel(Varselkode.RV_AY_8)
        assertTilstand(1.vedtaksperiode, AVVENTER_SIMULERING)
    }

    @Test
    fun `Opplæringspenger starter mer enn 4 uker før sykefraværstilfellet`() {
        håndterSykmelding(Sykmeldingsperiode(3.mars, 19.mars))
        håndterSøknad(Sykdom(3.mars, 19.mars, 100.prosent))
        håndterInntektsmelding(listOf(3.mars til 18.mars),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, opplæringspenger = listOf(3.januar til 20.januar))
        assertIngenFunksjonelleFeil()
        assertIngenVarsler()
    }

    @Test
    fun `Periode for person der det ikke foreligger opplæringspengerytelse blir behandlet og sendt til godkjenning`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode, opplæringspenger = emptyList())
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(Oppdragstatus.AKSEPTERT)

        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `Periode som overlapper med opplæringspengerytelse får varsel`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, opplæringspenger = listOf(1.januar til 31.januar))
        assertVarsel(Varselkode.RV_AY_8)
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)
    }

    @Test
    fun `Periode som overlapper med opplæringspengerytelse i starten av perioden får varsel`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, opplæringspenger = listOf(1.desember(2017) til 1.januar))
        assertVarsel(Varselkode.RV_AY_8)
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)
    }

    @Test
    fun `Periode som overlapper med opplæringspengerytelse i slutten av perioden får varsel`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, opplæringspenger = listOf(31.januar til 14.februar))
        assertVarsel(Varselkode.RV_AY_8)
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)
    }

}
