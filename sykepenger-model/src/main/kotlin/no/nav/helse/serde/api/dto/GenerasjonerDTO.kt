package no.nav.helse.serde.api.dto

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import no.nav.helse.hendelser.Periode
import no.nav.helse.dto.SimuleringResultatDto
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.Dokumentsporing.Companion.ider
import no.nav.helse.person.Vedtaksperiode
import no.nav.helse.person.aktivitetslogg.UtbetalingInntektskilde
import no.nav.helse.serde.api.dto.Periodetilstand.Annullert
import no.nav.helse.serde.api.dto.Periodetilstand.ForberederGodkjenning
import no.nav.helse.serde.api.dto.Periodetilstand.IngenUtbetaling
import no.nav.helse.serde.api.dto.Periodetilstand.ManglerInformasjon
import no.nav.helse.serde.api.dto.Periodetilstand.Utbetalt
import no.nav.helse.serde.api.dto.Periodetilstand.UtbetaltVenterPåAnnenPeriode
import no.nav.helse.serde.api.dto.Periodetilstand.VenterPåAnnenPeriode
import no.nav.helse.serde.api.speil.SpeilGenerasjoner
import no.nav.helse.serde.api.speil.builders.ISpleisGrunnlag
import no.nav.helse.serde.api.speil.builders.IVilkårsgrunnlagHistorikk
import no.nav.helse.serde.api.speil.merge

data class SpeilGenerasjonDTO(
    val id: UUID, // Runtime
    val perioder: List<SpeilTidslinjeperiode>,
    val kildeTilGenerasjon: UUID
) {
    val size = perioder.size
}

enum class Periodetilstand {
    TilUtbetaling,
    TilAnnullering,
    Utbetalt,
    Annullert,
    AnnulleringFeilet,
    RevurderingFeilet,
    ForberederGodkjenning,
    ManglerInformasjon,
    UtbetaltVenterPåAnnenPeriode,
    VenterPåAnnenPeriode,
    TilGodkjenning,
    IngenUtbetaling,
    TilInfotrygd;
}

data class Utbetalingsinfo(
    val personbeløp: Int? = null,
    val arbeidsgiverbeløp: Int? = null,
    val totalGrad: Int // Speil vises grad i heltall
) {
    fun harUtbetaling() = personbeløp != null || arbeidsgiverbeløp != null
}

enum class Tidslinjeperiodetype {
    FØRSTEGANGSBEHANDLING,
    FORLENGELSE,
    OVERGANG_FRA_IT,
    INFOTRYGDFORLENGELSE;
}

abstract class SpeilTidslinjeperiode : Comparable<SpeilTidslinjeperiode> {
    abstract val vedtaksperiodeId: UUID
    abstract val generasjonId: UUID
    abstract val kilde: UUID
    abstract val fom: LocalDate
    abstract val tom: LocalDate
    abstract val sammenslåttTidslinje: List<SammenslåttDag>
    abstract val periodetype: Tidslinjeperiodetype
    abstract val inntektskilde: UtbetalingInntektskilde
    abstract val erForkastet: Boolean
    abstract val opprettet: LocalDateTime
    abstract val oppdatert: LocalDateTime
    abstract val periodetilstand: Periodetilstand
    abstract val skjæringstidspunkt: LocalDate
    abstract val hendelser: Set<UUID>
    abstract val sorteringstidspunkt: LocalDateTime

    internal open fun registrerBruk(vilkårsgrunnlaghistorikk: IVilkårsgrunnlagHistorikk, organisasjonsnummer: String): SpeilTidslinjeperiode {
        return this
    }

    internal abstract fun medPeriodetype(periodetype: Tidslinjeperiodetype): SpeilTidslinjeperiode
    internal fun erSammeVedtaksperiode(other: SpeilTidslinjeperiode) = vedtaksperiodeId == other.vedtaksperiodeId
    internal open fun venter() = periodetilstand in setOf(VenterPåAnnenPeriode, ForberederGodkjenning, ManglerInformasjon, UtbetaltVenterPåAnnenPeriode)

    internal abstract fun tilGenerasjon(generasjoner: SpeilGenerasjoner)
    override fun compareTo(other: SpeilTidslinjeperiode) = tom.compareTo(other.tom)
    internal open fun medOpplysningerFra(other: UberegnetPeriode): UberegnetPeriode? = null

    internal companion object {
        fun List<SpeilTidslinjeperiode>.sorterEtterHendelse() = this
            .sortedBy { it.sorteringstidspunkt }

        fun List<SpeilTidslinjeperiode>.utledPeriodetyper(): List<SpeilTidslinjeperiode> {
            val out = mutableListOf<SpeilTidslinjeperiode>()
            val sykefraværstilfeller = this.sortedBy { it.fom }.groupBy { it.skjæringstidspunkt }
            sykefraværstilfeller.forEach { (_, perioder) ->
                out.add(perioder.first().medPeriodetype(Tidslinjeperiodetype.FØRSTEGANGSBEHANDLING))
                perioder.zipWithNext { forrige, nåværende ->
                    if (forrige is BeregnetPeriode) out.add(nåværende.medPeriodetype(Tidslinjeperiodetype.FORLENGELSE))
                    else out.add(nåværende.medPeriodetype(Tidslinjeperiodetype.FØRSTEGANGSBEHANDLING))
                }
            }
            return out.sortedByDescending { it.fom }
        }
    }
}

private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private fun LocalDate.format() = format(formatter)

data class UberegnetVilkårsprøvdPeriode(
    override val vedtaksperiodeId: UUID,
    override val generasjonId: UUID,
    override val kilde: UUID,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val sammenslåttTidslinje: List<SammenslåttDag>,
    override val periodetype: Tidslinjeperiodetype, // feltet gir ikke mening for uberegnede perioder
    override val inntektskilde: UtbetalingInntektskilde, // feltet gir ikke mening for uberegnede perioder
    override val erForkastet: Boolean,
    override val sorteringstidspunkt: LocalDateTime,
    override val opprettet: LocalDateTime,
    override val oppdatert: LocalDateTime,
    override val periodetilstand: Periodetilstand,
    override val skjæringstidspunkt: LocalDate,
    override val hendelser: Set<UUID>,
    val vilkårsgrunnlagId: UUID
) : SpeilTidslinjeperiode() {

    internal constructor(uberegnetPeriode: UberegnetPeriode, vilkårsgrunnlagId: UUID, tidslinjeperiodetype: Tidslinjeperiodetype) :
            this(
                vedtaksperiodeId = uberegnetPeriode.vedtaksperiodeId,
                generasjonId = uberegnetPeriode.generasjonId,
                kilde = uberegnetPeriode.kilde,
                fom = uberegnetPeriode.fom,
                tom = uberegnetPeriode.tom,
                sammenslåttTidslinje = uberegnetPeriode.sammenslåttTidslinje,
                periodetype = tidslinjeperiodetype,
                inntektskilde = uberegnetPeriode.inntektskilde,
                erForkastet = uberegnetPeriode.erForkastet,
                sorteringstidspunkt = uberegnetPeriode.sorteringstidspunkt,
                opprettet = uberegnetPeriode.opprettet,
                oppdatert = uberegnetPeriode.oppdatert,
                periodetilstand = uberegnetPeriode.periodetilstand,
                skjæringstidspunkt = uberegnetPeriode.skjæringstidspunkt,
                hendelser = uberegnetPeriode.hendelser,
                vilkårsgrunnlagId = vilkårsgrunnlagId,
            )

    override fun medPeriodetype(periodetype: Tidslinjeperiodetype): SpeilTidslinjeperiode {
        return this.copy(periodetype = periodetype)
    }

    override fun tilGenerasjon(generasjoner: SpeilGenerasjoner) {
        generasjoner.uberegnetVilkårsprøvdPeriode(this)
    }
}

data class UberegnetPeriode(
    override val vedtaksperiodeId: UUID,
    override val generasjonId: UUID,
    override val kilde: UUID,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val sammenslåttTidslinje: List<SammenslåttDag>,
    override val periodetype: Tidslinjeperiodetype, // feltet gir ikke mening for uberegnede perioder
    override val inntektskilde: UtbetalingInntektskilde, // feltet gir ikke mening for uberegnede perioder
    override val erForkastet: Boolean,
    override val opprettet: LocalDateTime,
    override val oppdatert: LocalDateTime,
    override val sorteringstidspunkt: LocalDateTime,
    override val periodetilstand: Periodetilstand,
    override val skjæringstidspunkt: LocalDate,
    override val hendelser: Set<UUID>
) : SpeilTidslinjeperiode() {
    override fun toString(): String {
        return "${fom.format()}-${tom.format()} - $periodetilstand"
    }

    override fun tilGenerasjon(generasjoner: SpeilGenerasjoner) {
        generasjoner.uberegnetPeriode(this)
    }

    override fun medPeriodetype(periodetype: Tidslinjeperiodetype): SpeilTidslinjeperiode {
        return this.copy(periodetype = periodetype)
    }

    override fun medOpplysningerFra(other: UberegnetPeriode): UberegnetPeriode? {
        // kopierer bare -like- generasjoner; om en periode er strukket tilbake så bevarer vi generasjonen
        if (this.fom != other.fom) return null
        if (this.periodetilstand == IngenUtbetaling && other.periodetilstand != IngenUtbetaling) return null
        return this.copy(
            hendelser = this.hendelser + other.hendelser,
            sammenslåttTidslinje = other.sammenslåttTidslinje
        )
    }

    internal fun somAnnullering(annulleringen: AnnullertUtbetaling, sisteBeregnetPeriode: BeregnetPeriode): AnnullertPeriode {
        return AnnullertPeriode(
            vedtaksperiodeId = vedtaksperiodeId,
            generasjonId = generasjonId,
            kilde = kilde,
            fom = fom,
            tom = tom,
            opprettet = opprettet,
            // feltet gir ikke mening for annullert periode:
            vilkår = BeregnetPeriode.Vilkår(
                sykepengedager = BeregnetPeriode.Sykepengedager(fom, LocalDate.MAX, null, null, false),
                alder = sisteBeregnetPeriode.periodevilkår.alder
            ),
            beregnet = annulleringen.annulleringstidspunkt,
            oppdatert = oppdatert,
            periodetilstand = annulleringen.periodetilstand,
            hendelser = hendelser,
            beregningId = sisteBeregnetPeriode.beregningId,
            utbetaling = Utbetaling(
                Utbetalingtype.ANNULLERING,
                sisteBeregnetPeriode.utbetaling.korrelasjonsId,
                annulleringen.utbetalingstatus,
                0,
                0,
                sisteBeregnetPeriode.utbetaling.arbeidsgiverFagsystemId,
                sisteBeregnetPeriode.utbetaling.personFagsystemId,
                emptyMap(),
                null,
                annulleringen.id
            )
        )
    }

    internal class Builder(
        private val vedtaksperiodeId: UUID,
        private val generasjonId: UUID,
        private val kilde: UUID,
        private val skjæringstidspunkt: LocalDate,
        private val tilstand: Vedtaksperiode.Vedtaksperiodetilstand,
        private val generasjonOpprettet: LocalDateTime,
        private val forkastet: Boolean,
        private val generasjonAvsluttet: LocalDateTime?,
        private val opprettet: LocalDateTime,
        private val oppdatert: LocalDateTime,
        private val periode: Periode
    ) {
        private lateinit var sykdomstidslinje: List<Sykdomstidslinjedag>

        internal fun build(dokumentsporinger: Set<Dokumentsporing>): UberegnetPeriode {
            return UberegnetPeriode(
                vedtaksperiodeId = vedtaksperiodeId,
                generasjonId = generasjonId,
                kilde = kilde,
                fom = periode.start,
                tom = periode.endInclusive,
                sammenslåttTidslinje = sykdomstidslinje.merge(emptyList()),
                periodetype = Tidslinjeperiodetype.FØRSTEGANGSBEHANDLING, // feltet gir ikke mening for uberegnede perioder
                inntektskilde = UtbetalingInntektskilde.EN_ARBEIDSGIVER, // feltet gir ikke mening for uberegnede perioder
                erForkastet = false,
                sorteringstidspunkt = generasjonOpprettet,
                opprettet = opprettet,
                oppdatert = oppdatert,
                skjæringstidspunkt = skjæringstidspunkt,
                hendelser = dokumentsporinger.ider(),
                periodetilstand = generasjonAvsluttet?.let { if (forkastet) Annullert else IngenUtbetaling } ?: when (tilstand) {
                    is Vedtaksperiode.AvventerRevurdering -> UtbetaltVenterPåAnnenPeriode
                    is Vedtaksperiode.AvventerBlokkerendePeriode -> VenterPåAnnenPeriode

                    is Vedtaksperiode.AvventerHistorikk,
                    is Vedtaksperiode.AvventerHistorikkRevurdering,
                    is Vedtaksperiode.AvventerVilkårsprøving,
                    is Vedtaksperiode.AvventerVilkårsprøvingRevurdering -> ForberederGodkjenning

                    is Vedtaksperiode.AvventerInntektsmelding,
                    is Vedtaksperiode.AvventerInfotrygdHistorikk -> ManglerInformasjon
                    else -> error("Forventer ikke mappingregel for $tilstand")
                }

            )
        }

        internal fun medSykdomstidslinje(sykdomstidslinje: List<Sykdomstidslinjedag>) = apply {
            this.sykdomstidslinje = sykdomstidslinje
        }
    }
}

// Dekker datagrunnlaget vi trenger for å populere både pølsen og _hele_ saksbildet
data class BeregnetPeriode(
    override val vedtaksperiodeId: UUID,
    override val generasjonId: UUID,
    override val kilde: UUID,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val sammenslåttTidslinje: List<SammenslåttDag>,
    override val erForkastet: Boolean, // feltet trengs ikke i speil
    override val periodetype: Tidslinjeperiodetype,
    override val inntektskilde: UtbetalingInntektskilde, // verdien av dette feltet brukes bare for å sjekke !=null i speil
    override val opprettet: LocalDateTime,
    val generasjonOpprettet: LocalDateTime,
    val beregnet: LocalDateTime,
    override val oppdatert: LocalDateTime,
    override val periodetilstand: Periodetilstand,
    override val skjæringstidspunkt: LocalDate,
    override val hendelser: Set<UUID>,
    // todo: feltet brukes så og si ikke i speil, kan fjernes fra graphql
    // verdien av ID-en brukes ifm. å lage en unik ID for notatet om utbetalingene.
    val beregningId: UUID,
    val gjenståendeSykedager: Int?,
    val forbrukteSykedager: Int?,
    val maksdato: LocalDate,
    val utbetaling: Utbetaling,
    val periodevilkår: Vilkår,
    val vilkårsgrunnlagId: UUID?, // dette feltet er i != for beregnede perioder, men må være nullable så lenge annullerte perioder mappes til beregnet periode
    val forrigeGenerasjon: BeregnetPeriode? = null
) : SpeilTidslinjeperiode() {
    override val sorteringstidspunkt = beregnet

    override fun venter(): Boolean = super.venter() && periodetilstand != Utbetalt

    override fun registrerBruk(vilkårsgrunnlaghistorikk: IVilkårsgrunnlagHistorikk, organisasjonsnummer: String): BeregnetPeriode {
        val vilkårsgrunnlag = vilkårsgrunnlagId?.let { vilkårsgrunnlaghistorikk.leggIBøtta(it) } ?: return this
        if (vilkårsgrunnlag !is ISpleisGrunnlag) return this
        return this.copy(hendelser = this.hendelser + vilkårsgrunnlag.overstyringer)
    }

    override fun medPeriodetype(periodetype: Tidslinjeperiodetype): SpeilTidslinjeperiode {
        return this.copy(periodetype = periodetype)
    }

    /*
        finner ut om det har vært endringer i sykdomstidslinjen eller vilkårsgrunnlagene mellom periodene
        ved å se om det har vært endringer på den nye perioden
     */
    internal fun ingenEndringerMellom(other: BeregnetPeriode): Boolean {
        checkNotNull(this.forrigeGenerasjon) { "forventet ikke at forrigeGenerasjon er null" }
        if (other.vedtaksperiodeId == this.vedtaksperiodeId) return false
        // hvis vilkårsgrunnlaget har endret seg mellom forrige generasjon, så kan det likevel hende at 'other' (revurderingen før)
        // har allerede laget ny rad - og derfor trenger vi ikke lage enda en
        if (this.vilkårsgrunnlagId != this.forrigeGenerasjon.vilkårsgrunnlagId && this.vilkårsgrunnlagId != other.vilkårsgrunnlagId) return false
        return this.sammenslåttTidslinje
            .zip(this.forrigeGenerasjon.sammenslåttTidslinje) { ny, gammel ->
                ny.sammeGrunnlag(gammel)
            }
            .all { it }
    }

    override fun toString(): String {
        return "${fom.format()}-${tom.format()} - $periodetilstand - ${utbetaling.type}"
    }

    override fun tilGenerasjon(generasjoner: SpeilGenerasjoner) {
        check(utbetaling.type in setOf(Utbetalingtype.REVURDERING, Utbetalingtype.UTBETALING)) {
            "beregnet periode skal bare anvendes på utbetalte perioder"
        }
        when (utbetaling.type) {
            Utbetalingtype.REVURDERING -> generasjoner.revurdertPeriode(this)
            else -> generasjoner.utbetaltPeriode(this)
        }
    }

    data class Vilkår(
        val sykepengedager: Sykepengedager,
        val alder: Alder
    )

    data class Sykepengedager(
        val skjæringstidspunkt: LocalDate,
        val maksdato: LocalDate,
        val forbrukteSykedager: Int?,
        val gjenståendeDager: Int?,
        val oppfylt: Boolean
    )

    data class Alder(
        val alderSisteSykedag: Int,
        val oppfylt: Boolean
    )

    /**
     * Bygger en periode som har minst én utbetaling
     */
    internal class Builder(
        private val vedtaksperiodeId: UUID,
        private val generasjonId: UUID,
        private val kilde: UUID,
        private val periodetilstand: Vedtaksperiode.Vedtaksperiodetilstand,
        private val opprettet: LocalDateTime,
        private val oppdatert: LocalDateTime,
        private val periode: Periode,
        private val forrigeBeregnetPeriode: BeregnetPeriode?,
        private val generasjonOpprettet: LocalDateTime
    ) {
        private lateinit var beregnet: LocalDateTime
        private lateinit var skjæringstidspunkt: LocalDate
        private lateinit var utbetalingId: UUID
        private lateinit var korrelasjonsId: UUID
        private var gjenståendeSykedager: Int = 0
        private var forbrukteSykedager: Int = 0
        private lateinit var maksdato: LocalDate
        private lateinit var vilkårsgrunnlagId: UUID

        private lateinit var utbetalingstidslinje: List<Utbetalingstidslinjedag>
        private lateinit var sykdomstidslinje: List<Sykdomstidslinjedag>

        private var utbetalingtype: Utbetalingtype? = null
        private lateinit var utbetalingstatus: Utbetalingstatus
        private var utbetalingvurdering: Utbetaling.Vurdering? = null

        private var arbeidsgiverNettoBeløp: Int = 0
        private var personNettoBeløp: Int = 0
        private lateinit var arbeidsgiverFagsystemId: String
        private lateinit var personFagsystemId: String
        private val oppdrag = mutableMapOf<String, SpeilOppdrag>()

        fun build(alder: no.nav.helse.Alder, dokumentsporinger: Set<Dokumentsporing>): BeregnetPeriode? {
            val utbetalingtype = this.utbetalingtype ?: return null

            val avgrensetUtbetalingstidslinje = utbetalingstidslinje.filter { it.dato in periode }
            val utbetalingDTO = Utbetaling(
                type = utbetalingtype,
                korrelasjonsId = korrelasjonsId,
                status = utbetalingstatus,
                arbeidsgiverNettoBeløp = arbeidsgiverNettoBeløp,
                personNettoBeløp = personNettoBeløp,
                arbeidsgiverFagsystemId = arbeidsgiverFagsystemId,
                personFagsystemId = personFagsystemId,
                oppdrag = oppdrag,
                vurdering = utbetalingvurdering,
                id = utbetalingId
            )
            return BeregnetPeriode(
                vedtaksperiodeId = vedtaksperiodeId,
                generasjonId = generasjonId,
                kilde = kilde,
                beregningId = utbetalingId,
                fom = periode.start,
                tom = periode.endInclusive,
                erForkastet = false,
                periodetype = Tidslinjeperiodetype.FØRSTEGANGSBEHANDLING, // TODO: fikse,
                inntektskilde = UtbetalingInntektskilde.EN_ARBEIDSGIVER, // verdien av feltet brukes ikke i speil
                skjæringstidspunkt = skjæringstidspunkt,
                hendelser = dokumentsporinger.ider(),
                maksdato = maksdato,
                generasjonOpprettet = generasjonOpprettet,
                beregnet = beregnet,
                opprettet = opprettet,
                oppdatert = oppdatert,
                periodevilkår = periodevilkår(alder, skjæringstidspunkt, avgrensetUtbetalingstidslinje),
                sammenslåttTidslinje = sykdomstidslinje.merge(utbetalingstidslinje),
                gjenståendeSykedager = gjenståendeSykedager,
                forbrukteSykedager = forbrukteSykedager,
                utbetaling = utbetalingDTO,
                vilkårsgrunnlagId = vilkårsgrunnlagId,
                periodetilstand = utledePeriodetilstand(utbetalingDTO, avgrensetUtbetalingstidslinje),
                forrigeGenerasjon = forrigeBeregnetPeriode
            )
        }

        private fun utledePeriodetilstand(utbetalingDTO: Utbetaling, avgrensetUtbetalingstidslinje: List<Utbetalingstidslinjedag>) =
            when (utbetalingDTO.status) {
                Utbetalingstatus.IkkeGodkjent -> Periodetilstand.RevurderingFeilet
                Utbetalingstatus.Utbetalt -> when {
                    avgrensetUtbetalingstidslinje.none { it.utbetalingsinfo()?.harUtbetaling() == true } -> Periodetilstand.IngenUtbetaling
                    else -> Utbetalt
                }
                Utbetalingstatus.Ubetalt -> when {
                    periodetilstand in setOf(Vedtaksperiode.AvventerGodkjenningRevurdering, Vedtaksperiode.AvventerGodkjenning) -> Periodetilstand.TilGodkjenning
                    periodetilstand in setOf(Vedtaksperiode.AvventerHistorikkRevurdering, Vedtaksperiode.AvventerSimulering, Vedtaksperiode.AvventerSimuleringRevurdering) -> ForberederGodkjenning
                    periodetilstand in setOf(Vedtaksperiode.AvventerHistorikk) -> ForberederGodkjenning
                    periodetilstand == Vedtaksperiode.AvventerRevurdering -> UtbetaltVenterPåAnnenPeriode // flere AG; en annen AG har laget utbetaling på vegne av *denne* (revurdering)
                    periodetilstand == Vedtaksperiode.AvventerBlokkerendePeriode -> VenterPåAnnenPeriode // flere AG; en annen AG har laget utbetaling på vegne av *denne* (førstegangsvurdering)
                    else -> error("har ikke mappingregel for utbetalingstatus ${utbetalingDTO.status} og periodetilstand=$periodetilstand")
                }
                Utbetalingstatus.GodkjentUtenUtbetaling -> when {
                    utbetalingDTO.type == Utbetalingtype.REVURDERING -> Utbetalt
                    else -> Periodetilstand.IngenUtbetaling
                }
                Utbetalingstatus.Godkjent,
                Utbetalingstatus.Overført -> Periodetilstand.TilUtbetaling
                else -> error("har ikke mappingregel for ${utbetalingDTO.status}")
            }

        private fun List<Utbetalingstidslinjedag>.sisteNavDag() =
            lastOrNull { it.type == UtbetalingstidslinjedagType.NavDag }

        private fun periodevilkår(
            alder: no.nav.helse.Alder,
            skjæringstidspunkt: LocalDate,
            avgrensetUtbetalingstidslinje: List<Utbetalingstidslinjedag>
        ): Vilkår {
            val sisteSykepengedag = avgrensetUtbetalingstidslinje.sisteNavDag()?.dato ?: periode.endInclusive
            val sykepengedager = Sykepengedager(skjæringstidspunkt, maksdato, forbrukteSykedager, gjenståendeSykedager, maksdato > sisteSykepengedag)
            val alderSisteSykepengedag = alder.let {
                val alderSisteSykedag = it.alderPåDato(sisteSykepengedag)
                Alder(alderSisteSykedag, alderSisteSykedag < 70)
            }
            return Vilkår(sykepengedager, alderSisteSykepengedag)
        }

        internal fun medUtbetaling(
            id: UUID,
            korrelasjonsId: UUID,
            status: String,
            opprettet: LocalDateTime,
            maksdato: LocalDate,
            forbrukteSykedager: Int,
            gjenståendeDager: Int
        ) = apply {
            medUtbetaling(Utbetalingtype.UTBETALING, id, korrelasjonsId, status, opprettet, maksdato, forbrukteSykedager, gjenståendeDager)
        }
        internal fun medRevurdering(
            id: UUID,
            korrelasjonsId: UUID,
            status: String,
            opprettet: LocalDateTime,
            maksdato: LocalDate,
            forbrukteSykedager: Int,
            gjenståendeDager: Int
        ) = apply {
            medUtbetaling(Utbetalingtype.REVURDERING, id, korrelasjonsId, status, opprettet, maksdato, forbrukteSykedager, gjenståendeDager)
        }

        private fun medUtbetaling(
            type: Utbetalingtype,
            id: UUID,
            korrelasjonsId: UUID,
            status: String,
            opprettet: LocalDateTime,
            maksdato: LocalDate,
            forbrukteSykedager: Int,
            gjenståendeDager: Int
        ) {
            this.utbetalingtype = type
            this.korrelasjonsId = korrelasjonsId
            this.utbetalingstatus = utledStatus(type, status)
            this.utbetalingId = id
            this.beregnet = opprettet
            this.maksdato = maksdato
            this.forbrukteSykedager = forbrukteSykedager
            this.gjenståendeSykedager = gjenståendeDager
        }

        internal fun medVurdering(godkjent: Boolean, tidsstempel: LocalDateTime, automatisk: Boolean, ident: String) = apply {
            this.utbetalingvurdering = Utbetaling.Vurdering(godkjent, tidsstempel, automatisk, ident)
        }

        fun medArbeidsgiveroppdrag(oppdrag: SpeilOppdrag) = apply {
            this.arbeidsgiverFagsystemId = oppdrag.fagsystemId
            this.arbeidsgiverNettoBeløp = oppdrag.nettobeløp
            this.oppdrag[oppdrag.fagsystemId] = oppdrag
        }

        fun medPersonoppdrag(oppdrag: SpeilOppdrag) = apply {
            this.personFagsystemId = oppdrag.fagsystemId
            this.personNettoBeløp = oppdrag.nettobeløp
            this.oppdrag[oppdrag.fagsystemId] = oppdrag
        }

        fun medUtbetalingstidslinje(utbetalingstidslinje: List<Utbetalingstidslinjedag>) {
            this.utbetalingstidslinje = utbetalingstidslinje
        }

        fun medVilkårsgrunnlag(vilkårsgrunnlagId: UUID, skjæringstidspunkt: LocalDate) {
            this.vilkårsgrunnlagId = vilkårsgrunnlagId
            this.skjæringstidspunkt = skjæringstidspunkt
        }

        fun medSykdomstidslinje(sykdomstidslinje: List<Sykdomstidslinjedag>) = apply {
            this.sykdomstidslinje = sykdomstidslinje
        }

        private fun utledStatus(type: Utbetalingtype, status: String): Utbetalingstatus {
            return when (status) {
                "ANNULLERT" -> Utbetalingstatus.Annullert
                "GODKJENT" -> Utbetalingstatus.Godkjent
                "GODKJENT_UTEN_UTBETALING" -> Utbetalingstatus.GodkjentUtenUtbetaling
                "IKKE_GODKJENT" -> when (type) {
                    Utbetalingtype.REVURDERING -> Utbetalingstatus.IkkeGodkjent
                    else -> error("forsøker å mappe en IKKE_GODKJENT-utbetaling til Speil, som ikke er revurdering")
                }
                "OVERFØRT" -> Utbetalingstatus.Overført
                "IKKE_UTBETALT" -> Utbetalingstatus.Ubetalt
                "UTBETALT" -> Utbetalingstatus.Utbetalt
                else -> error("har ingen mappingregel for $status")
            }
        }
    }
}

data class AnnullertPeriode(
    override val vedtaksperiodeId: UUID,
    override val generasjonId: UUID,
    override val kilde: UUID,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val opprettet: LocalDateTime,
    val vilkår: BeregnetPeriode.Vilkår, // feltet gir ikke mening for annullert periode
    val beregnet: LocalDateTime,
    override val oppdatert: LocalDateTime,
    override val periodetilstand: Periodetilstand,
    override val hendelser: Set<UUID>,

    // todo: feltet brukes så og si ikke i speil, kan fjernes fra graphql
    // verdien av ID-en brukes ifm. å lage en unik ID for notatet om utbetalingene.
    val beregningId: UUID,
    val utbetaling: Utbetaling
) : SpeilTidslinjeperiode() {
    override val sammenslåttTidslinje: List<SammenslåttDag> = emptyList() // feltet gir ikke mening for annullert periode
    override val erForkastet = true
    override val skjæringstidspunkt = fom // feltet gir ikke mening for annullert periode
    override val periodetype = Tidslinjeperiodetype.FØRSTEGANGSBEHANDLING // feltet gir ikke mening for annullert periode
    override val inntektskilde = UtbetalingInntektskilde.EN_ARBEIDSGIVER // feltet gir ikke mening for annullert periode
    override val sorteringstidspunkt = beregnet
    override fun medPeriodetype(periodetype: Tidslinjeperiodetype): SpeilTidslinjeperiode {
        return this
    }

    override fun tilGenerasjon(generasjoner: SpeilGenerasjoner) {
        generasjoner.annullertPeriode(this)
    }

    // returnerer en beregnet perioder for
    // at mapping til graphql skal være lik.
    // TODO: Speil bør ha et konsept om 'AnnullertPeriode' som egen type,
    // slik at vi kan slippe å sende så mange unødvendige felter for annulleringene
    fun somBeregnetPeriode(): BeregnetPeriode {
        return BeregnetPeriode(
            vedtaksperiodeId = vedtaksperiodeId,
            generasjonId = generasjonId,
            kilde = kilde,
            fom = fom,
            tom = tom,
            sammenslåttTidslinje = sammenslåttTidslinje,
            erForkastet = erForkastet,
            periodetype = periodetype,
            inntektskilde = inntektskilde,
            opprettet = opprettet,
            generasjonOpprettet = beregnet,
            beregnet = beregnet,
            oppdatert = oppdatert,
            periodetilstand = periodetilstand,
            skjæringstidspunkt = skjæringstidspunkt,
            hendelser = hendelser,
            beregningId = beregningId,
            gjenståendeSykedager = null,
            forbrukteSykedager = null,
            maksdato = LocalDate.MAX,
            utbetaling = utbetaling,
            periodevilkår = vilkår,
            vilkårsgrunnlagId = null
        )
    }
}

internal class AnnullertUtbetaling(
    internal val id: UUID,
    private val korrelasjonsId: UUID,
    internal val annulleringstidspunkt: LocalDateTime,
    internal val utbetalingstatus: Utbetalingstatus
) {
    val periodetilstand = when (utbetalingstatus) {
        Utbetalingstatus.Annullert -> Periodetilstand.Annullert
        else -> Periodetilstand.TilAnnullering
    }

    fun annullerer(korrelasjonsId: UUID) = this.korrelasjonsId == korrelasjonsId

}

data class SpeilOppdrag(
    val fagsystemId: String,
    val tidsstempel: LocalDateTime,
    val nettobeløp: Int,
    val simulering: Simulering?,
    val utbetalingslinjer: List<Utbetalingslinje>
) {
    data class Simulering(
        val totalbeløp: Int,
        val perioder: List<Simuleringsperiode>
    )

    data class Simuleringsperiode(
        val fom: LocalDate,
        val tom: LocalDate,
        val utbetalinger: List<Simuleringsutbetaling>
    )

    data class Simuleringsutbetaling(
        val mottakerId: String,
        val mottakerNavn: String,
        val forfall: LocalDate,
        val feilkonto: Boolean,
        val detaljer: List<Simuleringsdetaljer>
    )

    data class Simuleringsdetaljer(
        val faktiskFom: LocalDate,
        val faktiskTom: LocalDate,
        val konto: String,
        val beløp: Int,
        val tilbakeføring: Boolean,
        val sats: Double,
        val typeSats: String,
        val antallSats: Int,
        val uføregrad: Int,
        val klassekode: String,
        val klassekodeBeskrivelse: String,
        val utbetalingstype: String,
        val refunderesOrgNr: String
    )

    data class Utbetalingslinje(
        val fom: LocalDate,
        val tom: LocalDate,
        val dagsats: Int,
        val grad: Int,
        val endringskode: EndringskodeDTO
    )

    class Builder(
        private val fagsystemId: String,
        private val tidsstempel: LocalDateTime,
        private val nettobeløp: Int,
        simuleringresultat: SimuleringResultatDto?
    ) {
        private var simulering: Simulering? = null
        private val utbetalingslinjer = mutableListOf<Utbetalingslinje>()

        init {
            medSimulering(simuleringresultat)
        }

        fun medOppdragslinje(fom: LocalDate, tom: LocalDate, beløp: Int, grad: Int, endringskode: EndringskodeDTO) = apply {
            utbetalingslinjer.add(Utbetalingslinje(fom, tom, beløp, grad, endringskode))
        }

        fun build() = SpeilOppdrag(fagsystemId, tidsstempel, nettobeløp, simulering, utbetalingslinjer)

        private fun medSimulering(simuleringsresultat: SimuleringResultatDto?) {
            if (simuleringsresultat == null) return
            this.simulering = Simulering(
                totalbeløp = simuleringsresultat.totalbeløp,
                perioder = simuleringsresultat.perioder.map { periode ->
                    Simuleringsperiode(
                        fom = periode.fom,
                        tom = periode.tom,
                        utbetalinger = periode.utbetalinger.map { utbetaling ->
                            Simuleringsutbetaling(
                                mottakerId = utbetaling.utbetalesTil.id,
                                mottakerNavn = utbetaling.utbetalesTil.navn,
                                forfall = utbetaling.forfallsdato,
                                feilkonto = utbetaling.feilkonto,
                                detaljer = utbetaling.detaljer.map {
                                    Simuleringsdetaljer(
                                        faktiskFom = it.fom,
                                        faktiskTom = it.tom,
                                        konto = it.konto,
                                        beløp = it.beløp,
                                        tilbakeføring = it.tilbakeføring,
                                        sats = it.sats.sats,
                                        typeSats = it.sats.type,
                                        antallSats = it.sats.antall,
                                        uføregrad = it.uføregrad,
                                        klassekode = it.klassekode.kode,
                                        klassekodeBeskrivelse = it.klassekode.beskrivelse,
                                        utbetalingstype = it.utbetalingstype,
                                        refunderesOrgNr = it.refunderesOrgnummer
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }
    }
}

enum class Utbetalingstatus {
    Annullert,
    Godkjent,
    GodkjentUtenUtbetaling,
    IkkeGodkjent,
    Overført,
    Ubetalt,
    Utbetalt
}

enum class Utbetalingtype {
    UTBETALING,
    ETTERUTBETALING,
    ANNULLERING,
    REVURDERING,
    FERIEPENGER
}

class Utbetaling(
    val type: Utbetalingtype,
    val korrelasjonsId: UUID,
    val status: Utbetalingstatus,
    val arbeidsgiverNettoBeløp: Int,
    val personNettoBeløp: Int,
    val arbeidsgiverFagsystemId: String,
    val personFagsystemId: String,
    val oppdrag: Map<String, SpeilOppdrag>,
    val vurdering: Vurdering?,
    val id: UUID
) {
    data class Vurdering(
        val godkjent: Boolean,
        val tidsstempel: LocalDateTime,
        val automatisk: Boolean,
        val ident: String
    )
}

data class Refusjon(
    val arbeidsgiverperioder: List<Periode>,
    val endringer: List<Endring>,
    val førsteFraværsdag: LocalDate?,
    val sisteRefusjonsdag: LocalDate?,
    val beløp: Double?,
) {
    data class Periode(
        val fom: LocalDate,
        val tom: LocalDate
    )

    data class Endring(
        val beløp: Double,
        val dato: LocalDate
    )
}
