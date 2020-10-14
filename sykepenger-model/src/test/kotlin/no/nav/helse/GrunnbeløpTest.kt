package no.nav.helse

import no.nav.helse.testhelpers.april
import no.nav.helse.testhelpers.mai
import no.nav.helse.testhelpers.september
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class GrunnbeløpTest {

    @Test
    fun dagsats() {
        assertEquals(2304.daglig, Grunnbeløp.`6G`.dagsats(1.mai(2019)))
        assertEquals(2236.daglig, Grunnbeløp.`6G`.dagsats(30.april(2019)))
    }

    @Test
    fun grunnbeløp() {
        assertEquals(599148.årlig, Grunnbeløp.`6G`.beløp(1.mai(2019)))
        assertEquals(581298.årlig, Grunnbeløp.`6G`.beløp(30.april(2019)))
    }

    @Test
    fun `grunnbeløp før virkingdato`() {
        assertEquals(99858.årlig, Grunnbeløp.`1G`.beløp(1.mai(2020)))
        assertEquals(99858.årlig, Grunnbeløp.`1G`.beløp(30.april(2020), 20.september(2020)))
        assertEquals(99858.årlig, Grunnbeløp.`1G`.beløp(1.mai(2020), 20.september(2020)))
    }

    @Test
    fun `grunnbeløp etter virkingdato`() {
        assertEquals(99858.årlig, Grunnbeløp.`1G`.beløp(30.april(2020), 21.september(2020)))
        assertEquals(101351.årlig, Grunnbeløp.`1G`.beløp(1.mai(2020), 21.september(2020)))
        assertEquals(101351.årlig, Grunnbeløp.`1G`.beløp(21.september(2020)))
    }

    @Test
    fun `virkingsdato må være nyere enn beregningsdato`() {
        assertThrows<IllegalArgumentException> { Grunnbeløp.`1G`.beløp(30.april(2020), 29.april(2020)) }
    }
}
