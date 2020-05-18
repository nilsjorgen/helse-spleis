package no.nav.helse.utbetalingslinjer

import no.nav.helse.hendelser.Simulering
import no.nav.helse.person.OppdragVisitor
import no.nav.helse.sykdomstidslinje.erHelg
import no.nav.helse.utbetalingstidslinje.genererUtbetalingsreferanse
import java.time.LocalDate
import java.util.*
import kotlin.streams.toList

internal class Oppdrag private constructor(
    private val mottaker: String,
    private val fagområde: Fagområde,
    private val linjer: MutableList<Utbetalingslinje>,
    private var fagsystemId: String,
    private var endringskode: Endringskode,
    private val sisteArbeidsgiverdag: LocalDate?,
    private var nettoBeløp: Int = linjer.sumBy { it.totalbeløp() }
) : MutableList<Utbetalingslinje> by linjer {


    internal constructor(
        mottaker: String,
        fagområde: Fagområde,
        linjer: List<Utbetalingslinje> = listOf(),
        fagsystemId: String = genererUtbetalingsreferanse(UUID.randomUUID()),
        sisteArbeidsgiverdag: LocalDate?
    ) : this(
        mottaker,
        fagområde,
        linjer.toMutableList(),
        fagsystemId,
        Endringskode.NY,
        sisteArbeidsgiverdag
    )

    internal fun accept(visitor: OppdragVisitor) {
        visitor.preVisitOppdrag(this, totalbeløp(), nettoBeløp)
        linjer.forEach { it.accept(visitor) }
        visitor.postVisitOppdrag(this)
    }

    internal fun fagsystemId() = fagsystemId

    internal val førstedato get() = linjer.firstOrNull()?.fom ?: LocalDate.MIN

    internal val sistedato get() = linjer.lastOrNull()?.tom ?: LocalDate.MIN

    internal fun removeUEND() = Oppdrag(
        mottaker,
        fagområde,
        linjer.filter { it.erForskjell() }.toMutableList(),
        fagsystemId,
        endringskode,
        sisteArbeidsgiverdag
    )

    internal fun totalbeløp() = linjerUtenOpphør().sumBy { it.totalbeløp() }

    internal fun nettoBeløp() = nettoBeløp

    internal fun nettoBeløp(tidligere: Oppdrag) {
        nettoBeløp = this.totalbeløp() - tidligere.totalbeløp()
    }

    internal fun linjerUtenOpphør() = filter { !it.erOpphør() }

    internal fun erForskjelligFra(resultat: Simulering.SimuleringResultat): Boolean {
        return dagSatser().zip(dagSatser(resultat, førstedato, sistedato)).any { (oppdrag, simulering) ->
            oppdrag.first != simulering.first || oppdrag.second != simulering.second
        }
    }

    private fun dagSatser() = linjerUtenOpphør().flatMap { linje -> linje.dager().map { it to linje.dagsats } }

    private fun dagSatser(resultat: Simulering.SimuleringResultat, fom: LocalDate, tom: LocalDate) =
        resultat.perioder.flatMap {
            it.utbetalinger.flatMap {
                it.detaljer.flatMap { detalj ->
                    detalj.periode.start.datesUntil(detalj.periode.endInclusive.plusDays(1))
                        .filter { it >= fom && it <= tom }
                        .filter { !it.erHelg() }
                        .map { it to detalj.sats.sats }
                        .toList()
                }
            }
        }

    internal operator fun minus(other: Oppdrag): Oppdrag {
        val tidligere = other.copyWith(other.linjerUtenOpphør())
        return when {
            tidligere.isEmpty() ->
                this
            this.isEmpty() &&
                (this.sisteArbeidsgiverdag == null || this.sisteArbeidsgiverdag < tidligere.sistedato) ->
                deleteAll(tidligere)
            this.isEmpty() && this.sisteArbeidsgiverdag != null && this.sisteArbeidsgiverdag > tidligere.sistedato ->
                this
            this.førstedato > tidligere.sistedato ->
                this
            this.førstedato > tidligere.førstedato ->
                deleted(tidligere)
            this.førstedato < tidligere.førstedato ->
                appended(tidligere)
            this.førstedato == tidligere.førstedato ->
                ghosted(tidligere)
            else ->
                throw IllegalArgumentException("uventet utbetalingslinje forhold")
        }
    }

    private fun deleteAll(tidligere: Oppdrag) = this.also { nåværende ->
        nåværende.kobleTil(tidligere)
        linjer.add(tidligere.last().deletion(tidligere.first().fom))
    }

    private fun appended(tidligere: Oppdrag) = this.also { nåværende ->
        nåværende.kobleTil(tidligere)
        nåværende.first().linkTo(tidligere.last())
        nåværende.zipWithNext { a, b -> b.linkTo(a) }
    }

    private lateinit var tilstand: Tilstand
    private lateinit var sisteLinjeITidligereOppdrag: Utbetalingslinje

    private lateinit var linkTo: Utbetalingslinje

    private fun ghosted(tidligere: Oppdrag, linkTo: Utbetalingslinje = tidligere.last()) =
        this.also { nåværende ->
            this.linkTo = linkTo
            nåværende.kobleTil(tidligere)
            nåværende.kopierLikeLinjer(tidligere)
            nåværende.håndterLengreNåværende(tidligere)
            deletion?.let { this.add(0, it) }
        }

    private fun deleted(tidligere: Oppdrag) = this.also { nåværende ->
        val deletion = nåværende.deletionLinje(tidligere)
        nåværende.appended(tidligere)
        nåværende.add(0, deletion)
    }

    private fun deletionLinje(tidligere: Oppdrag) =
        tidligere.last().deletion(tidligere.førstedato)

    private fun copyWith(linjer: List<Utbetalingslinje>) = Oppdrag(
        mottaker,
        fagområde,
        linjer.toMutableList(),
        fagsystemId,
        endringskode,
        sisteArbeidsgiverdag
    )

    private var deletion: Utbetalingslinje? = null

    private fun kopierLikeLinjer(tidligere: Oppdrag) {
        tilstand = if (tidligere.sistedato > this.sistedato) Slett() else Identisk()
        sisteLinjeITidligereOppdrag = tidligere.last()
        this.zip(tidligere).forEach { (a, b) -> tilstand.forskjell(a, b) }
    }

    private fun håndterLengreNåværende(tidligere: Oppdrag) {
        if (this.size <= tidligere.size) return
        this[tidligere.size].linkTo(linkTo)
        this
            .subList(tidligere.size, this.size)
            .zipWithNext { a, b -> b.linkTo(a) }
    }

    private fun kobleTil(tidligere: Oppdrag) {
        this.fagsystemId = tidligere.fagsystemId
        this.forEach { it.refFagsystemId = tidligere.fagsystemId }
        this.endringskode = Endringskode.ENDR
    }

    internal fun emptied(): Oppdrag =
        Oppdrag(
            mottaker = mottaker,
            fagområde = fagområde,
            fagsystemId = fagsystemId,
            sisteArbeidsgiverdag = sisteArbeidsgiverdag
        )

    private interface Tilstand {
        fun forskjell(
            nåværende: Utbetalingslinje,
            tidligere: Utbetalingslinje
        )
    }

    private inner class Identisk : Tilstand {
        override fun forskjell(
            nåværende: Utbetalingslinje,
            tidligere: Utbetalingslinje
        ) {
            if (nåværende.equals(tidligere)) return nåværende.ghostFrom(tidligere)
            if (nåværende.kunTomForskjelligFra(tidligere)) {
                nåværende.utvidTom(tidligere)
                tilstand = Ny()
                return
            }
            nåværende.linkTo(linkTo)
            linkTo = nåværende
            tilstand = Ny()
        }
    }

    private inner class Slett : Tilstand {
        override fun forskjell(
            nåværende: Utbetalingslinje,
            tidligere: Utbetalingslinje
        ) {
            if (nåværende.equals(tidligere)) return nåværende.ghostFrom(tidligere)
            deletion = sisteLinjeITidligereOppdrag.deletion(tidligere.fom).also { nåværende.linkTo(it) }
            linkTo = nåværende
            tilstand = Ny()
        }
    }

    private inner class Ny : Tilstand {
        override fun forskjell(
            nåværende: Utbetalingslinje,
            tidligere: Utbetalingslinje
        ) {
            nåværende.linkTo(linkTo)
            linkTo = nåværende
        }
    }
}

