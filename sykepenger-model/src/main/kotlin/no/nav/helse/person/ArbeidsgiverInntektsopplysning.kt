package no.nav.helse.person

import java.time.LocalDate
import no.nav.helse.person.Inntektshistorikk.Inntektsopplysning.Companion.valider
import no.nav.helse.person.Refusjonsopplysning.Companion.merge
import no.nav.helse.person.builders.VedtakFattetBuilder
import no.nav.helse.person.etterlevelse.SubsumsjonObserver
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiode
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.summer
import no.nav.helse.økonomi.Økonomi

internal class ArbeidsgiverInntektsopplysning(
    private val orgnummer: String,
    private val inntektsopplysning: Inntektshistorikk.Inntektsopplysning,
    private val refusjonsopplysninger: List<Refusjonsopplysning>
) {
    internal constructor(orgnummer: String, inntektsopplysning: Inntektshistorikk.Inntektsopplysning): this(orgnummer, inntektsopplysning, emptyList<Refusjonsopplysning>())
    private fun omregnetÅrsinntekt(acc: Inntekt): Inntekt {
        return acc + inntektsopplysning.omregnetÅrsinntekt()
    }

    internal fun harInntektFraAOrdningen() =
        inntektsopplysning is Inntektshistorikk.SkattComposite || inntektsopplysning is Inntektshistorikk.IkkeRapportert

    internal fun gjelder(organisasjonsnummer: String) = organisasjonsnummer == orgnummer

    internal fun accept(visitor: ArbeidsgiverInntektsopplysningVisitor) {
        visitor.preVisitArbeidsgiverInntektsopplysning(this, orgnummer)
        inntektsopplysning.accept(visitor)
        visitor.postVisitArbeidsgiverInntektsopplysning(this, orgnummer)
    }

    private fun overstyr(overstyringer: List<ArbeidsgiverInntektsopplysning>): ArbeidsgiverInntektsopplysning {
        val overstyring = overstyringer.singleOrNull { it.orgnummer == this.orgnummer } ?: return this
        return overstyring.overstyrer(this)
    }

    private fun overstyrer(overstyrt: ArbeidsgiverInntektsopplysning): ArbeidsgiverInntektsopplysning {
        return ArbeidsgiverInntektsopplysning(orgnummer = this.orgnummer, inntektsopplysning = this.inntektsopplysning, refusjonsopplysninger = overstyrt.refusjonsopplysninger.merge(this.refusjonsopplysninger))
    }

    private fun subsummer(subsumsjonObserver: SubsumsjonObserver, opptjening: Opptjening?) {
        inntektsopplysning.subsumerSykepengegrunnlag(subsumsjonObserver, orgnummer, opptjening?.startdatoFor(orgnummer))
    }

    private fun subsummerArbeidsforhold(forklaring: String, oppfylt: Boolean, subsumsjonObserver: SubsumsjonObserver) {
        inntektsopplysning.subsumerArbeidsforhold(subsumsjonObserver, orgnummer, forklaring, oppfylt)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ArbeidsgiverInntektsopplysning) return false
        return orgnummer == other.orgnummer && inntektsopplysning == other.inntektsopplysning
    }

    override fun hashCode(): Int {
        var result = orgnummer.hashCode()
        result = 31 * result + inntektsopplysning.hashCode()
        return result
    }

    internal companion object {
        internal fun List<ArbeidsgiverInntektsopplysning>.deaktiver(deaktiverte: List<ArbeidsgiverInntektsopplysning>, orgnummer: String, forklaring: String, subsumsjonObserver: SubsumsjonObserver) =
            this.fjernInntekt(deaktiverte, orgnummer, forklaring, true, subsumsjonObserver)

        internal fun List<ArbeidsgiverInntektsopplysning>.aktiver(aktiverte: List<ArbeidsgiverInntektsopplysning>, orgnummer: String, forklaring: String, subsumsjonObserver: SubsumsjonObserver) =
            this.fjernInntekt(aktiverte, orgnummer, forklaring, false, subsumsjonObserver)

        // flytter inntekt for *orgnummer* fra *this* til *deaktiverte*
        // aktive.deaktiver(deaktiverte, orgnummer) er direkte motsetning til deaktiverte.deaktiver(aktive, orgnummer)
        private fun List<ArbeidsgiverInntektsopplysning>.fjernInntekt(deaktiverte: List<ArbeidsgiverInntektsopplysning>, orgnummer: String, forklaring: String, oppfylt: Boolean, subsumsjonObserver: SubsumsjonObserver): Pair<List<ArbeidsgiverInntektsopplysning>, List<ArbeidsgiverInntektsopplysning>> {
            val fjernet = this.single { it.orgnummer == orgnummer }
            val aktive = this.filterNot { it.orgnummer == orgnummer }
            fjernet.subsummerArbeidsforhold(forklaring, oppfylt, subsumsjonObserver)
            return aktive to (deaktiverte + fjernet)
        }

        // overskriver eksisterende verdier i *this* med verdier fra *other*,
        // og ignorerer ting i *other* som ikke finnes i *this*
        internal fun List<ArbeidsgiverInntektsopplysning>.overstyrInntekter(opptjening: Opptjening, other: List<ArbeidsgiverInntektsopplysning>, subsumsjonObserver: SubsumsjonObserver) = this
            .map { inntekt -> inntekt.overstyr(other) }
            .also { it.subsummer(subsumsjonObserver, opptjening) }
        internal fun List<ArbeidsgiverInntektsopplysning>.erOverstyrt() = any { it.inntektsopplysning is Inntektshistorikk.Saksbehandler }

        internal fun List<ArbeidsgiverInntektsopplysning>.subsummer(subsumsjonObserver: SubsumsjonObserver, opptjening: Opptjening? = null) {
            subsumsjonObserver.`§ 8-30 ledd 1`(omregnetÅrsinntektPerArbeidsgiver(), omregnetÅrsinntekt())
            forEach { it.subsummer(subsumsjonObserver, opptjening) }
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.build(builder: VedtakFattetBuilder) {
            builder.omregnetÅrsinntektPerArbeidsgiver(omregnetÅrsinntektPerArbeidsgiver())
        }

        private fun List<ArbeidsgiverInntektsopplysning>.omregnetÅrsinntektPerArbeidsgiver() =
            associate { it.orgnummer to it.inntektsopplysning.omregnetÅrsinntekt() }

        internal fun List<ArbeidsgiverInntektsopplysning>.valider(aktivitetslogg: IAktivitetslogg) {
            map { it.inntektsopplysning }.valider(aktivitetslogg)
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.harInntekt(organisasjonsnummer: String) =
            singleOrNull { it.orgnummer == organisasjonsnummer } != null

        internal fun List<ArbeidsgiverInntektsopplysning>.omregnetÅrsinntekt() =
            fold(INGEN) { acc, item -> item.omregnetÅrsinntekt(acc)}

        internal fun List<ArbeidsgiverInntektsopplysning>.sammenligningsgrunnlag(): Inntekt {
            return map { it.inntektsopplysning.rapportertInntekt() }.summer()
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.medInntekt(organisasjonsnummer: String, skjæringstidspunkt: LocalDate, dato: LocalDate, økonomi: Økonomi, arbeidsgiverperiode: Arbeidsgiverperiode?, regler: ArbeidsgiverRegler, subsumsjonObserver: SubsumsjonObserver): Økonomi? {
            return singleOrNull { it.orgnummer == organisasjonsnummer }?.inntektsopplysning?.omregnetÅrsinntekt()?.let { inntekt ->
                økonomi.inntekt(
                    aktuellDagsinntekt = inntekt,
                    dekningsgrunnlag = inntekt.dekningsgrunnlag(dato, regler, subsumsjonObserver),
                    skjæringstidspunkt = skjæringstidspunkt,
                    arbeidsgiverperiode = arbeidsgiverperiode
                )
            }
        }
    }
}
