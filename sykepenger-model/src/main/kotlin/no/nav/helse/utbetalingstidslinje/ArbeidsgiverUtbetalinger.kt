package no.nav.helse.utbetalingstidslinje

import no.nav.helse.hendelser.Periode
import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.Arbeidsgiver
import java.time.LocalDate

internal class ArbeidsgiverUtbetalinger(
    private val tidslinjer: Map<Arbeidsgiver, Utbetalingstidslinje>,
    private val personTidslinje: Utbetalingstidslinje,
    private val periode: Periode,
    private val beregningsdato: LocalDate,
    private val alder: Alder,
    private val arbeidsgiverRegler: ArbeidsgiverRegler,
    private val aktivitetslogg: Aktivitetslogg,
    private val organisasjonsnummer: String,
    private val fødselsnummer: String
) {
    internal lateinit var tidslinjeEngine: MaksimumSykepengedagerfilter

    internal fun beregn() {
        val tidslinjer = this.tidslinjer.values.toList()
        Sykdomsgradfilter(tidslinjer, periode, aktivitetslogg).filter()
        MinimumInntektsfilter(alder, tidslinjer, periode, aktivitetslogg).filter()
        tidslinjeEngine = MaksimumSykepengedagerfilter(alder, arbeidsgiverRegler, periode, aktivitetslogg).also {
            it.filter(tidslinjer, personTidslinje)
        }
        MaksimumUtbetaling(tidslinjer, aktivitetslogg, beregningsdato).betal()
        this.tidslinjer.forEach { (arbeidsgiver, utbetalingstidslinje) ->
            arbeidsgiver.createUtbetaling(
                fødselsnummer,
                organisasjonsnummer,
                utbetalingstidslinje,
                periode.endInclusive,
                aktivitetslogg
            )
        }
    }

}
