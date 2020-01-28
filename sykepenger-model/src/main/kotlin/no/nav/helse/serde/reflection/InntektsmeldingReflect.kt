package no.nav.helse.serde.reflection

import no.nav.helse.hendelser.ModelInntektsmelding
import no.nav.helse.person.ArbeidstakerHendelse
import no.nav.helse.person.ArbeidstakerHendelse.Hendelsestype
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class InntektsmeldingReflect(inntektsmelding: ModelInntektsmelding) {
    private val hendelseId: UUID = inntektsmelding.hendelseId()
    private val hendelsetype: Hendelsestype = inntektsmelding.hendelsetype()
    private val refusjon: ModelInntektsmelding.Refusjon = inntektsmelding.getProp("refusjon")
    private val orgnummer: String = inntektsmelding.getProp("orgnummer")
    private val fødselsnummer: String = inntektsmelding.getProp("fødselsnummer")
    private val aktørId: String = inntektsmelding.getProp("aktørId")
    private val mottattDato: LocalDateTime = inntektsmelding.getProp("mottattDato")
    private val førsteFraværsdag: LocalDate = inntektsmelding.getProp("førsteFraværsdag")
    private val beregnetInntekt: Double = inntektsmelding.getProp("beregnetInntekt")
    private val originalJson: String = inntektsmelding.getProp("originalJson")
    private val arbeidsgiverperioder: List<ModelInntektsmelding.InntektsmeldingPeriode.Arbeidsgiverperiode> =
        inntektsmelding.getProp("arbeidsgiverperioder")
    private val ferieperioder: List<ModelInntektsmelding.InntektsmeldingPeriode.Ferieperiode> =
        inntektsmelding.getProp("ferieperioder")

    fun toMap() = mutableMapOf<String, Any?>(
        "hendelseId" to hendelseId,
        "hendelsetype" to hendelsetype.name,
        "refusjon" to refusjon,
        "orgnummer" to orgnummer,
        "fødselsnummer" to fødselsnummer,
        "aktørId" to aktørId,
        "mottattDato" to mottattDato,
        "førsteFraværsdag" to førsteFraværsdag,
        "beregnetInntekt" to beregnetInntekt,
        "originalJson" to originalJson,
        "arbeidsgiverperioder" to arbeidsgiverperioder.map(::periodeToMap),
        "ferieperioder" to ferieperioder.map(::periodeToMap)
    )

    private fun periodeToMap(periode: ModelInntektsmelding.InntektsmeldingPeriode) = mapOf(
        "fom" to periode.fom,
        "tom" to periode.tom
    )
}
