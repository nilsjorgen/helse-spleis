package no.nav.helse.person


import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Subsumsjon
import no.nav.helse.hendelser.til
import no.nav.helse.person.Inntektshistorikk.Inntektsopplysning.Companion.lagre
import no.nav.helse.person.etterlevelse.SubsumsjonObserver
import no.nav.helse.person.etterlevelse.SubsumsjonObserver.Companion.subsumsjonsformat
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.summer
import org.slf4j.LoggerFactory

internal class Inntektshistorikk private constructor(private val historikk: MutableList<Innslag>) {

    internal constructor() : this(mutableListOf())

    internal companion object {
        internal val NULLUUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")

        internal fun gjenopprett(list: List<Innslag>) = Inntektshistorikk(list.toMutableList())
    }

    internal fun accept(visitor: InntekthistorikkVisitor) {
        visitor.preVisitInntekthistorikk(this)
        historikk.forEach { it.accept(visitor) }
        visitor.postVisitInntekthistorikk(this)
    }

    private fun leggTil(nyttInnslag: Innslag) {
        val gjeldende = nyesteInnslag() ?: return historikk.add(0, nyttInnslag)
        val oppdatertInnslag = (gjeldende + nyttInnslag) ?: return
        historikk.add(0, oppdatertInnslag)
    }

    internal fun nyesteInnslag() = historikk.firstOrNull()

    internal fun nyesteId() = Innslag.nyesteId(this)

    internal fun isNotEmpty() = historikk.isNotEmpty()

    internal fun omregnetÅrsinntekt(skjæringstidspunkt: LocalDate, dato: LocalDate, førsteFraværsdag: LocalDate?): Inntektsopplysning? =
        omregnetÅrsinntekt(skjæringstidspunkt, førsteFraværsdag) ?: skjæringstidspunkt
            .takeIf { it <= dato }
            ?.let { nyesteInnslag()?.omregnetÅrsinntektInfotrygd(it til dato) }

    internal fun omregnetÅrsinntekt(skjæringstidspunkt: LocalDate, førsteFraværsdag: LocalDate?): Inntektsopplysning? =
        nyesteInnslag()?.omregnetÅrsinntekt(skjæringstidspunkt, førsteFraværsdag)

    internal fun rapportertInntekt(dato: LocalDate): Inntektsopplysning? =
        nyesteInnslag()?.rapportertInntekt(dato)

    internal class Innslag private constructor(private val id: UUID, private val inntekter: List<Inntektsopplysning>) {
        constructor(inntekter: List<Inntektsopplysning> = emptyList()) : this(UUID.randomUUID(), inntekter)

        internal fun accept(visitor: InntekthistorikkVisitor) {
            visitor.preVisitInnslag(this, id)
            inntekter.forEach { it.accept(visitor) }
            visitor.postVisitInnslag(this, id)
        }

        internal fun omregnetÅrsinntekt(skjæringstidspunkt: LocalDate, førsteFraværsdag: LocalDate?) =
            inntekter
                .mapNotNull { it.omregnetÅrsinntekt(skjæringstidspunkt, førsteFraværsdag) }
                .minOrNull()

        internal fun rapportertInntekt(dato: LocalDate) =
            inntekter
                .sorted()
                .mapNotNull { it.rapportertInntekt(dato) }
                .firstOrNull()

        internal fun omregnetÅrsinntektInfotrygd(periode: Periode) =
            inntekter
                .filterIsInstance<Infotrygd>()
                .sorted()
                .mapNotNull { it.omregnetÅrsinntekt(periode) }
                .firstOrNull()

        override fun equals(other: Any?): Boolean {
            return other is Innslag && this.inntekter == other.inntekter
        }

        operator fun plus(other: Innslag): Innslag? {
            var inntekter = this.inntekter
            other.inntekter.forEach { inntektsopplysning ->
                inntekter = inntektsopplysning.lagre(inntekter)
            }
            val nytt = Innslag(inntekter)
            if (this == nytt) return null
            return nytt
        }

        internal companion object {
            internal fun gjenopprett(id: UUID, inntektsopplysninger: List<Inntektsopplysning>) =
                Innslag(id, inntektsopplysninger)

            internal fun nyesteId(inntektshistorikk: Inntektshistorikk) = inntektshistorikk.nyesteInnslag()!!.id
        }
    }

    internal abstract class Inntektsopplysning protected constructor(
        protected val dato: LocalDate,
        private val prioritet: Int
    ): Comparable<Inntektsopplysning> {
        protected constructor(other: Inntektsopplysning) : this(other.dato, other.prioritet)

        abstract fun accept(visitor: InntekthistorikkVisitor)
        open fun omregnetÅrsinntekt(skjæringstidspunkt: LocalDate, førsteFraværsdag: LocalDate?): Inntektsopplysning? = null
        abstract fun omregnetÅrsinntekt(): Inntekt
        open fun rapportertInntekt(dato: LocalDate): Inntektsopplysning? = null
        abstract fun rapportertInntekt(): Inntekt
        open fun kanLagres(other: Inntektsopplysning) = this != other
        open fun skalErstattesAv(other: Inntektsopplysning) = this == other

        final override fun equals(other: Any?) = other is Inntektsopplysning && erSamme(other)

        protected abstract fun erSamme(other: Inntektsopplysning): Boolean

        open fun subsumerSykepengegrunnlag(
            subsumsjonObserver: SubsumsjonObserver,
            skjæringstidspunkt: LocalDate,
            organisasjonsnummer: String,
            startdatoArbeidsforhold: LocalDate?,
            forklaring: String?,
            subsumsjon: Subsumsjon?
        ) { }
        open fun subsumerArbeidsforhold(
            subsumsjonObserver: SubsumsjonObserver,
            skjæringstidspunkt: LocalDate,
            organisasjonsnummer: String,
            forklaring: String,
            oppfylt: Boolean
        ) {}

        final override fun compareTo(other: Inntektsopplysning) =
            (-this.dato.compareTo(other.dato)).takeUnless { it == 0 } ?: -this.prioritet.compareTo(other.prioritet)

        companion object {
            internal fun <Opplysning: Inntektsopplysning> Opplysning.lagre(liste: List<Opplysning>): List<Opplysning> {
                if (liste.any { !it.kanLagres(this) }) return liste
                return liste.filterNot { it.skalErstattesAv(this) } + this
            }

            internal fun List<Inntektsopplysning>.valider(aktivitetslogg: IAktivitetslogg) {
                if (all { it is SkattComposite }) {
                    aktivitetslogg.funksjonellFeil("Bruker mangler nødvendig inntekt ved validering av Vilkårsgrunnlag")
                }
            }
        }
    }

    internal class Saksbehandler(
        private val id: UUID,
        dato: LocalDate,
        private val hendelseId: UUID,
        private val beløp: Inntekt,
        private val tidsstempel: LocalDateTime = LocalDateTime.now()
    ) : Inntektsopplysning(dato, 100) {

        override fun accept(visitor: InntekthistorikkVisitor) {
            visitor.visitSaksbehandler(this, id, dato, hendelseId, beløp, tidsstempel)
        }

        override fun omregnetÅrsinntekt(skjæringstidspunkt: LocalDate, førsteFraværsdag: LocalDate?) = takeIf { it.dato == skjæringstidspunkt }
        override fun omregnetÅrsinntekt(): Inntekt = beløp

        override fun rapportertInntekt(): Inntekt = error("Saksbehandler har ikke grunnlag for sammenligningsgrunnlag")

        override fun skalErstattesAv(other: Inntektsopplysning) =
            other is Saksbehandler && this.dato == other.dato

        override fun erSamme(other: Inntektsopplysning) =
            other is Saksbehandler && this.dato == other.dato && this.beløp == other.beløp

        override fun subsumerSykepengegrunnlag(
            subsumsjonObserver: SubsumsjonObserver,
            skjæringstidspunkt: LocalDate,
            organisasjonsnummer: String,
            startdatoArbeidsforhold: LocalDate?,
            forklaring: String?,
            subsumsjon: Subsumsjon?
        ) {
            if(subsumsjon == null) return
            requireNotNull(forklaring) { "Det skal være en forklaring fra saksbehandler ved overstyring av inntekt" }
            if (subsumsjon.paragraf == Paragraf.PARAGRAF_8_28.ref
                && subsumsjon.ledd == Ledd.LEDD_3.nummer
                && subsumsjon.bokstav == Bokstav.BOKSTAV_B.ref.toString()
            ) {
                requireNotNull(startdatoArbeidsforhold) { "Fant ikke aktivt arbeidsforhold for skjæringstidspunktet i arbeidsforholdshistorikken" }
                subsumsjonObserver.`§ 8-28 ledd 3 bokstav b`(
                    organisasjonsnummer = organisasjonsnummer,
                    startdatoArbeidsforhold = startdatoArbeidsforhold,
                    overstyrtInntektFraSaksbehandler = mapOf("dato" to dato, "beløp" to beløp.reflection { _, månedlig, _, _ -> månedlig }),
                    skjæringstidspunkt = skjæringstidspunkt,
                    forklaring = forklaring,
                    grunnlagForSykepengegrunnlag = omregnetÅrsinntekt()
                )
            } else if (subsumsjon.paragraf == Paragraf.PARAGRAF_8_28.ref
                && subsumsjon.ledd == Ledd.LEDD_3.nummer
                && subsumsjon.bokstav == Bokstav.BOKSTAV_C.ref.toString()
            ) {
                subsumsjonObserver.`§ 8-28 ledd 3 bokstav c`(
                    organisasjonsnummer = organisasjonsnummer,
                    overstyrtInntektFraSaksbehandler = mapOf("dato" to dato, "beløp" to beløp.reflection { _, månedlig, _, _ -> månedlig }),
                    skjæringstidspunkt = skjæringstidspunkt,
                    forklaring = forklaring,
                    grunnlagForSykepengegrunnlag = omregnetÅrsinntekt()
                )
            } else if (subsumsjon.paragraf == Paragraf.PARAGRAF_8_28.ref && subsumsjon.ledd == Ledd.LEDD_5.nummer) {
                subsumsjonObserver.`§ 8-28 ledd 5`(
                    organisasjonsnummer = organisasjonsnummer,
                    overstyrtInntektFraSaksbehandler = mapOf("dato" to dato, "beløp" to beløp.reflection { _, månedlig, _, _ -> månedlig }),
                    skjæringstidspunkt = skjæringstidspunkt,
                    forklaring = forklaring,
                    grunnlagForSykepengegrunnlag = omregnetÅrsinntekt()
                )
            } else {
                sikkerLogg.warn("Overstyring av ghost: inntekt ble overstyrt med ukjent årsak: $forklaring")
            }

        }

        override fun subsumerArbeidsforhold(
            subsumsjonObserver: SubsumsjonObserver,
            skjæringstidspunkt: LocalDate,
            organisasjonsnummer: String,
            forklaring: String,
            oppfylt: Boolean
        ) {
            subsumsjonObserver.`§ 8-15`(
                skjæringstidspunkt,
                organisasjonsnummer,
                emptyList(),
                forklaring,
                oppfylt
            )
        }
    }

    internal class Infotrygd(
        private val id: UUID,
        dato: LocalDate,
        private val hendelseId: UUID,
        private val beløp: Inntekt,
        private val tidsstempel: LocalDateTime = LocalDateTime.now()
    ) : Inntektsopplysning(dato, 80) {

        override fun accept(visitor: InntekthistorikkVisitor) {
            visitor.visitInfotrygd(this, id, dato, hendelseId, beløp, tidsstempel)
        }

        // TODO: egen test for å bruke førstefraværsdag her: https://trello.com/c/QFYSoFOs
        override fun omregnetÅrsinntekt(skjæringstidspunkt: LocalDate, førsteFraværsdag: LocalDate?) = takeIf { it.dato == skjæringstidspunkt }
        override fun omregnetÅrsinntekt(): Inntekt = beløp

        internal fun omregnetÅrsinntekt(periode: Periode) = takeIf { it.dato in periode }

        override fun rapportertInntekt(): Inntekt = error("Infotrygd har ikke grunnlag for sammenligningsgrunnlag")

        override fun skalErstattesAv(other: Inntektsopplysning) =
            other is Infotrygd && this.dato == other.dato

        override fun erSamme(other: Inntektsopplysning): Boolean {
            if (other !is Infotrygd) return false
            return this.dato == other.dato && this.beløp == other.beløp
        }
    }

    internal class Inntektsmelding(
        private val id: UUID,
        dato: LocalDate,
        private val hendelseId: UUID,
        private val beløp: Inntekt,
        private val tidsstempel: LocalDateTime = LocalDateTime.now()
    ) : Inntektsopplysning(dato, 60) {

        override fun accept(visitor: InntekthistorikkVisitor) {
            visitor.visitInntektsmelding(this, id, dato, hendelseId, beløp, tidsstempel)
        }

        override fun omregnetÅrsinntekt(skjæringstidspunkt: LocalDate, førsteFraværsdag: LocalDate?): Inntektsopplysning? {
            if (dato == skjæringstidspunkt) return this
            if (førsteFraværsdag == null || dato != førsteFraværsdag) return null
            if (YearMonth.from(skjæringstidspunkt) == YearMonth.from(førsteFraværsdag)) return this
            return IkkeRapportert(UUID.randomUUID(), skjæringstidspunkt)
        }

        override fun omregnetÅrsinntekt(): Inntekt = beløp

        override fun rapportertInntekt(): Inntekt = error("Inntektsmelding har ikke grunnlag for sammenligningsgrunnlag")

        override fun kanLagres(other: Inntektsopplysning) = !skalErstattesAv(other)

        override fun skalErstattesAv(other: Inntektsopplysning) =
            other is Inntektsmelding && this.dato == other.dato

        override fun erSamme(other: Inntektsopplysning): Boolean {
            return other is Inntektsmelding && this.dato == other.dato && other.beløp == this.beløp
        }
    }

    internal class SkattComposite(
        private val id: UUID,
        private val inntektsopplysninger: List<Skatt>
    ) : Inntektsopplysning(inntektsopplysninger.first()) {

        private val inntekterSisteTreMåneder = inntektsopplysninger.filter { it.erRelevant(3) }

        override fun accept(visitor: InntekthistorikkVisitor) {
            visitor.preVisitSkatt(this, id, dato)
            inntektsopplysninger.forEach { it.accept(visitor) }
            visitor.postVisitSkatt(this, id, dato)
        }

        override fun omregnetÅrsinntekt(skjæringstidspunkt: LocalDate, førsteFraværsdag: LocalDate?) =
            takeIf {
                inntektsopplysninger.any {
                    it.omregnetÅrsinntekt(skjæringstidspunkt, førsteFraværsdag) != null
                        && it.erRelevant(Arbeidsforholdhistorikk.Arbeidsforhold.MAKS_INNTEKT_GAP)
                }
            }

        override fun omregnetÅrsinntekt(): Inntekt {
            return inntekterSisteTreMåneder
                .map(Skatt::omregnetÅrsinntekt)
                .summer()
                .coerceAtLeast(INGEN)
                .div(3)
        }

        override fun rapportertInntekt(dato: LocalDate) =
            takeIf { inntektsopplysninger.any { it.rapportertInntekt(dato) != null } }

        override fun rapportertInntekt(): Inntekt =
            inntektsopplysninger
                .filter { it.erRelevant(12) }
                .map(Skatt::rapportertInntekt)
                .summer()
                .div(12)

        override fun subsumerSykepengegrunnlag(
            subsumsjonObserver: SubsumsjonObserver,
            skjæringstidspunkt: LocalDate,
            organisasjonsnummer: String,
            startdatoArbeidsforhold: LocalDate?,
            forklaring: String?,
            subsumsjon: Subsumsjon?
        ) {
            subsumsjonObserver.`§ 8-28 ledd 3 bokstav a`(
                organisasjonsnummer = organisasjonsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                inntekterSisteTreMåneder = inntekterSisteTreMåneder.subsumsjonsformat(),
                grunnlagForSykepengegrunnlag = omregnetÅrsinntekt()
            )
            subsumsjonObserver.`§ 8-29`(skjæringstidspunkt, omregnetÅrsinntekt(), inntektsopplysninger.subsumsjonsformat(), organisasjonsnummer)
        }

        override fun subsumerArbeidsforhold(
            subsumsjonObserver: SubsumsjonObserver,
            skjæringstidspunkt: LocalDate,
            organisasjonsnummer: String,
            forklaring: String,
            oppfylt: Boolean
        ) {
            subsumsjonObserver.`§ 8-15`(
                skjæringstidspunkt = skjæringstidspunkt,
                organisasjonsnummer = organisasjonsnummer,
                inntekterSisteTreMåneder = inntekterSisteTreMåneder.subsumsjonsformat(),
                forklaring = forklaring,
                oppfylt = oppfylt
            )
        }

        override fun skalErstattesAv(other: Inntektsopplysning): Boolean {
            if (other is SkattComposite) return skalErstattesAv(other)
            return this.inntektsopplysninger.any { it.skalErstattesAv(other) }
        }

        private fun skalErstattesAv(other: SkattComposite) =
            other.inntektsopplysninger.any { skalErstattesAv(it) }

        override fun erSamme(other: Inntektsopplysning): Boolean {
            return other is SkattComposite && this.inntektsopplysninger == other.inntektsopplysninger
        }
    }

    internal class IkkeRapportert(
        private val id: UUID,
        dato: LocalDate,
        private val tidsstempel: LocalDateTime = LocalDateTime.now()
    ) : Inntektsopplysning(dato, 10) {

        override fun accept(visitor: InntekthistorikkVisitor) {
            visitor.visitIkkeRapportert(id, dato, tidsstempel)
        }

        override fun omregnetÅrsinntekt() = INGEN

        override fun rapportertInntekt() = INGEN

        override fun subsumerArbeidsforhold(
            subsumsjonObserver: SubsumsjonObserver,
            skjæringstidspunkt: LocalDate,
            organisasjonsnummer: String,
            forklaring: String,
            oppfylt: Boolean
        ) {
            subsumsjonObserver.`§ 8-15`(
                skjæringstidspunkt = skjæringstidspunkt,
                organisasjonsnummer = organisasjonsnummer,
                inntekterSisteTreMåneder = emptyList(),
                forklaring = forklaring,
                oppfylt = oppfylt
            )
        }

        override fun erSamme(other: Inntektsopplysning): Boolean {
            return other is IkkeRapportert && this.dato == other.dato
        }
    }

    internal sealed class Skatt(
        dato: LocalDate,
        prioritet: Int,
        protected val hendelseId: UUID,
        protected val beløp: Inntekt,
        protected val måned: YearMonth,
        protected val type: Inntekttype,
        protected val fordel: String,
        protected val beskrivelse: String,
        protected val tidsstempel: LocalDateTime = LocalDateTime.now()
    ) : Inntektsopplysning(dato, prioritet) {
        internal enum class Inntekttype {
            LØNNSINNTEKT,
            NÆRINGSINNTEKT,
            PENSJON_ELLER_TRYGD,
            YTELSE_FRA_OFFENTLIGE
        }

        internal fun erRelevant(måneder: Long) = måned.isWithinRangeOf(dato, måneder)

        protected fun skalErstattesAv(other: Skatt) =
            this.dato == other.dato && other.måned == this.måned

        protected fun erSammeSkatteinntekt(other: Skatt) =
            this.dato == other.dato && this.beløp == other.beløp && this.måned == other.måned
                    && this.type == other.type && this.fordel == other.fordel && this.beskrivelse == other.beskrivelse

        internal class Sykepengegrunnlag(
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            måned: YearMonth,
            type: Inntekttype,
            fordel: String,
            beskrivelse: String,
            tidsstempel: LocalDateTime = LocalDateTime.now()
        ) : Skatt(
            dato,
            40,
            hendelseId,
            beløp,
            måned,
            type,
            fordel,
            beskrivelse,
            tidsstempel
        ) {
            override fun accept(visitor: InntekthistorikkVisitor) {
                visitor.visitSkattSykepengegrunnlag(this, dato, hendelseId, beløp, måned, type, fordel, beskrivelse, tidsstempel)
            }

            override fun omregnetÅrsinntekt(skjæringstidspunkt: LocalDate, førsteFraværsdag: LocalDate?) =
                takeIf { this.dato == skjæringstidspunkt && måned.isWithinRangeOf(skjæringstidspunkt, 3) }

            override fun omregnetÅrsinntekt(): Inntekt = beløp

            override fun rapportertInntekt(): Inntekt = error("Sykepengegrunnlag har ikke grunnlag for sammenligningsgrunnlag")

            override fun skalErstattesAv(other: Inntektsopplysning) =
                other is Sykepengegrunnlag && skalErstattesAv(other)

            override fun erSamme(other: Inntektsopplysning): Boolean {
                return other is Sykepengegrunnlag && erSammeSkatteinntekt(other)
            }
        }

        internal class RapportertInntekt(
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            måned: YearMonth,
            type: Inntekttype,
            fordel: String,
            beskrivelse: String,
            tidsstempel: LocalDateTime = LocalDateTime.now()
        ) :
            Skatt(dato, 20, hendelseId, beløp, måned, type, fordel, beskrivelse, tidsstempel) {

            override fun accept(visitor: InntekthistorikkVisitor) {
                visitor.visitSkattRapportertInntekt(
                    this,
                    dato,
                    hendelseId,
                    beløp,
                    måned,
                    type,
                    fordel,
                    beskrivelse,
                    tidsstempel
                )
            }

            override fun rapportertInntekt(dato: LocalDate) =
                takeIf { this.dato == dato && måned.isWithinRangeOf(dato, 12) }

            override fun rapportertInntekt(): Inntekt = beløp

            override fun omregnetÅrsinntekt(): Inntekt = error("Sammenligningsgrunnlag har ikke grunnlag for sykepengegrunnlag")

            override fun skalErstattesAv(other: Inntektsopplysning) =
                other is RapportertInntekt && skalErstattesAv(other)

            override fun erSamme(other: Inntektsopplysning) =
                other is RapportertInntekt && erSammeSkatteinntekt(other)
        }
    }

    internal fun append(block: InnslagBuilder.() -> Unit) {
        leggTil(InnslagBuilder().build(block))
    }

    internal class InnslagBuilder() {
        private val tidsstempel = LocalDateTime.now()
        private val skatt = mutableListOf<Skatt>()
        private var inntektsopplysninger = listOf<Inntektsopplysning>()

        internal fun build(builder: InnslagBuilder.() -> Unit): Innslag {
            apply(builder)
            return Innslag(inntektsopplysninger)
        }

        internal fun addSaksbehandler(dato: LocalDate, hendelseId: UUID, beløp: Inntekt) =
            add(Saksbehandler(UUID.randomUUID(), dato, hendelseId, beløp, tidsstempel))

        internal fun addInntektsmelding(dato: LocalDate, hendelseId: UUID, beløp: Inntekt) =
            add(Inntektsmelding(UUID.randomUUID(), dato, hendelseId, beløp, tidsstempel))

        internal fun addInfotrygd(dato: LocalDate, hendelseId: UUID, beløp: Inntekt) =
            add(Infotrygd(UUID.randomUUID(), dato, hendelseId, beløp, tidsstempel))

        internal fun addSkattSykepengegrunnlag(
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            måned: YearMonth,
            type: Skatt.Inntekttype,
            fordel: String,
            beskrivelse: String
        ) {
            skatt.add(Skatt.Sykepengegrunnlag(dato, hendelseId, beløp, måned, type, fordel, beskrivelse, tidsstempel))
            add(SkattComposite(UUID.randomUUID(), skatt.toList()))
        }

        internal fun addRapportertInntekt(
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            måned: YearMonth,
            type: Skatt.Inntekttype,
            fordel: String,
            beskrivelse: String
        ) {
            skatt.add(Skatt.RapportertInntekt(dato, hendelseId, beløp, måned, type, fordel, beskrivelse, tidsstempel))
            add(SkattComposite(UUID.randomUUID(), skatt.toList()))
        }

        private fun add(opplysning: Inntektsopplysning) {
            inntektsopplysninger = opplysning.lagre(inntektsopplysninger)
        }
    }
}

private fun YearMonth.isWithinRangeOf(dato: LocalDate, måneder: Long) =
    this in YearMonth.from(dato).let { it.minusMonths(måneder)..it.minusMonths(1) }
