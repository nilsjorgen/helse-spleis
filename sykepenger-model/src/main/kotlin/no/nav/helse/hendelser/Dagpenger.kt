package no.nav.helse.hendelser

import no.nav.helse.hendelser.Periode.Companion.slutterEtter
import no.nav.helse.person.Aktivitetslogg
import java.time.LocalDate

class Dagpenger(private val perioder: List<Periode>) {
    internal fun valider(aktivitetslogg: Aktivitetslogg, førsteFraværsdag: LocalDate): Aktivitetslogg {
        if (perioder.slutterEtter(førsteFraværsdag.minusWeeks(4))) {
            aktivitetslogg.warn("Bruker har mottatt dagpenger innenfor 4 uker av første fraværsdag. Kontroller om bruker er dagpengemottaker. Kombinerte ytelser støttes foreløpig ikke av systemet")
        }
        return aktivitetslogg
    }
}
