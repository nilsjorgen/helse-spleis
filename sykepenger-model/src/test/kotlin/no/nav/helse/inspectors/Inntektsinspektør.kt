package no.nav.helse.inspectors

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID
import no.nav.helse.hendelser.Subsumsjon
import no.nav.helse.person.InntekthistorikkVisitor
import no.nav.helse.person.inntekt.Infotrygd
import no.nav.helse.person.inntekt.Inntektshistorikk
import no.nav.helse.person.inntekt.Inntektsmelding
import no.nav.helse.person.inntekt.Inntektsopplysning
import no.nav.helse.person.inntekt.Saksbehandler
import no.nav.helse.person.inntekt.Skatteopplysning
import no.nav.helse.økonomi.Inntekt

internal class Inntektsinspektør(historikk: Inntektshistorikk) : InntekthistorikkVisitor {
    var inntektTeller = mutableListOf<Int>()

    init {
        historikk.accept(this)
    }

    override fun preVisitInntekthistorikk(inntektshistorikk: Inntektshistorikk) {
        inntektTeller.clear()
    }

    override fun preVisitInnslag(innslag: Inntektshistorikk.Innslag, id: UUID) {
        inntektTeller.add(0)
    }

    override fun visitInntektsmelding(
        inntektsmelding: Inntektsmelding,
        id: UUID,
        dato: LocalDate,
        hendelseId: UUID,
        beløp: Inntekt,
        tidsstempel: LocalDateTime
    ) {
        inntektTeller.add(inntektTeller.removeLast() + 1)
    }
}
