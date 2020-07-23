package no.nav.helse.serde.reflection

import no.nav.helse.person.Inntekthistorikk
import no.nav.helse.serde.reflection.ReflectInstance.Companion.get
import no.nav.helse.økonomi.Inntekt
import java.time.LocalDate
import java.util.*

internal class InntektReflect(inntektsendring: Inntekthistorikk.Inntektsendring) {
    private val fom: LocalDate = inntektsendring["fom"]
    private val hendelseId: UUID = inntektsendring["hendelseId"]
    private val beløp: Inntekt = inntektsendring["beløp"]
    private val kilde: Inntekthistorikk.Inntektsendring.Kilde = inntektsendring["kilde"]

    internal fun toMap(): Map<String, Any?> = mapOf(
        "fom" to fom,
        "hendelseId" to hendelseId,
        "beløp" to beløp.tilMånedligDouble(),
        "kilde" to kilde.toString()
    )
}
