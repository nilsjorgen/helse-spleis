package no.nav.helse.serde.api.builders

import no.nav.helse.serde.api.builders.UtbetalingshistorikkBuilder.SykdomshistorikkElementBuilder.Companion.build
import no.nav.helse.serde.api.dto.UtbetalingshistorikkElementDTO
import no.nav.helse.serde.reflection.Utbetalingstatus
import no.nav.helse.sykdomstidslinje.Sykdomshistorikk
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.utbetalingslinjer.Utbetaling
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class UtbetalingshistorikkBuilder : BuilderState() {
    private val utbetalingberegninger = Utbetalingberegninger()
    private val sykdomshistorikkElementBuilders = mutableListOf<SykdomshistorikkElementBuilder>()
    private val utbetalingstidslinjeBuilders = mutableListOf<UtbetalingInfo>()

    internal fun build() = UtbetalingInfo
        .utbetalinger(utbetalingstidslinjeBuilders, utbetalingberegninger)
        .mapNotNull { sykdomshistorikkElementBuilders.build(it.first, it.second, it.third) }
        .reversed()

    private data class UtbetalingInfo(
        private val beregningId: UUID,
        private val type: String,
        private val maksdato: LocalDate,
        private val status: String,
        private val gjenståendeSykedager: Int?,
        private val forbrukteSykedager: Int?,
        private val arbeidsgiverNettoBeløp: Int,
        private val personNettoBeløp: Int,
        private val tidsstempel: LocalDateTime,
        private val vurderingBuilder: VurderingBuilder,
        private val oppdragBuilder: OppdragBuilder,
        private val utbetalingstidslinjeBuilder: UtbetalingstidslinjeBuilder
    ) {
        fun utbetaling() = UtbetalingshistorikkElementDTO.UtbetalingDTO(
            utbetalingstidslinje = utbetalingstidslinjeBuilder.build(),
            beregningId = beregningId,
            type = type,
            maksdato = maksdato,
            status = status,
            tidsstempel = tidsstempel,
            gjenståendeSykedager = gjenståendeSykedager,
            forbrukteSykedager = forbrukteSykedager,
            arbeidsgiverNettoBeløp = arbeidsgiverNettoBeløp,
            personNettoBeløp = personNettoBeløp,
            arbeidsgiverFagsystemId = oppdragBuilder.arbeidsgiverFagsystemId(),
            personFagsystemId = oppdragBuilder.personFagsystemId(),
            vurdering = vurderingBuilder.build()
        )

        companion object {
            fun utbetalinger(
                liste: List<UtbetalingInfo>,
                utbetalingberegninger: Utbetalingberegninger
            ): List<Triple<UUID, UUID, UtbetalingshistorikkElementDTO.UtbetalingDTO>> {
                // Vi bruker en random UUID fordi en annullering ikke selv sitter på en beregningId

                return liste.map {
                    val (sykdomshistorikkId, vilkårsgrunnlagHistorikkId) = utbetalingberegninger.historikkIder(it.beregningId)
                    Triple(sykdomshistorikkId ?: UUID.randomUUID(), vilkårsgrunnlagHistorikkId ?: UUID.randomUUID(), it.utbetaling())
                }
            }
        }
    }

    private class Utbetalingberegninger {
        private val liste = mutableListOf<BeregningInfo>()

        fun add(beregningInfo: BeregningInfo) = liste.add(beregningInfo)
        fun historikkIder(beregningId: UUID) = BeregningInfo.historikkIder(liste, beregningId)
    }

    private class BeregningInfo(
        private val beregningId: UUID,
        private val sykdomshistorikkElementId: UUID,
        private val vilkårsgrunnlagHistorikkInnslagId: UUID
    ) {
        companion object {
            fun historikkIder(beregningInfo: List<BeregningInfo>, beregningId: UUID) =
                beregningInfo.firstOrNull { it.beregningId == beregningId }?.sykdomshistorikkElementId to
                    beregningInfo.firstOrNull { it.beregningId == beregningId }?.vilkårsgrunnlagHistorikkInnslagId
        }
    }

    override fun preVisitUtbetaling(
        utbetaling: Utbetaling,
        id: UUID,
        beregningId: UUID,
        type: Utbetaling.Utbetalingtype,
        tilstand: Utbetaling.Tilstand,
        tidsstempel: LocalDateTime,
        oppdatert: LocalDateTime,
        arbeidsgiverNettoBeløp: Int,
        personNettoBeløp: Int,
        maksdato: LocalDate,
        forbrukteSykedager: Int?,
        gjenståendeSykedager: Int?,
        stønadsdager: Int
    ) {
        if (tilstand is Utbetaling.Forkastet) return

        val vurderingBuilder = VurderingBuilder()
        pushState(vurderingBuilder)

        val oppdragBuilder = OppdragBuilder()
        pushState(oppdragBuilder)

        val utbetalingstidslinjeBuilder = UtbetalingstidslinjeBuilder(mutableListOf())
        utbetalingstidslinjeBuilders.add(
            UtbetalingInfo(
                // en annullering kopierer den forrige utbetalingsens beregningId
                beregningId = if (utbetaling.erAnnullering()) UUID.randomUUID() else beregningId,
                type = type.name,
                maksdato = maksdato,
                status = Utbetalingstatus.fraTilstand(tilstand).name,
                tidsstempel = tidsstempel,
                gjenståendeSykedager = gjenståendeSykedager,
                forbrukteSykedager = forbrukteSykedager,
                arbeidsgiverNettoBeløp = arbeidsgiverNettoBeløp,
                personNettoBeløp = personNettoBeløp,
                vurderingBuilder = vurderingBuilder,
                oppdragBuilder = oppdragBuilder,
                utbetalingstidslinjeBuilder = utbetalingstidslinjeBuilder
            )
        )
        pushState(utbetalingstidslinjeBuilder)
    }

    override fun postVisitUtbetalinger(utbetalinger: List<Utbetaling>) {
        popState()
    }

    override fun postVisitSykdomshistorikk(sykdomshistorikk: Sykdomshistorikk) {
        popState()
    }

    override fun visitUtbetalingstidslinjeberegning(
        id: UUID,
        tidsstempel: LocalDateTime,
        sykdomshistorikkElementId: UUID,
        inntektshistorikkInnslagId: UUID,
        vilkårsgrunnlagHistorikkInnslagId: UUID
    ) {
        utbetalingberegninger.add(BeregningInfo(id, sykdomshistorikkElementId, vilkårsgrunnlagHistorikkInnslagId))
    }

    override fun preVisitSykdomshistorikkElement(element: Sykdomshistorikk.Element, id: UUID, hendelseId: UUID?, tidsstempel: LocalDateTime) {
        val elementBuilder = SykdomshistorikkElementBuilder(id)
        sykdomshistorikkElementBuilders.add(elementBuilder)
        pushState(elementBuilder)
    }

    private class SykdomshistorikkElementBuilder(private val id: UUID) : BuilderState() { //ID er sykdomshistorikkId
        private lateinit var hendelsetidslinje: SykdomstidslinjeBuilder
        private lateinit var beregnettidslinje: SykdomstidslinjeBuilder

        companion object {
            fun List<SykdomshistorikkElementBuilder>.build(
                sykdomshistorikkId: UUID,
                vilkårsgrunnlagHistorikkId: UUID,
                utbetaling: UtbetalingshistorikkElementDTO.UtbetalingDTO
            ): UtbetalingshistorikkElementDTO? {
                return firstOrNull { it.id == sykdomshistorikkId }?.let {
                    UtbetalingshistorikkElementDTO(
                        hendelsetidslinje = it.hendelsetidslinje.build(),
                        beregnettidslinje = it.beregnettidslinje.build(),
                        vilkårsgrunnlagHistorikkId = vilkårsgrunnlagHistorikkId,
                        tidsstempel = utbetaling.tidsstempel,
                        utbetaling = utbetaling
                    )
                } ?: if (utbetaling.erAnnullering()) {
                    UtbetalingshistorikkElementDTO(
                        hendelsetidslinje = emptyList(),
                        beregnettidslinje = emptyList(),
                        vilkårsgrunnlagHistorikkId = vilkårsgrunnlagHistorikkId,
                        tidsstempel = utbetaling.tidsstempel,
                        utbetaling = utbetaling
                    )
                } else null
            }
        }

        override fun preVisitHendelseSykdomstidslinje(
            tidslinje: Sykdomstidslinje,
            hendelseId: UUID?,
            tidsstempel: LocalDateTime
        ) {
            hendelsetidslinje = SykdomstidslinjeBuilder()
            pushState(hendelsetidslinje)
        }

        override fun preVisitBeregnetSykdomstidslinje(tidslinje: Sykdomstidslinje) {
            beregnettidslinje = SykdomstidslinjeBuilder()
            pushState(beregnettidslinje)
        }

        override fun postVisitSykdomshistorikkElement(element: Sykdomshistorikk.Element, id: UUID, hendelseId: UUID?, tidsstempel: LocalDateTime) {
            popState()
        }
    }
}
