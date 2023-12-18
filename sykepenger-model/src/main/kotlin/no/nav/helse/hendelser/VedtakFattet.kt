package no.nav.helse.hendelser

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Avsender.SAKSBEHANDLER
import no.nav.helse.hendelser.Avsender.SYSTEM
import no.nav.helse.hendelser.utbetaling.Utbetalingsavgjørelse
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.utbetalingslinjer.Utbetaling.Vurdering

class VedtakFattet(
    meldingsreferanseId: UUID,
    fødselsnummer: String,
    aktørId: String,
    organisasjonsnummer: String,
    private val vedtaksperiodeId: UUID,
    private val utbetalingId: UUID,
    private val saksbehandlerIdent: String?,
    private val saksbehandlerEpost: String?,
    private val vedtakFattetTidspunkt: LocalDateTime,
) : ArbeidstakerHendelse(meldingsreferanseId, fødselsnummer, aktørId, organisasjonsnummer, Aktivitetslogg()), Utbetalingsavgjørelse {

    init {
        listOfNotNull(saksbehandlerIdent, saksbehandlerEpost).size.let {
            check(it == 0 || it == 2) { "Enten må både ident & epost for saksebandler settes, eller ingen av delene." }
        }
    }

    override fun godkjent() = true
    override fun avgjørelsestidspunkt() = vedtakFattetTidspunkt
    override fun automatisert() = saksbehandlerIdent == null
    override fun vurdering() = Vurdering(
        godkjent = godkjent(),
        ident = saksbehandlerIdent ?: "Automatisk behandlet",
        epost = saksbehandlerEpost ?: "tbd@nav.no",
        tidspunkt = vedtakFattetTidspunkt,
        automatiskBehandling = automatisert()
    )
    override fun relevantVedtaksperiode(id: UUID) = vedtaksperiodeId == id
    override fun relevantUtbetaling(id: UUID) = utbetalingId == id

    override fun innsendt() = vedtakFattetTidspunkt
    override fun avsender() = if (automatisert()) SYSTEM else SAKSBEHANDLER
}
