package no.nav.helse.spleis.meldinger.model

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.hendelser.SkjønnsmessigFastsettelse
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning
import no.nav.helse.person.inntekt.SkjønnsmessigFastsatt
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.spleis.IHendelseMediator
import no.nav.helse.spleis.meldinger.model.OverstyrArbeidsgiveropplysningerMessage.Companion.asRefusjonsopplysninger
import no.nav.helse.økonomi.Inntekt.Companion.månedlig

internal class SkjønnsmessigFastsettelseMessage(packet: JsonMessage) : HendelseMessage(packet) {

    override val fødselsnummer: String = packet["fødselsnummer"].asText()
    private val aktørId = packet["aktørId"].asText()
    private val skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate()
    private val arbeidsgiveropplysninger = packet["arbeidsgivere"].asArbeidsgiveropplysninger()

    override fun behandle(mediator: IHendelseMediator, context: MessageContext) =
        mediator.behandle(
            this, SkjønnsmessigFastsettelse(
                meldingsreferanseId = id,
                fødselsnummer = fødselsnummer,
                aktørId = aktørId,
                skjæringstidspunkt = skjæringstidspunkt,
                arbeidsgiveropplysninger = arbeidsgiveropplysninger
            ),
            context
        )

    private fun JsonNode.asArbeidsgiveropplysninger() = map { arbeidsgiveropplysning ->
        val orgnummer = arbeidsgiveropplysning["organisasjonsnummer"].asText()
        val månedligInntekt = arbeidsgiveropplysning["månedligInntekt"].asDouble().månedlig

        val skjønnsmessigFastsattInntekt =
            SkjønnsmessigFastsatt(skjæringstidspunkt, id, månedligInntekt, opprettet)
        val refusjonsopplysninger = arbeidsgiveropplysning["refusjonsopplysninger"].asRefusjonsopplysninger(id, opprettet)

        ArbeidsgiverInntektsopplysning(orgnummer, skjønnsmessigFastsattInntekt, refusjonsopplysninger)
    }
}


