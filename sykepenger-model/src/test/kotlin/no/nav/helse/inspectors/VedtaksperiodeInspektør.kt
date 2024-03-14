package no.nav.helse.inspectors

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Avsender
import no.nav.helse.hendelser.Periode
import no.nav.helse.inspectors.VedtaksperiodeInspektør.Generasjon.Generasjontilstand
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.Generasjoner
import no.nav.helse.person.Vedtaksperiode
import no.nav.helse.person.VedtaksperiodeVisitor
import no.nav.helse.person.VilkårsgrunnlagHistorikk
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje

internal val Vedtaksperiode.inspektør get() = VedtaksperiodeInspektør(this)

internal class VedtaksperiodeInspektør(vedtaksperiode: Vedtaksperiode) : VedtaksperiodeVisitor {

    internal lateinit var id: UUID
        private set

    internal lateinit var periode: Periode
        private set

    internal lateinit var tilstand: Vedtaksperiode.Vedtaksperiodetilstand
        private set

    internal lateinit var oppdatert: LocalDateTime
        private set
    internal lateinit var skjæringstidspunkt: LocalDate
    internal lateinit var utbetalingIdTilVilkårsgrunnlagId: Pair<UUID, UUID>
    internal lateinit var utbetalingstidslinje: Utbetalingstidslinje
    internal val generasjoner = mutableListOf<Generasjon>()
    init {
        vedtaksperiode.accept(this)
    }

    data class Generasjon(
        val id: UUID,
        val endringer: List<Generasjonendring>,
        val periode: Periode,
        val tilstand: Generasjontilstand,
        val vedtakFattet: LocalDateTime?,
        val avsluttet: LocalDateTime?,
        val kilde: Generasjonkilde
    ) {
        internal enum class Generasjontilstand {
            UBEREGNET, UBEREGNET_OMGJØRING, UBEREGNET_REVURDERING, BEREGNET, BEREGNET_OMGJØRING, BEREGNET_REVURDERING, VEDTAK_FATTET, REVURDERT_VEDTAK_AVVIST, VEDTAK_IVERKSATT, AVSLUTTET_UTEN_VEDTAK, ANNULLERT_PERIODE, TIL_INFOTRYGD
        }
        data class Generasjonendring(
            val grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement?,
            val utbetaling: Utbetaling?,
            val periode: Periode,
            val dokumentsporing: Dokumentsporing
        )

        data class Generasjonkilde(
           val meldingsreferanseId: UUID,
           val innsendt: LocalDateTime,
           val registert: LocalDateTime,
           val avsender: Avsender
        )
    }

    override fun preVisitVedtaksperiode(
        vedtaksperiode: Vedtaksperiode,
        id: UUID,
        tilstand: Vedtaksperiode.Vedtaksperiodetilstand,
        opprettet: LocalDateTime,
        oppdatert: LocalDateTime,
        periode: Periode,
        opprinneligPeriode: Periode,
        skjæringstidspunkt: () -> LocalDate,
        hendelseIder: Set<Dokumentsporing>
    ) {
        this.id = id
        this.periode = periode
        this.oppdatert = oppdatert
        this.skjæringstidspunkt = skjæringstidspunkt()
        this.tilstand = tilstand
    }

    override fun preVisitGenerasjon(
        id: UUID,
        tidsstempel: LocalDateTime,
        tilstand: Generasjoner.Generasjon.Tilstand,
        periode: Periode,
        vedtakFattet: LocalDateTime?,
        avsluttet: LocalDateTime?,
        kilde: Generasjoner.Generasjonkilde
    ) {
        this.generasjoner.add(Generasjon(
            id = id,
            endringer = emptyList(),
            periode = periode,
            tilstand = when (tilstand) {
                is Generasjoner.Generasjon.Tilstand.Uberegnet -> Generasjontilstand.BEREGNET
                is Generasjoner.Generasjon.Tilstand.UberegnetOmgjøring -> Generasjontilstand.UBEREGNET_OMGJØRING
                is Generasjoner.Generasjon.Tilstand.UberegnetRevurdering -> Generasjontilstand.UBEREGNET_REVURDERING
                is Generasjoner.Generasjon.Tilstand.Beregnet -> Generasjontilstand.BEREGNET
                is Generasjoner.Generasjon.Tilstand.BeregnetOmgjøring -> Generasjontilstand.BEREGNET_OMGJØRING
                is Generasjoner.Generasjon.Tilstand.BeregnetRevurdering -> Generasjontilstand.BEREGNET_REVURDERING
                is Generasjoner.Generasjon.Tilstand.VedtakFattet -> Generasjontilstand.VEDTAK_FATTET
                is Generasjoner.Generasjon.Tilstand.VedtakIverksatt -> Generasjontilstand.VEDTAK_IVERKSATT
                is Generasjoner.Generasjon.Tilstand.RevurdertVedtakAvvist -> Generasjontilstand.REVURDERT_VEDTAK_AVVIST
                is Generasjoner.Generasjon.Tilstand.AvsluttetUtenVedtak -> Generasjontilstand.AVSLUTTET_UTEN_VEDTAK
                is Generasjoner.Generasjon.Tilstand.TilInfotrygd -> Generasjontilstand.TIL_INFOTRYGD
                is Generasjoner.Generasjon.Tilstand.AnnullertPeriode -> Generasjontilstand.ANNULLERT_PERIODE
            },
            vedtakFattet = vedtakFattet,
            avsluttet = avsluttet,
            kilde = Generasjon.Generasjonkilde(
                meldingsreferanseId = kilde.meldingsreferanseId,
                innsendt = kilde.innsendt,
                registert = kilde.registert,
                avsender = kilde.avsender
            )
        ))
    }

    override fun preVisitGenerasjonendring(
        id: UUID,
        tidsstempel: LocalDateTime,
        sykmeldingsperiode: Periode,
        periode: Periode,
        grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement?,
        utbetaling: Utbetaling?,
        dokumentsporing: Dokumentsporing,
        sykdomstidslinje: Sykdomstidslinje
    ) {
        val sisteGenerasjon = this.generasjoner.last()
        this.generasjoner[this.generasjoner.lastIndex] = sisteGenerasjon.copy(
            endringer = sisteGenerasjon.endringer.plus(Generasjon.Generasjonendring(grunnlagsdata, utbetaling, sykdomstidslinje.periode()!!, dokumentsporing))
        )
        val vilkårsgrunnlagId = grunnlagsdata?.inspektør?.vilkårsgrunnlagId ?: return
        val utbetalingId = utbetaling!!.inspektør.utbetalingId
        utbetalingIdTilVilkårsgrunnlagId = utbetalingId to vilkårsgrunnlagId
    }

    override fun preVisitGenerasjonkilde(
        meldingsreferanseId: UUID,
        innsendt: LocalDateTime,
        registrert: LocalDateTime,
        avsender: Avsender
    ) {
        val sisteGenerasjon = this.generasjoner.last()
        this.generasjoner[this.generasjoner.lastIndex] = sisteGenerasjon.copy(
            kilde = Generasjon.Generasjonkilde(meldingsreferanseId, innsendt, registrert, avsender)
        )
    }

    override fun preVisitUtbetalingstidslinje(tidslinje: Utbetalingstidslinje, gjeldendePeriode: Periode?) {
        this.utbetalingstidslinje = tidslinje
    }
}
