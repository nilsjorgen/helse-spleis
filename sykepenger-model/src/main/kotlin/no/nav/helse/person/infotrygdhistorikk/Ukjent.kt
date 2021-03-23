package no.nav.helse.person.infotrygdhistorikk

import no.nav.helse.hendelser.Periode
import no.nav.helse.person.IAktivitetslogg
import no.nav.helse.person.InfotrygdhistorikkVisitor

internal class Ukjent(periode: Periode) : Infotrygdperiode(periode) {
    override fun valider(aktivitetslogg: IAktivitetslogg, periode: Periode) {
        if (endInclusive < periode.start.minusDays(18)) return
        aktivitetslogg.warn("Perioden er lagt inn i Infotrygd, men ikke utbetalt. Fjern fra Infotrygd hvis det utbetales via speil.")
    }

    override fun accept(visitor: InfotrygdhistorikkVisitor) {
        visitor.visitInfotrygdhistorikkFerieperiode(this)
    }
}
