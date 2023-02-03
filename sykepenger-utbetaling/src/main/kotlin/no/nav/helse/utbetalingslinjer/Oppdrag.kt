package no.nav.helse.utbetalingslinjer

import OverføringsinformasjonPort
import SimuleringPort
import java.time.LocalDate
import java.time.LocalDate.MIN
import java.time.LocalDateTime
import java.util.*
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.SimuleringResultat
import no.nav.helse.hendelser.somPeriode
import no.nav.helse.hendelser.til
import no.nav.helse.nesteDag
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_OS_1
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_OS_2
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_OS_3
import no.nav.helse.person.aktivitetslogg.Aktivitetskontekst
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.SpesifikkKontekst
import no.nav.helse.utbetalingslinjer.Oppdragstatus.AVVIST
import no.nav.helse.utbetalingslinjer.Oppdragstatus.FEIL

class Oppdrag private constructor(
    private val mottaker: String,
    private val fagområde: Fagområde,
    private val linjer: MutableList<Utbetalingslinje>,
    private var fagsystemId: String,
    private var endringskode: Endringskode,
    private val sisteArbeidsgiverdag: LocalDate?,
    private var nettoBeløp: Int = linjer.sumOf { it.totalbeløp() },
    private var overføringstidspunkt: LocalDateTime? = null,
    private var avstemmingsnøkkel: Long? = null,
    private var status: Oppdragstatus? = null,
    private val tidsstempel: LocalDateTime,
    private var erSimulert: Boolean = false,
    private var simuleringsResultat: SimuleringResultat? = null
) : MutableList<Utbetalingslinje> by linjer, Aktivitetskontekst {
    companion object {
        fun periode(vararg oppdrag: Oppdrag): Periode? {
            return oppdrag
                .mapNotNull { it.linjeperiode ?: it.sisteArbeidsgiverdag?.nesteDag?.somPeriode() }
                .takeIf(List<*>::isNotEmpty)
                ?.reduce(Periode::plus)
        }

        fun List<Oppdrag>.trekkerTilbakePenger() = sumOf { it.nettoBeløp() } < 0

        fun stønadsdager(vararg oppdrag: Oppdrag): Int {
            return Utbetalingslinje.stønadsdager(oppdrag.toList().flatten())
        }

        fun synkronisert(vararg oppdrag: Oppdrag): Boolean {
            val endrede = oppdrag.filter { it.harUtbetalinger() }
            return endrede.all { it.status == endrede.first().status }
        }

        fun ingenFeil(vararg oppdrag: Oppdrag) = oppdrag.none { it.status in listOf(AVVIST, FEIL) }
        fun kanIkkeForsøkesPåNy(vararg oppdrag: Oppdrag) = oppdrag.any { it.status == AVVIST }
        fun ferdigOppdrag(
            mottaker: String,
            from: Fagområde,
            utbetalingslinjer: List<Utbetalingslinje>,
            fagsystemId: String,
            endringskode: Endringskode,
            sisteArbeidsgiverdag: LocalDate?,
            nettoBeløp: Int,
            overføringstidspunkt: LocalDateTime?,
            avstemmingsnøkkel: Long?,
            status: Oppdragstatus?,
            tidsstempel: LocalDateTime,
            erSimulert: Boolean,
            simuleringResultat: SimuleringResultat?
        ): Oppdrag = Oppdrag(
            mottaker = mottaker,
            fagområde = from,
            linjer = utbetalingslinjer.toMutableList(),
            fagsystemId = fagsystemId,
            endringskode = endringskode,
            sisteArbeidsgiverdag = sisteArbeidsgiverdag,
            nettoBeløp = nettoBeløp,
            overføringstidspunkt = overføringstidspunkt,
            avstemmingsnøkkel = avstemmingsnøkkel,
            status = status,
            tidsstempel = tidsstempel,
            erSimulert = erSimulert,
            simuleringsResultat = simuleringResultat
        )
    }

    private val overlapperiode get() = when {
        sisteArbeidsgiverdag == null && isEmpty() -> null
        sisteArbeidsgiverdag == null -> linjeperiode
        else -> linjeperiode?.oppdaterFom(sisteArbeidsgiverdag)
    }
    private val linjeperiode get() = firstOrNull()?.let { (it.datoStatusFom() ?: it.fom) til last().tom }

    constructor(
        mottaker: String,
        fagområde: Fagområde,
        linjer: List<Utbetalingslinje> = listOf(),
        fagsystemId: String = genererUtbetalingsreferanse(UUID.randomUUID()),
        sisteArbeidsgiverdag: LocalDate? = null
    ) : this(
        mottaker,
        fagområde,
        linjer.toMutableList(),
        fagsystemId,
        Endringskode.NY,
        sisteArbeidsgiverdag,
        tidsstempel = LocalDateTime.now()
    )

    fun accept(visitor: OppdragVisitor) {
        visitor.preVisitOppdrag(
            this,
            fagområde,
            fagsystemId,
            mottaker,
            sisteArbeidsgiverdag,
            stønadsdager(),
            totalbeløp(),
            nettoBeløp,
            tidsstempel,
            endringskode,
            avstemmingsnøkkel,
            status,
            overføringstidspunkt,
            erSimulert,
            simuleringsResultat
        )
        linjer.forEach { it.accept(visitor) }
        visitor.postVisitOppdrag(
            this,
            fagområde,
            fagsystemId,
            mottaker,
            sisteArbeidsgiverdag,
            stønadsdager(),
            totalbeløp(),
            nettoBeløp,
            tidsstempel,
            endringskode,
            avstemmingsnøkkel,
            status,
            overføringstidspunkt,
            erSimulert,
            simuleringsResultat
        )
    }

    fun fagsystemId() = fagsystemId

    private operator fun contains(other: Oppdrag) = this.tilhører(other) || this.overlapperMed(other) || sammenhengendeUtbetalingsperiode(other)

    fun tilhører(other: Oppdrag) = this.fagsystemId == other.fagsystemId && this.fagområde == other.fagområde
    private fun overlapperMed(other: Oppdrag) =
        this.overlapperiode?.let { other.overlapperiode?.overlapperMed(it) } ?: false

    private fun sammenhengendeUtbetalingsperiode(other: Oppdrag) =
        this.sisteArbeidsgiverdag != null && this.sisteArbeidsgiverdag == other.sisteArbeidsgiverdag

    fun overfør(
        aktivitetslogg: IAktivitetslogg,
        maksdato: LocalDate?,
        saksbehandler: String
    ) {
        if (!harUtbetalinger()) return aktivitetslogg.info("Overfører ikke oppdrag uten endring for fagområde=$fagområde med fagsystemId=$fagsystemId")
        check(endringskode != Endringskode.UEND)
        check(status != Oppdragstatus.AKSEPTERT)
        aktivitetslogg.kontekst(this)
        aktivitetslogg.behov(Behovtype.Utbetaling, "Trenger å sende utbetaling til Oppdrag", behovdetaljer(saksbehandler, maksdato))
    }

    fun simuler(aktivitetslogg: IAktivitetslogg, maksdato: LocalDate, saksbehandler: String) {
        if (!harUtbetalinger()) return aktivitetslogg.info("Simulerer ikke oppdrag uten endring fagområde=$fagområde med fagsystemId=$fagsystemId")
        check(endringskode != Endringskode.UEND)
        check(status == null)
        aktivitetslogg.kontekst(this)
        aktivitetslogg.behov(Behovtype.Simulering, "Trenger simulering fra Oppdragssystemet", behovdetaljer(saksbehandler, maksdato))
    }

    override fun toSpesifikkKontekst() = SpesifikkKontekst("Oppdrag", mapOf("fagsystemId" to fagsystemId))

    private fun behovdetaljer(saksbehandler: String, maksdato: LocalDate?): MutableMap<String, Any> {
        return mutableMapOf(
            "mottaker" to mottaker,
            "fagområde" to "$fagområde",
            "linjer" to kopierKunLinjerMedEndring().map(Utbetalingslinje::toHendelseMap),
            "fagsystemId" to fagsystemId,
            "endringskode" to "$endringskode",
            "saksbehandler" to saksbehandler
        ).apply {
            maksdato?.let {
                put("maksdato", maksdato.toString())
            }
        }
    }

    fun totalbeløp() = linjerUtenOpphør().sumOf { it.totalbeløp() }
    fun stønadsdager() = sumOf { it.stønadsdager() }

    fun nettoBeløp() = nettoBeløp

    private fun nettoBeløp(tidligere: Oppdrag) {
        nettoBeløp = this.totalbeløp() - tidligere.totalbeløp()
    }

    fun harUtbetalinger() = any(Utbetalingslinje::erForskjell)

    fun erRelevant(fagsystemId: String, fagområde: Fagområde) =
        this.fagsystemId == fagsystemId && this.fagområde == fagområde

    fun valider(simulering: SimuleringPort) =
        simulering.valider(this)

    private fun kopierKunLinjerMedEndring() = kopierMed(filter(Utbetalingslinje::erForskjell))

    private fun kopierUtenOpphørslinjer() = kopierMed(linjerUtenOpphør())

    fun linjerUtenOpphør() = filter { !it.erOpphør() }

    fun annuller(aktivitetslogg: IAktivitetslogg): Oppdrag {
        return tomtOppdrag().minus(this, aktivitetslogg)
    }

    private fun tomtOppdrag(): Oppdrag =
        Oppdrag(
            mottaker = mottaker,
            fagområde = fagområde,
            fagsystemId = fagsystemId,
            sisteArbeidsgiverdag = sisteArbeidsgiverdag
        )

    operator fun plus(other: Oppdrag): Oppdrag {
        check(none { linje -> other.any { it.periode.overlapperMed(linje.periode) } }) {
            "ikke støttet: kan ikke overlappe med annet oppdrag"
        }
        if (this.fomHarFlyttetSegFremover(other)) return other + this
        return kopierMed((slåSammenOppdrag(other)))
    }

    private fun slåSammenOppdrag(other: Oppdrag): List<Utbetalingslinje> {
        if (this.isEmpty()) return other.linjer
        if (other.isEmpty()) return this.linjer
        val sisteLinje = this.first()
        val første = other.first()
        val mellomlinje = sisteLinje.slåSammenLinje(første) ?: return this.linjer + other.linjer
        return this.linjer.dropLast(1) + listOf(mellomlinje) + other.linjer.drop(1)
    }

    fun minus(eldre: Oppdrag, aktivitetslogg: IAktivitetslogg): Oppdrag {
        // Vi ønsker ikke å forlenge et oppdrag vi ikke overlapper med, eller et tomt oppdrag
        if (harIngenKoblingTilTidligereOppdrag(eldre)) return this
        // overtar fagsystemId fra tidligere Oppdrag uten utbetaling, gitt at det er samme arbeidsgiverperiode
        if (eldre.erTomt()) return this.also { this.fagsystemId = eldre.fagsystemId }
        return when {
            // om man trekker fra et utbetalt oppdrag med et tomt oppdrag medfører det et oppdrag som opphører (les: annullerer) hele fagsystemIDen
            erTomt() -> annulleringsoppdrag(eldre)
            // "fom" kan flytte seg fremover i tid dersom man, eksempelvis, revurderer en utbetalt periode til å starte med ikke-utbetalte dager (f.eks. ferie)
            eldre.ingenUtbetalteDager() -> {
                aktivitetslogg.varsel(RV_OS_1)
                kjørFrem(eldre)
            }
            fomHarFlyttetSegFremover(eldre.kopierUtenOpphørslinjer()) -> {
                aktivitetslogg.varsel(RV_OS_2)
                returførOgKjørFrem(eldre.kopierUtenOpphørslinjer())
            }
            // utbetaling kan endres til å starte tidligere, eksempelvis via revurdering der feriedager egentlig er sykedager
            fomHarFlyttetSegBakover(eldre.kopierUtenOpphørslinjer()) -> {
                aktivitetslogg.varsel(RV_OS_2)
                kjørFrem(eldre.kopierUtenOpphørslinjer())
            }
            // fom er lik, men endring kan oppstå overalt ellers
            else -> endre(eldre.kopierUtenOpphørslinjer(), aktivitetslogg)
        }.also { it.nettoBeløp(eldre) }
    }

    private fun harIngenKoblingTilTidligereOppdrag(eldre: Oppdrag) = (eldre.erTomt() && !sammenhengendeUtbetalingsperiode(eldre)) || this !in eldre

    private fun ingenUtbetalteDager() = linjerUtenOpphør().isEmpty()

    private fun erTomt() = this.isEmpty()

    // Vi har oppdaget utbetalingsdager tidligere i tidslinjen
    private fun fomHarFlyttetSegBakover(eldre: Oppdrag) = this.first().fom < eldre.first().fom

    // Vi har endret tidligere utbetalte dager til ikke-utbetalte dager i starten av tidslinjen
    private fun fomHarFlyttetSegFremover(eldre: Oppdrag) = this.first().fom > eldre.first().fom

    // man opphører (annullerer) et annet oppdrag ved å lage en opphørslinje som dekker hele perioden som er utbetalt
    private fun annulleringsoppdrag(tidligere: Oppdrag) = this.also { nåværende ->
        nåværende.kobleTil(tidligere)
        linjer.add(tidligere.last().opphørslinje(tidligere.first().fom))
    }

    // når man oppretter en NY linje med dato-intervall "(a, b)" vil oppdragsystemet
    // automatisk opphøre alle eventuelle linjer med fom > b.
    //
    // Eksempel:
    // Oppdrag 1: 5. januar til 31. januar (linje 1)
    // Oppdrag 2: 1. januar til 10. januar
    // Fordi linje "1. januar - 10. januar" opprettes som NY, medfører dette at oppdragsystemet opphører 11. januar til 31. januar automatisk
    private fun kjørFrem(tidligere: Oppdrag) = this.also { nåværende ->
        nåværende.kobleTil(tidligere)
        nåværende.first().kobleTil(tidligere.last())
        nåværende.zipWithNext { a, b -> b.kobleTil(a) }
    }
    private lateinit var tilstand: Tilstand
    private lateinit var sisteLinjeITidligereOppdrag: Utbetalingslinje

    private lateinit var linkTo: Utbetalingslinje

    // forsøker så langt det lar seg gjøre å endre _siste_ linje, dersom mulig *)
    // ellers lager den NY linjer fra og med linja før endringen oppstod
    // *) en linje kan endres dersom "tom"-dato eller grad er eneste forskjell
    //    ulik dagsats eller fom-dato medfører enten at linjen får status OPPH, eller at man overskriver
    //    ved å sende NY linjer
    private fun endre(avtroppendeOppdrag: Oppdrag, aktivitetslogg: IAktivitetslogg) =
        this.also { påtroppendeOppdrag ->
            this.linkTo = avtroppendeOppdrag.last()
            påtroppendeOppdrag.kobleTil(avtroppendeOppdrag)
            påtroppendeOppdrag.kopierLikeLinjer(avtroppendeOppdrag, aktivitetslogg)
            påtroppendeOppdrag.håndterLengreNåværende(avtroppendeOppdrag)
            if (!påtroppendeOppdrag.last().erForskjell()) påtroppendeOppdrag.endringskode = Endringskode.UEND
        }

    // når man oppretter en NY linje vil Oppdragsystemet IKKE ta stilling til periodene FØR.
    // Man må derfor eksplisitt opphøre evt. perioder tidligere, som i praksis vil medføre at
    // oppdraget kjøres tilbake, så fremover
    private fun returførOgKjørFrem(tidligere: Oppdrag) = this.also { nåværende ->
        val deletion = nåværende.opphørOppdrag(tidligere)
        nåværende.kjørFrem(tidligere)
        nåværende.add(0, deletion)
    }

    private fun opphørTidligereLinjeOgOpprettNy(nåværende: Utbetalingslinje, tidligere: Utbetalingslinje, aktivitetslogg: IAktivitetslogg) {
        linkTo = tidligere
        add(this.indexOf(nåværende), tidligere.opphørslinje(tidligere.fom))
        nåværende.kobleTil(linkTo)
        tilstand = Ny()
        aktivitetslogg.varsel(RV_OS_3)
    }

    private fun opphørOppdrag(tidligere: Oppdrag) =
        tidligere.last().opphørslinje(tidligere.first().fom)

    private fun kopierMed(linjer: List<Utbetalingslinje>) = Oppdrag(
        mottaker,
        fagområde,
        linjer.toMutableList(),
        fagsystemId,
        endringskode,
        sisteArbeidsgiverdag,
        overføringstidspunkt = overføringstidspunkt,
        avstemmingsnøkkel = avstemmingsnøkkel,
        status = status,
        tidsstempel = tidsstempel
    )

    private fun kopierLikeLinjer(tidligere: Oppdrag, aktivitetslogg: IAktivitetslogg) {
        tilstand = if (tidligere.last().tom > this.last().tom) Slett() else Identisk()
        sisteLinjeITidligereOppdrag = tidligere.last()
        this.zip(tidligere).forEach { (a, b) -> tilstand.håndterForskjell(a, b, aktivitetslogg) }
    }

    private fun håndterLengreNåværende(tidligere: Oppdrag) {
        if (this.size <= tidligere.size) return
        this[tidligere.size].kobleTil(linkTo)
        this
            .subList(tidligere.size, this.size)
            .zipWithNext { a, b -> b.kobleTil(a) }
    }

    private fun kobleTil(tidligere: Oppdrag) {
        this.fagsystemId = tidligere.fagsystemId
        this.forEach { it.refFagsystemId = tidligere.fagsystemId }
        this.endringskode = Endringskode.ENDR
    }

    private fun håndterUlikhet(nåværende: Utbetalingslinje, tidligere: Utbetalingslinje, aktivitetslogg: IAktivitetslogg) {
        when {
            nåværende.kanEndreEksisterendeLinje(tidligere, sisteLinjeITidligereOppdrag) -> nåværende.endreEksisterendeLinje(tidligere)
            nåværende.skalOpphøreOgErstatte(tidligere, sisteLinjeITidligereOppdrag) -> opphørTidligereLinjeOgOpprettNy(nåværende, tidligere, aktivitetslogg)
            else -> opprettNyLinje(nåværende)
        }
    }

    private fun opprettNyLinje(nåværende: Utbetalingslinje) {
        nåværende.kobleTil(linkTo)
        linkTo = nåværende
        tilstand = Ny()
    }

    fun toHendelseMap() = mapOf(
        "mottaker" to mottaker,
        "fagområde" to "$fagområde",
        "linjer" to map(Utbetalingslinje::toHendelseMap),
        "fagsystemId" to fagsystemId,
        "endringskode" to "$endringskode",
        "sisteArbeidsgiverdag" to sisteArbeidsgiverdag,
        "tidsstempel" to tidsstempel,
        "nettoBeløp" to nettoBeløp,
        "stønadsdager" to stønadsdager(),
        "avstemmingsnøkkel" to avstemmingsnøkkel?.let { "$it" },
        "status" to status?.let { "$it" },
        "overføringstidspunkt" to overføringstidspunkt,
        "fom" to (linjeperiode?.start ?: MIN),
        "tom" to (linjeperiode?.endInclusive ?: MIN),
        "simuleringsResultat" to simuleringsResultat?.toMap()
    )

    fun lagreOverføringsinformasjon(hendelse: OverføringsinformasjonPort) {
        if (!hendelse.erRelevant(fagsystemId)) return
        if (this.avstemmingsnøkkel == null) this.avstemmingsnøkkel = hendelse.avstemmingsnøkkel
        if (this.overføringstidspunkt == null) this.overføringstidspunkt = hendelse.overføringstidspunkt
        this.status = hendelse.status
    }

    fun håndter(simulering: SimuleringPort) {
        if (!simulering.erRelevantFor(fagområde, fagsystemId)) return
        this.erSimulert = true
        this.simuleringsResultat = simulering.simuleringResultat
    }

    fun erKlarForGodkjenning() = !harUtbetalinger() || erSimulert

    private interface Tilstand {
        fun håndterForskjell(nåværende: Utbetalingslinje, tidligere: Utbetalingslinje, aktivitetslogg: IAktivitetslogg)
    }

    private inner class Identisk : Tilstand {
         override fun håndterForskjell(nåværende: Utbetalingslinje, tidligere: Utbetalingslinje, aktivitetslogg: IAktivitetslogg) {
            if (nåværende == tidligere) return nåværende.markerUendret(tidligere)
            håndterUlikhet(nåværende, tidligere, aktivitetslogg)
        }
    }

    private inner class Slett : Tilstand {
        override fun håndterForskjell(nåværende: Utbetalingslinje, tidligere: Utbetalingslinje, aktivitetslogg: IAktivitetslogg) {
            if (nåværende == tidligere) {
                if (nåværende == last()) return nåværende.kobleTil(linkTo)
                return nåværende.markerUendret(tidligere)
            }
            håndterUlikhet(nåværende, tidligere, aktivitetslogg)
        }
    }

    private inner class Ny : Tilstand {
        override fun håndterForskjell(nåværende: Utbetalingslinje, tidligere: Utbetalingslinje, aktivitetslogg: IAktivitetslogg) {
            nåværende.kobleTil(linkTo)
            linkTo = nåværende
        }
    }
}
