package no.nav.helse.serde.reflection

import no.nav.helse.person.Inntektshistorikk
import no.nav.helse.person.InntektshistorikkVol2
import no.nav.helse.serde.reflection.ReflectInstance.Companion.get
import no.nav.helse.økonomi.Inntekt
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.*

internal enum class Kilde {
    SKATT_SAMMENLIGNINGSGRUNNLAG, SKATT_SYKEPENGEGRUNNLAG, INFOTRYGD, INNTEKTSMELDING, INNTEKTSOPPLYSNING_REFERANSE, SAKSBEHANDLER
}

internal class InntektsendringReflect(inntektsendring: Inntektshistorikk.Inntektsendring) {
    private val fom: LocalDate = inntektsendring["fom"]
    private val hendelseId: UUID = inntektsendring["hendelseId"]
    private val beløp: Inntekt = inntektsendring["beløp"]
    private val kilde: Inntektshistorikk.Inntektsendring.Kilde = inntektsendring["kilde"]
    private val tidsstempel: LocalDateTime = inntektsendring["tidsstempel"]

    internal fun toMap(): Map<String, Any?> = mapOf(
        "fom" to fom,
        "hendelseId" to hendelseId,
        "beløp" to beløp.reflection { _, månedlig, _, _ -> månedlig },
        "kilde" to kilde.toString(),
        "tidsstempel" to tidsstempel
    )
}

internal abstract class InntektsopplysningReflect(
    inntektsopplysning: InntektshistorikkVol2.Inntektsopplysning,
    private val kilde: Kilde
) {
    private val id: UUID = inntektsopplysning["id"]
    private val dato: LocalDate = inntektsopplysning["dato"]
    private val hendelseId: UUID = inntektsopplysning["hendelseId"]
    private val beløp: Inntekt = inntektsopplysning["beløp"]
    private val tidsstempel: LocalDateTime = inntektsopplysning["tidsstempel"]

    internal open fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "dato" to dato,
        "hendelseId" to hendelseId,
        "beløp" to beløp.reflection { _, månedlig, _, _ -> månedlig },
        "kilde" to kilde,
        "tidsstempel" to tidsstempel
    )
}

internal abstract class SkattSykepengegrunnlagVol2Reflect(
    inntektsopplysning: InntektshistorikkVol2.Skatt,
    private val kilde: Kilde
) {
    private val dato: LocalDate = inntektsopplysning["dato"]
    private val hendelseId: UUID = inntektsopplysning["hendelseId"]
    private val beløp: Inntekt = inntektsopplysning["beløp"]
    private val tidsstempel: LocalDateTime = inntektsopplysning["tidsstempel"]

    private val måned: YearMonth = inntektsopplysning["måned"]
    private val type: InntektshistorikkVol2.Skatt.Inntekttype = inntektsopplysning["type"]
    private val fordel: String = inntektsopplysning["fordel"]
    private val beskrivelse: String = inntektsopplysning["beskrivelse"]

    internal fun toMap(): Map<String, Any?> {
        return mapOf(
            "dato" to dato,
            "hendelseId" to hendelseId,
            "beløp" to beløp.reflection { _, månedlig, _, _ -> månedlig },
            "kilde" to kilde,
            "tidsstempel" to tidsstempel,

            "måned" to måned,
            "type" to type,
            "fordel" to fordel,
            "beskrivelse" to beskrivelse
        )
    }
}

internal class SaksbehandlerVol2Reflect(
    inntektsopplysning: InntektshistorikkVol2.Saksbehandler
) : InntektsopplysningReflect(inntektsopplysning, Kilde.SAKSBEHANDLER)

internal class InntektsmeldingVol2Reflect(
    inntektsopplysning: InntektshistorikkVol2.Inntektsmelding
) : InntektsopplysningReflect(inntektsopplysning, Kilde.INNTEKTSMELDING)

internal class InntektsopplysningKopiVol2Reflect(
    inntektsopplysning: InntektshistorikkVol2.InntektsopplysningReferanse
){
    private val id: UUID = inntektsopplysning["id"]
    private val innslagId: UUID = inntektsopplysning["innslagId"]
    private val orginalOpplysningId: UUID = inntektsopplysning["orginalOpplysningId"]
    private val dato: LocalDate = inntektsopplysning["dato"]
    private val hendelseId: UUID = inntektsopplysning["hendelseId"]
    private val tidsstempel: LocalDateTime = inntektsopplysning["tidsstempel"]

    internal fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "innslagId" to innslagId,
        "orginalOpplysningId" to orginalOpplysningId,
        "dato" to dato,
        "hendelseId" to hendelseId,
        "kilde" to Kilde.INNTEKTSOPPLYSNING_REFERANSE,
        "tidsstempel" to tidsstempel
    )
}

internal class InfotrygdVol2Reflect(
    inntektsopplysning: InntektshistorikkVol2.Infotrygd
) : InntektsopplysningReflect(inntektsopplysning, Kilde.INFOTRYGD)

internal class SykepengegrunnlagVol2Reflect(
    inntektsopplysning: InntektshistorikkVol2.Skatt.Sykepengegrunnlag
) : SkattSykepengegrunnlagVol2Reflect(inntektsopplysning, Kilde.SKATT_SYKEPENGEGRUNNLAG)

internal class SammenligningsgrunnlagVol2Reflect(
    inntektsopplysning: InntektshistorikkVol2.Skatt.Sammenligningsgrunnlag
) : SkattSykepengegrunnlagVol2Reflect(inntektsopplysning, Kilde.SKATT_SAMMENLIGNINGSGRUNNLAG)
