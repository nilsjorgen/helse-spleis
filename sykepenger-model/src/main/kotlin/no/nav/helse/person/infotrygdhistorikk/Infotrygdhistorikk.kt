package no.nav.helse.person.infotrygdhistorikk

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.dto.deserialisering.InfotrygdhistorikkInnDto
import no.nav.helse.dto.serialisering.InfotrygdhistorikkUtDto
import no.nav.helse.etterlevelse.SubsumsjonObserver
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.til
import no.nav.helse.person.InfotrygdhistorikkVisitor
import no.nav.helse.person.Person
import no.nav.helse.person.Vedtaksperiode
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.utbetalingshistorikk
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.sykdomstidslinje.SykdomshistorikkHendelse
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverperiodeBuilder
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverperiodeMediator
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiodeteller
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje

internal class Infotrygdhistorikk private constructor(
    private val elementer: MutableList<InfotrygdhistorikkElement>
) {
    private val siste get() = elementer.first()

    constructor() : this(mutableListOf())

    internal companion object {
        private fun oppfriskningsperiode(tidligsteDato: LocalDate) =
            tidligsteDato.minusYears(4) til LocalDate.now()

        internal fun gjenopprett(dto: InfotrygdhistorikkInnDto): Infotrygdhistorikk {
            return Infotrygdhistorikk(
                elementer = dto.elementer.map { InfotrygdhistorikkElement.gjenopprett(it) }.toMutableList()
            )
        }
    }

    internal fun valider(
        aktivitetslogg: IAktivitetslogg,
        periode: Periode,
        skjæringstidspunkt: LocalDate,
        orgnummer: String
    ): Boolean {
        if (!harHistorikk()) return true
        return siste.valider(aktivitetslogg, periode, orgnummer)
    }

    internal fun oppfriskNødvendig(aktivitetslogg: IAktivitetslogg, tidligsteDato: LocalDate) {
        oppfrisk(aktivitetslogg, tidligsteDato)
    }

    internal fun oppfrisk(aktivitetslogg: IAktivitetslogg, tidligsteDato: LocalDate) {
        utbetalingshistorikk(aktivitetslogg, oppfriskningsperiode(tidligsteDato))
    }

    internal fun utbetalingstidslinje(): Utbetalingstidslinje {
        if (!harHistorikk()) return Utbetalingstidslinje()
        return siste.utbetalingstidslinje()
    }

    internal fun skjæringstidspunkt(organisasjonsnummer: String, periode: Periode, tidslinje: Sykdomstidslinje): LocalDate {
        return Sykdomstidslinje.sisteRelevanteSkjæringstidspunktForPerioden(periode, listOf(tidslinje) + listOf(sykdomstidslinje(organisasjonsnummer))) ?: periode.start
    }

    internal fun skjæringstidspunkt(periode: Periode, tidslinjer: List<Sykdomstidslinje>): LocalDate {
        return Sykdomstidslinje.sisteRelevanteSkjæringstidspunktForPerioden(periode, tidslinjer + listOf(sykdomstidslinje())) ?: periode.start
    }

    internal fun skjæringstidspunkter(tidslinjer: List<Sykdomstidslinje>): List<LocalDate> {
        return Sykdomstidslinje.skjæringstidspunkter(tidslinjer + listOf(sykdomstidslinje()))
    }

    private fun sykdomstidslinje(orgnummer: String): Sykdomstidslinje {
        if (!harHistorikk()) return Sykdomstidslinje()
        return siste.sykdomstidslinje(orgnummer)
    }

    private fun sykdomstidslinje(): Sykdomstidslinje {
        if (!harHistorikk()) return Sykdomstidslinje()
        return siste.sykdomstidslinje()
    }

    internal fun oppdaterHistorikk(element: InfotrygdhistorikkElement): Boolean {
        if (harHistorikk() && element.erstatter(siste)) return false
        elementer.add(0, element)
        return true
    }

    internal fun harEndretHistorikk(utbetaling: Utbetaling): Boolean {
        if (!harHistorikk()) return false
        val sisteElementSomFantesFørUtbetaling = elementer.firstOrNull{
            it.erEldreEnn(utbetaling)
        } ?: return true
        return siste.erEndretUtbetaling(sisteElementSomFantesFørUtbetaling)
    }

    internal fun tøm() {
        if (!harHistorikk()) return
        val nyeste = siste
        elementer.clear()
        elementer.add(nyeste)
    }

    internal fun accept(visitor: InfotrygdhistorikkVisitor) {
        visitor.preVisitInfotrygdhistorikk()
        elementer.forEach { it.accept(visitor) }
        visitor.postVisitInfotrygdhistorikk()
    }

    internal fun build(
        organisasjonsnummer: String,
        sykdomstidslinje: Sykdomstidslinje,
        builder: ArbeidsgiverperiodeMediator,
        subsumsjonObserver: SubsumsjonObserver?,
        hendelseskilde: SykdomshistorikkHendelse.Hendelseskilde? = null
    ) {
        val teller = Arbeidsgiverperiodeteller.NormalArbeidstaker
        val arbeidsgiverperiodeBuilder = ArbeidsgiverperiodeBuilder(teller, builder, subsumsjonObserver)
        if (!harHistorikk()) return sykdomstidslinje.accept(arbeidsgiverperiodeBuilder)
        siste.build(organisasjonsnummer, sykdomstidslinje, teller, arbeidsgiverperiodeBuilder, hendelseskilde)
    }

    internal fun buildUtbetalingstidslinje(
        organisasjonsnummer: String,
        sykdomstidslinje: Sykdomstidslinje,
        builder: ArbeidsgiverperiodeMediator,
        subsumsjonObserver: SubsumsjonObserver
    ) {
        val infotrygdkilde = SykdomshistorikkHendelse.Hendelseskilde("Infotrygdhistorikk", UUID.randomUUID(), LocalDateTime.now())
        val dekoratør = if (harHistorikk()) InfotrygdUtbetalingstidslinjedekoratør(builder, sykdomstidslinje.periode()!!, siste.betaltePerioder(organisasjonsnummer), infotrygdkilde) else builder
        build(organisasjonsnummer, sykdomstidslinje, dekoratør, subsumsjonObserver, infotrygdkilde)
    }


    internal fun harHistorikk() = elementer.isNotEmpty()
    internal fun harUtbetaltI(periode: Periode): Boolean {
        if (!harHistorikk()) return false
        return siste.harUtbetaltI(periode)
    }

    internal fun harFerieI(periode: Periode): Boolean {
        if (!harHistorikk()) return false
        return siste.harFerieI(periode)
    }

    internal fun dto() = InfotrygdhistorikkUtDto(
        elementer = this.elementer.map { it.dto() }
    )

    internal fun overlappendeInfotrygdperioder(person: Person, alleVedtaksperioder: List<Vedtaksperiode>) {
        if (!harHistorikk()) return
        siste.overlappendeInfotrygdperioder(person, alleVedtaksperioder)
    }

    fun loggOverlappendeUtbetaling(utbetaling: Utbetaling, aktørId: String, fnr: String, vedtaksperiodeId: UUID) {
        if (!harHistorikk()) return
        siste.loggOverlappendeUtbetaling(utbetaling, aktørId, fnr, vedtaksperiodeId)
    }
}
