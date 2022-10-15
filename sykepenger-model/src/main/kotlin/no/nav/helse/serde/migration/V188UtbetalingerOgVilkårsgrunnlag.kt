package no.nav.helse.serde.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.til
import org.slf4j.LoggerFactory
import org.slf4j.MDC

private typealias InnslagId = UUID
private typealias VilkårsgrunnlagId = UUID
private typealias BeregningId = UUID
private typealias UtbetalingId = UUID
private typealias VedtaksperiodeId = UUID

internal class V188UtbetalingerOgVilkårsgrunnlag: JsonMigration(188) {
    private companion object {
        private val ingenInnslagId = UUID.fromString("00000000-0000-0000-0000-000000000000") as InnslagId
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private fun withMDC(context: Map<String, String>, block: () -> Unit) {
            val contextMap = MDC.getCopyOfContextMap() ?: emptyMap()
            try {
                MDC.setContextMap(contextMap + context)
                block()
            } finally {
                MDC.setContextMap(contextMap)
            }
        }

    }
    override val description = "DRY RUN - Migrerer vedtaksperiodeutbetalinger til å ha vilkårsgrunnlagId"

    override fun doMigration(jsonNode: ObjectNode, meldingerSupplier: MeldingerSupplier) {
        val aktørId = jsonNode.path("aktørId").asText()
        withMDC(mapOf("aktørId" to aktørId)) {
            utførMigrering(aktørId, jsonNode)
        }
    }

    private fun utførMigrering(aktørId: String, jsonNode: ObjectNode) {
        val vedtaksperioderForPerson = Sykefraværstilfeller.vedtaksperioder(jsonNode)
        val sykefraværstilfeller = Sykefraværstilfeller.sykefraværstilfeller(vedtaksperioderForPerson)
        val innslagTidsstempel = jsonNode
            .path("vilkårsgrunnlagHistorikk")
            .map {
                val id = UUID.fromString(it.path("id").asText()) as InnslagId
                val tidsstempel = LocalDateTime.parse(it.path("opprettet").asText())
                id to tidsstempel
            }
        val vilkårsgrunnlag = jsonNode
            .path("vilkårsgrunnlagHistorikk")
            .associateBy({ UUID.fromString(it.path("id").asText()) as InnslagId }) { innslag ->
                innslag.path("vilkårsgrunnlag").associateBy({ UUID.fromString(it.path("vilkårsgrunnlagId").asText()) as VilkårsgrunnlagId }) { grunnlag ->
                    Vilkårsgrunnlag(
                        innslagId = UUID.fromString(innslag.path("id").asText()) as InnslagId,
                        vilkårsgrunnlagId = UUID.fromString(grunnlag.path("vilkårsgrunnlagId").asText()) as VilkårsgrunnlagId,
                        skjæringstidspunkt = LocalDate.parse(grunnlag.path("skjæringstidspunkt").asText()),
                        fraInfotrygd = grunnlag.path("type").asText() == "Infotrygd"
                    )
                }
            }

        jsonNode.path("arbeidsgivere").forEach { arbeidsgiver ->
            val beregningTilInnslagId = arbeidsgiver
                .path("beregnetUtbetalingstidslinjer")
                .associateBy({ UUID.fromString(it.path("id").asText()) as BeregningId }) { beregning ->
                    val opprettet = LocalDateTime.parse(beregning.path("tidsstempel").asText())
                    (UUID.fromString(beregning.path("vilkårsgrunnlagHistorikkInnslagId").asText()) as InnslagId)
                        .takeUnless { id -> id == ingenInnslagId }
                        ?: innslagTidsstempel.first { (_, tidsstempel) ->
                            tidsstempel < opprettet
                        }.first
                }
            val utbetalingTilInnslag = arbeidsgiver
                .path("utbetalinger")
                .associateBy({ UUID.fromString(it.path("id").asText()) as UtbetalingId }) { utbetaling ->
                    val beregningId = UUID.fromString(utbetaling.path("beregningId").asText()) as BeregningId
                    beregningTilInnslagId[beregningId].also {
                        if (it == null) {
                            sikkerlogg.info("[V188] finner ikke vilkårsgrunnlagInnslagId for utbetaling=${utbetaling.path("id").asText()} for aktørId=$aktørId")
                        }
                    }
                }

            arbeidsgiver.path("vedtaksperioder").forEach {
                migrerVedtaksperiode(sykefraværstilfeller, vilkårsgrunnlag, utbetalingTilInnslag, it)
            }
            arbeidsgiver.path("forkastede").forEach { forkastet ->
                migrerVedtaksperiode(
                    sykefraværstilfeller,
                    vilkårsgrunnlag,
                    utbetalingTilInnslag,
                    forkastet.path("vedtaksperiode")
                )
            }
        }
    }

    private fun migrerVedtaksperiode(
        sykefraværstilfeller: Set<Sykefraværstilfeller.Sykefraværstilfelle>,
        vilkårsgrunnlag: Map<InnslagId, Map<VilkårsgrunnlagId, Vilkårsgrunnlag>>,
        utbetalingTilInnslag: Map<UtbetalingId, InnslagId?>,
        vedtaksperiode: JsonNode
    ) {
        val vedtaksperiodeId = UUID.fromString(vedtaksperiode.path("id").asText()) as VedtaksperiodeId
        val skjæringstidspunktVedtaksperiode = LocalDate.parse(vedtaksperiode.path("skjæringstidspunkt").asText())
        val skjæringstidspunktFraInfotrygd = vedtaksperiode.path("skjæringstidspunktFraInfotrygd").takeIf { it.isTextual }?.asText()?.let {
            LocalDate.parse(it)
        }
        val førstedatoVedtaksperiode = LocalDate.parse(vedtaksperiode.path("fom").asText())
        val sistedatoVedtaksperiode = LocalDate.parse(vedtaksperiode.path("tom").asText())
        val søkeperiodeVedtaksperiode = minOf(skjæringstidspunktVedtaksperiode, førstedatoVedtaksperiode, skjæringstidspunktFraInfotrygd ?: førstedatoVedtaksperiode) til sistedatoVedtaksperiode

        val sykefraværstilfelle = sykefraværstilfeller.firstOrNull { tilfelle ->
            tilfelle.periode.overlapperMed(søkeperiodeVedtaksperiode)
        }?.periode ?: søkeperiodeVedtaksperiode

        val søkeperiode = søkeperiodeVedtaksperiode.oppdaterFom(sykefraværstilfelle)

        vedtaksperiode
            .path("utbetalinger")
            .forEach { utbetaling ->
                val vilkårsgrunnlagId = utbetaling.path("vilkårsgrunnlagId").takeUnless { it.isNull || it.isMissingNode }?.asText()
                val utbetalingId = UUID.fromString(utbetaling.path("utbetalingId").asText()) as UtbetalingId
                if (vilkårsgrunnlagId == null) {
                    val innslagId = utbetalingTilInnslag.getValue(utbetalingId)
                    if (innslagId != null) {
                        val match = finnVilkårsgrunnlagForUtbetaling(vilkårsgrunnlag, innslagId, skjæringstidspunktVedtaksperiode, søkeperiode)
                            ?: finnVilkårsgrunnlagForUtbetaling(vilkårsgrunnlag, innslagId, skjæringstidspunktVedtaksperiode, sykefraværstilfelle)
                        match?.log(utbetalingId, vedtaksperiodeId, skjæringstidspunktVedtaksperiode)
                        if (match == null) {
                            sikkerlogg.info("[V188] fant ikke match søkeperiode=$søkeperiode for utbetaling=$utbetalingId for vedtaksperiode=$vedtaksperiodeId med vedtaksperiodeSkjæringstidspunkt=$skjæringstidspunktVedtaksperiode")
                        } else {
                            // når ikke dry-run:
                            // (utbetaling as ObjectNode).put("vilkårsgrunnlagId", match.grunnlag.vilkårsgrunnlagId.toString())
                        }
                    }
                }
            }
    }

    private fun finnVilkårsgrunnlagForUtbetaling(
        vilkårsgrunnlag: Map<InnslagId, Map<VilkårsgrunnlagId, Vilkårsgrunnlag>>,
        innslagId: InnslagId,
        skjæringstidspunktVedtaksperiode: LocalDate,
        søkeperiode: Periode
    ): Match? {
        val ufiltrertListe = vilkårsgrunnlag[innslagId]?.values ?: return null
        val direkteMatch = matchDirekte(ufiltrertListe, skjæringstidspunktVedtaksperiode)
        if (direkteMatch != null) return direkteMatch
        return matchIndirekte(ufiltrertListe, søkeperiode)
    }

    private fun matchDirekte(liste: Collection<Vilkårsgrunnlag>, skjæringstidspunkt: LocalDate): Match? {
        return liste.firstOrNull { grunnlag -> grunnlag.skjæringstidspunkt == skjæringstidspunkt }?.let {
            Match.Direkte(it)
        }
    }
    private fun matchIndirekte(liste: Collection<Vilkårsgrunnlag>, søkeperiode: Periode): Match? {
        return liste.firstOrNull { grunnlag -> grunnlag.skjæringstidspunkt in søkeperiode }?.let {
            Match.Indirekte(søkeperiode, it)
        }
    }

    private sealed class Match(val grunnlag: Vilkårsgrunnlag) {
        abstract fun log(
            utbetalingId: UtbetalingId,
            vedtaksperiodeId: VedtaksperiodeId,
            skjæringstidspunktVedtaksperiode: LocalDate
        )

        protected fun loggMatch(
            tekst: String,
            utbetalingId: UtbetalingId,
            vedtaksperiodeId: VedtaksperiodeId,
            skjæringstidspunktVedtaksperiode: LocalDate
        ) {
            sikkerlogg.info("[V188] $tekst vilkårsgrunnlag=${grunnlag.vilkårsgrunnlagId} skjæringstidspunkt=${grunnlag.skjæringstidspunkt} for utbetaling=$utbetalingId for vedtaksperiode=$vedtaksperiodeId med vedtaksperiodeSkjæringstidspunkt=$skjæringstidspunktVedtaksperiode")
        }

        class Direkte(grunnlag: Vilkårsgrunnlag) : Match(grunnlag) {
            override fun log(
                utbetalingId: UtbetalingId,
                vedtaksperiodeId: VedtaksperiodeId,
                skjæringstidspunktVedtaksperiode: LocalDate
            ) {
                loggMatch("fant direkte match", utbetalingId, vedtaksperiodeId, skjæringstidspunktVedtaksperiode)
            }
        }
        class Indirekte(private val søkeperiode: Periode, grunnlag: Vilkårsgrunnlag) : Match(grunnlag) {
            override fun log(
                utbetalingId: UtbetalingId,
                vedtaksperiodeId: VedtaksperiodeId,
                skjæringstidspunktVedtaksperiode: LocalDate
            ) {
                loggMatch("fant indirekte match i søkeperiode=$søkeperiode", utbetalingId, vedtaksperiodeId, skjæringstidspunktVedtaksperiode)
            }
        }
    }

    private class Vilkårsgrunnlag(
        val innslagId: InnslagId,
        val vilkårsgrunnlagId: VilkårsgrunnlagId,
        val skjæringstidspunkt: LocalDate,
        val fraInfotrygd: Boolean
    )
}