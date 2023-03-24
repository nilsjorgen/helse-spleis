package no.nav.helse.spleis.meldinger.model

import no.nav.helse.hendelser.ForkastSykmeldingsperioder
import no.nav.helse.hendelser.til
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.spleis.IHendelseMediator

internal class ForkastSykmeldingsperioderMessage(packet: JsonMessage) : HendelseMessage(packet) {

    private val aktørId = packet["aktørId"].asText()
    private val organisasjonsnummer = packet["organisasjonsnummer"].asText()
    private val periode = packet["fom"].asLocalDate() til packet["tom"].asLocalDate()
    override val fødselsnummer: String = packet["fødselsnummer"].asText()

    private val forkastSykmeldingsperioder
        get() = ForkastSykmeldingsperioder(
            meldingsreferanseId = id,
            aktørId = aktørId,
            fødselsnummer = fødselsnummer,
            organisasjonsnummer = organisasjonsnummer,
            periode = periode
        )

    override fun behandle(mediator: IHendelseMediator, context: MessageContext) {
        mediator.behandle(this, forkastSykmeldingsperioder, context)
    }
}
