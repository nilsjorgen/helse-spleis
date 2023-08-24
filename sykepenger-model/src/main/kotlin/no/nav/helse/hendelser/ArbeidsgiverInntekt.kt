package no.nav.helse.hendelser

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID
import no.nav.helse.etterlevelse.SubsumsjonObserver
import no.nav.helse.hendelser.ArbeidsgiverInntekt.MånedligInntekt.Companion.harInntektFor
import no.nav.helse.person.Person
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlag
import no.nav.helse.person.inntekt.Sammenligningsgrunnlag
import no.nav.helse.person.inntekt.SkattSykepengegrunnlag
import no.nav.helse.person.inntekt.Skatteopplysning
import no.nav.helse.person.inntekt.Sykepengegrunnlag
import no.nav.helse.økonomi.Inntekt

class ArbeidsgiverInntekt(
    private val arbeidsgiver: String,
    private val inntekter: List<MånedligInntekt>
) {
    internal fun tilSykepengegrunnlag(skjæringstidspunkt: LocalDate, meldingsreferanseId: UUID) =
        SkattSykepengegrunnlag(
            hendelseId = meldingsreferanseId,
            dato = skjæringstidspunkt,
            inntektsopplysninger = inntekter.map { it.somInntekt(meldingsreferanseId) },
            ansattPerioder = emptyList(),
            tidsstempel = LocalDateTime.now()
        )

    internal fun tilSammenligningsgrunnlag(meldingsreferanseId: UUID) =
        ArbeidsgiverInntektsopplysningForSammenligningsgrunnlag(
            orgnummer = arbeidsgiver,
            inntektsopplysninger = inntekter.map { it.somInntekt(meldingsreferanseId) }
        )

    internal companion object {
        internal fun List<ArbeidsgiverInntekt>.avklarSykepengegrunnlag(
            hendelse: IAktivitetslogg,
            person: Person,
            rapporterteArbeidsforhold: Map<String, SkattSykepengegrunnlag>,
            skjæringstidspunkt: LocalDate,
            sammenligningsgrunnlag: Sammenligningsgrunnlag,
            meldingsreferanseId: UUID,
            subsumsjonObserver: SubsumsjonObserver
        ): Sykepengegrunnlag {
            val rapporterteInntekter = this.associateBy({ it.arbeidsgiver }) { it.tilSykepengegrunnlag(skjæringstidspunkt, meldingsreferanseId) }
            // tar utgangspunktet i inntekter som bare stammer fra orgnr vedkommende har registrert arbeidsforhold
            val inntekterMedOpptjening = rapporterteArbeidsforhold.mapValues { (orgnummer, ikkeRapportert) -> ikkeRapportert + rapporterteInntekter[orgnummer] }
            return person.avklarSykepengegrunnlag(
                hendelse,
                skjæringstidspunkt,
                sammenligningsgrunnlag,
                inntekterMedOpptjening,
                subsumsjonObserver
            )
        }

        internal fun List<ArbeidsgiverInntekt>.harInntektFor(orgnummer: String, måned: YearMonth) =
            this.any { it.arbeidsgiver == orgnummer && it.inntekter.harInntektFor(måned) }

        internal fun List<ArbeidsgiverInntekt>.antallMåneder() =
            MånedligInntekt.antallMåneder(flatMap { it.inntekter })
    }

    class MånedligInntekt(
        private val yearMonth: YearMonth,
        private val inntekt: Inntekt,
        private val type: Inntekttype,
        private val fordel: String,
        private val beskrivelse: String
    ) {
        internal fun somInntekt(meldingsreferanseId: UUID) = Skatteopplysning(
            meldingsreferanseId,
            inntekt,
            yearMonth,
            enumValueOf(type.name),
            fordel,
            beskrivelse
        )

        companion object {
            internal fun List<MånedligInntekt>.harInntektFor(måned: YearMonth) = this.any { it.yearMonth == måned }

            internal fun antallMåneder(inntekter: List<MånedligInntekt>): Long {
                if (inntekter.isEmpty()) return 0
                return ChronoUnit.MONTHS.between(inntekter.minMonth(), inntekter.maxMonth()) + 1
            }

            private fun List<MånedligInntekt>.minMonth() = minOfOrNull { it.yearMonth }
            private fun List<MånedligInntekt>.maxMonth() = maxOfOrNull { it.yearMonth }
        }

        enum class Inntekttype {
            LØNNSINNTEKT,
            NÆRINGSINNTEKT,
            PENSJON_ELLER_TRYGD,
            YTELSE_FRA_OFFENTLIGE
        }
    }
}

