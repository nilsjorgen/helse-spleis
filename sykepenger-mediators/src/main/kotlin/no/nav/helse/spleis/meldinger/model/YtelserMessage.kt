package no.nav.helse.spleis.meldinger.model

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.hendelser.Arbeidsavklaringspenger
import no.nav.helse.hendelser.Dagpenger
import no.nav.helse.hendelser.Foreldrepenger
import no.nav.helse.hendelser.ForeldrepengerPeriode
import no.nav.helse.hendelser.Institusjonsopphold
import no.nav.helse.hendelser.Institusjonsopphold.Institusjonsoppholdsperiode
import no.nav.helse.hendelser.Omsorgspenger
import no.nav.helse.hendelser.OmsorgspengerPeriode
import no.nav.helse.hendelser.Opplæringspenger
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Pleiepenger
import no.nav.helse.hendelser.PleiepengerPeriode
import no.nav.helse.hendelser.Svangerskapspenger
import no.nav.helse.hendelser.SvangerskapspengerPeriode
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asOptionalLocalDate
import no.nav.helse.spleis.IHendelseMediator
import org.slf4j.LoggerFactory

// Understands a JSON message representing an Ytelserbehov
internal class YtelserMessage(packet: JsonMessage) : BehovMessage(packet) {

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }

    private val vedtaksperiodeId = packet["vedtaksperiodeId"].asText()
    private val organisasjonsnummer = packet["organisasjonsnummer"].asText()
    private val aktørId = packet["aktørId"].asText()
    private val arbeidsavklaringspenger: List<Pair<LocalDate, LocalDate>>
    private val ugyldigeArbeidsavklaringspengeperioder: List<Pair<LocalDate, LocalDate>>
    private val dagpenger: List<Pair<LocalDate, LocalDate>>
    private val ugyldigeDagpengeperioder: List<Pair<LocalDate, LocalDate>>

    private val foreldrepengerytelse = packet["@løsning.${Behovtype.Foreldrepenger.name}.Foreldrepengeytelse"]
        .takeIf(JsonNode::isObject)?.path("perioder")?.map {
            ForeldrepengerPeriode(Periode(it.path("fom").asLocalDate(), it.path("tom").asLocalDate()), it.path("grad").asInt())
        } ?: emptyList()
    private val svangerskapsytelse = packet["@løsning.${Behovtype.Foreldrepenger.name}.Svangerskapsytelse"]
        .takeIf(JsonNode::isObject)?.path("perioder")?.map {
            SvangerskapspengerPeriode(Periode(it.path("fom").asLocalDate(), it.path("tom").asLocalDate()), it.path("grad").asInt())
        } ?: emptyList()

    private val foreldrepenger = Foreldrepenger(foreldrepengeytelse = foreldrepengerytelse)
    private val svangerskapspenger = Svangerskapspenger(svangerskapsytelse = svangerskapsytelse)

    private val pleiepenger =
        Pleiepenger(packet["@løsning.${Behovtype.Pleiepenger.name}"].map {
            PleiepengerPeriode(Periode(it.path("fom").asLocalDate(), it.path("tom").asLocalDate()), it.path("grad").asInt())
        })

    private val omsorgspenger =
        Omsorgspenger(packet["@løsning.${Behovtype.Omsorgspenger.name}"].map {
            OmsorgspengerPeriode(Periode(it.path("fom").asLocalDate(), it.path("tom").asLocalDate()), it.path("grad").asInt())
        })

    private val opplæringspenger =
        Opplæringspenger(packet["@løsning.${Behovtype.Opplæringspenger.name}"].map(::asPeriode))

    private val institusjonsopphold = Institusjonsopphold(packet["@løsning.${Behovtype.Institusjonsopphold.name}"].map {
        Institusjonsoppholdsperiode(
            it.path("startdato").asLocalDate(),
            it.path("faktiskSluttdato").asOptionalLocalDate()
        )
    })

    init {
        packet["@løsning.${Behovtype.Arbeidsavklaringspenger.name}.meldekortperioder"].map(::asDatePair)
            .partition { it.first <= it.second }
            .also {
                arbeidsavklaringspenger = it.first
                ugyldigeArbeidsavklaringspengeperioder = it.second
            }
        packet["@løsning.${Behovtype.Dagpenger.name}.meldekortperioder"]
            .map(::asDatePair)
            .partition { it.first <= it.second }
            .also {
                dagpenger = it.first
                ugyldigeDagpengeperioder = it.second
            }
    }


    private val ytelser
        get() = Ytelser(
            meldingsreferanseId = this.id,
            aktørId = aktørId,
            fødselsnummer = fødselsnummer,
            organisasjonsnummer = organisasjonsnummer,
            vedtaksperiodeId = vedtaksperiodeId,
            foreldrepenger = foreldrepenger,
            svangerskapspenger = svangerskapspenger,
            pleiepenger = pleiepenger,
            omsorgspenger = omsorgspenger,
            opplæringspenger = opplæringspenger,
            institusjonsopphold = institusjonsopphold,
            arbeidsavklaringspenger = Arbeidsavklaringspenger(arbeidsavklaringspenger.map { Periode(it.first, it.second) }),
            dagpenger = Dagpenger(dagpenger.map { Periode(it.first, it.second) }),
            aktivitetslogg = Aktivitetslogg()
        ).also {
            if (ugyldigeArbeidsavklaringspengeperioder.isNotEmpty()) sikkerlogg.warn("Arena inneholdt en eller flere AAP-perioder med ugyldig fom/tom for {}", keyValue("aktørId", aktørId))
            if (ugyldigeDagpengeperioder.isNotEmpty()) sikkerlogg.warn("Arena inneholdt en eller flere Dagpengeperioder med ugyldig fom/tom for {}", keyValue("aktørId", aktørId))
        }

    override fun behandle(mediator: IHendelseMediator, context: MessageContext) {
        mediator.behandle(this, ytelser, context)
    }

    private fun asDatePair(jsonNode: JsonNode) =
        jsonNode.path("fom").asLocalDate() to jsonNode.path("tom").asLocalDate()
}
