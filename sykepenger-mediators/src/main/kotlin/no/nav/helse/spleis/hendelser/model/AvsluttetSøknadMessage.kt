package no.nav.helse.spleis.hendelser.model

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.hendelser.AvsluttetSøknad
import no.nav.helse.hendelser.AvsluttetSøknad.Periode.Sykdom
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.spleis.hendelser.MessageFactory
import no.nav.helse.spleis.hendelser.MessageProcessor

// Understands a JSON message representing a Søknad that is only sent to the employer
internal class AvsluttetSøknadMessage(originalMessage: String, problems: MessageProblems) :
    SøknadMessage(originalMessage, problems) {
    init {
        requireValue("@event_name", "sendt_søknad")
        requireValue("status", "SENDT")
        requireKey("id", "egenmeldinger", "fravar")
        require("fom", JsonNode::asLocalDate)
        require("tom", JsonNode::asLocalDate)
        require("sendtArbeidsgiver", JsonNode::asLocalDate)
        forbid("sendtNav")
    }

    private val aktørId get() = this["aktorId"].asText()
    private val orgnummer get() = this["arbeidsgiver.orgnummer"].asText()
    private val perioder get() = this["soknadsperioder"].map {
        Sykdom(
            fom = it.path("fom").asLocalDate(),
            tom = it.path("tom").asLocalDate(),
            grad = it.path("sykmeldingsgrad").asInt(),
            faktiskGrad = it.path("faktiskGrad").asDouble(it.path("sykmeldingsgrad").asDouble())
        )
    }

    override fun accept(processor: MessageProcessor) {
        processor.process(this)
    }

    internal fun asAvsluttetSøknad(): AvsluttetSøknad {
        return AvsluttetSøknad(
            meldingsreferanseId = this.id,
            fnr = fødselsnummer,
            aktørId = aktørId,
            orgnummer = orgnummer,
            perioder = perioder
        )
    }

    object Factory : MessageFactory<AvsluttetSøknadMessage> {
        override fun createMessage(message: String, problems: MessageProblems) = AvsluttetSøknadMessage(message, problems)
    }
}
