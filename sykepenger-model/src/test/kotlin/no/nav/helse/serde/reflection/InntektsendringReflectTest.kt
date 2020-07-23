package no.nav.helse.serde.reflection

import no.nav.helse.person.Inntekthistorikk
import no.nav.helse.person.Inntekthistorikk.Inntektsendring.Kilde.INFOTRYGD
import no.nav.helse.testhelpers.januar
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

internal class InntektsendringReflectTest {
    private val hendelseId = UUID.randomUUID()

    @Test
    fun `mapper Inntekt til map`() {
        val map = InntektReflect(inntekt).toMap()

        assertEquals(4, map.size)
        assertEquals(1.januar, map["fom"])
        assertEquals(hendelseId, map["hendelseId"])
        assertEquals(1000.0, map["beløp"])
        assertEquals(INFOTRYGD, Inntekthistorikk.Inntektsendring.Kilde.valueOf(map["kilde"].toString()))
    }

    internal val inntekt =
        Inntekthistorikk.Inntektsendring(1.januar, hendelseId, 1000.månedlig, INFOTRYGD)
}
