package no.nav.helse.spleis.e2e.flere_arbeidsgivere

import no.nav.helse.februar
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.Varselkode.RV_SV_2
import no.nav.helse.person.inntekt.Inntektsmelding
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertFunksjonellFeil
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.nyPeriode
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.testhelpers.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FlereUkjenteArbeidsgivereTest : AbstractEndToEndTest() {

    @Test
    fun `én arbeidsgiver blir to - søknad for ag1 først`() {
        nyttVedtak(1.januar, 31.januar, orgnummer = a1)
        nyPeriode(1.februar til 20.februar, a1)
        nyPeriode(1.februar til 20.februar, a2)
        håndterInntektsmelding(listOf(1.februar til 16.februar), orgnummer = a2)
        assertFunksjonellFeil("Minst en arbeidsgiver inngår ikke i sykepengegrunnlaget", 2.vedtaksperiode.filter(a1))
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertTrue(inspektør(a1).periodeErForkastet(2.vedtaksperiode))
        assertTrue(inspektør(a2).periodeErForkastet(1.vedtaksperiode))
    }

    @Test
    fun `én arbeidsgiver blir to - forlenges kun av ny ag`() {
        nyttVedtak(1.januar, 31.januar, orgnummer = a1)
        nyPeriode(1.februar til 20.februar, a2)
        håndterInntektsmelding(listOf(1.februar til 16.februar), orgnummer = a2)
        val vilkårsgrunnlag = inspektør.vilkårsgrunnlag(1.vedtaksperiode)
        assertNotNull(vilkårsgrunnlag)
        val sykepengegrunnlagInspektør = vilkårsgrunnlag.inspektør.sykepengegrunnlag.inspektør
        assertEquals(1, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)

        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
            assertEquals(INNTEKT, it.inntektsopplysning.omregnetÅrsinntekt())
            assertEquals(Inntektsmelding::class, it.inntektsopplysning::class)
        }
        assertNull(sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver[a2])


        assertFunksjonellFeil(RV_SV_2, 1.vedtaksperiode.filter(a2))
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertTrue(inspektør(a2).periodeErForkastet(1.vedtaksperiode))
    }
}