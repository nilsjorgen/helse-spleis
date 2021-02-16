package no.nav.helse.serde.api.builders

import no.nav.helse.hendelser.Periode
import no.nav.helse.person.*
import no.nav.helse.serde.api.HendelseDTO
import no.nav.helse.utbetalingslinjer.Utbetaling
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class VedtaksperioderState(
    private val arbeidsgiver: Arbeidsgiver,
    private val fødselsnummer: String,
    private val inntektshistorikkBuilder: InntektshistorikkBuilder
) : BuilderState() {
    private val gruppeIder = mutableMapOf<Vedtaksperiode, UUID>()
    private val perioder = mutableListOf<VedtaksperiodeState>()

    fun build(hendelser: List<HendelseDTO>, utbetalinger: List<Utbetaling>) =
        perioder.map { it.build(hendelser, utbetalinger) }

    private fun gruppeId(vedtaksperiode: Vedtaksperiode): UUID {
        val gruppeId = arbeidsgiver.finnSykeperiodeRettFør(vedtaksperiode)?.let(gruppeIder::getValue) ?: UUID.randomUUID()
        gruppeIder[vedtaksperiode] = gruppeId
        return gruppeId
    }

    override fun preVisitVedtaksperiode(
        vedtaksperiode: Vedtaksperiode,
        id: UUID,
        tilstand: Vedtaksperiode.Vedtaksperiodetilstand,
        opprettet: LocalDateTime,
        oppdatert: LocalDateTime,
        periode: Periode,
        opprinneligPeriode: Periode,
        skjæringstidspunkt: LocalDate,
        periodetype: Periodetype,
        forlengelseFraInfotrygd: ForlengelseFraInfotrygd,
        hendelseIder: List<UUID>,
        inntektsmeldingId: UUID?,
        inntektskilde: Inntektskilde
    ) {
        val sykepengegrunnlag = arbeidsgiver.grunnlagForSykepengegrunnlag(skjæringstidspunkt, periode.start)

        val vedtaksperiodeState = VedtaksperiodeState(
            vedtaksperiode = vedtaksperiode,
            id = id,
            periode = periode,
            skjæringstidspunkt = skjæringstidspunkt,
            periodetype = periodetype,
            forlengelseFraInfotrygd = forlengelseFraInfotrygd,
            tilstand = tilstand,
            inntektskilde = inntektskilde,
            sykepengegrunnlag = sykepengegrunnlag,
            gruppeId = gruppeId(vedtaksperiode),
            fødselsnummer = fødselsnummer,
            hendelseIder = hendelseIder,
            inntektsmeldingId = inntektsmeldingId,
            inntektshistorikkBuilder = inntektshistorikkBuilder
        )
        perioder.add(vedtaksperiodeState)
        pushState(vedtaksperiodeState)
    }

    override fun postVisitPerioder(vedtaksperioder: List<Vedtaksperiode>) {
        popState()
    }

    override fun postVisitForkastedePerioder(vedtaksperioder: List<ForkastetVedtaksperiode>) {
        popState()
    }
}
