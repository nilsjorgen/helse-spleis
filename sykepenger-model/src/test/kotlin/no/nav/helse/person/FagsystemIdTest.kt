package no.nav.helse.person

import no.nav.helse.testhelpers.*
import no.nav.helse.testhelpers.NAV
import no.nav.helse.testhelpers.Utbetalingsdager
import no.nav.helse.testhelpers.tidslinjeOf
import no.nav.helse.utbetalingslinjer.Fagområde
import no.nav.helse.utbetalingslinjer.Oppdrag
import no.nav.helse.utbetalingslinjer.OppdragBuilder
import no.nav.helse.utbetalingstidslinje.MaksimumUtbetaling
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class FagsystemIdTest {

    companion object {
        private const val ORGNUMMER = "123456789"
    }

    private lateinit var fagsystemIder: MutableList<FagsystemId>
    private lateinit var aktivitetslogg: Aktivitetslogg

    @BeforeEach
    fun beforeEach() {
        fagsystemIder = mutableListOf()
        aktivitetslogg = Aktivitetslogg()
    }

    @Test
    fun `Ny fagsystemId`() {
        opprett(1.NAV)
        assertEquals(1, fagsystemIder.size)
    }

    @Test
    fun `Nytt element når fagsystemId'er er forskjellige`() {
        opprett(1.NAV)
        opprett(1.NAV, startdato = 17.januar)
        assertEquals(2, fagsystemIder.size)
    }

    @Test
    fun `legger nytt oppdrag til på eksisterende fagsystemId`() {
        opprett(16.AP, 5.NAV)
        opprett(5.NAV(1300))
        assertEquals(1, fagsystemIder.size)
    }

    @Test
    fun `mapper riktig når det finnes flere fagsystemId'er`() {
        val oppdrag1 = opprett(16.AP, 5.NAV)
        val oppdrag2 = opprett(16.AP, 5.NAV, startdato = 1.mars)
        val oppdrag1Oppdatert = opprett(16.AP, 5.NAV(1300))
        assertEquals(2, fagsystemIder.size)
        assertEquals(oppdrag1.fagsystemId(), oppdrag1Oppdatert.fagsystemId())
        assertNotEquals(oppdrag2.fagsystemId(), oppdrag1Oppdatert.fagsystemId())
    }

    private fun opprett(vararg dager: Utbetalingsdager, startdato: LocalDate = 1.januar, sisteDato: LocalDate? = null): Oppdrag {
        val tidslinje = tidslinjeOf(*dager, startDato = startdato)
        MaksimumUtbetaling(
            listOf(tidslinje),
            Aktivitetslogg(),
            listOf(1.januar),
            1.januar
        ).betal().let {
            return OppdragBuilder(
                tidslinje,
                ORGNUMMER,
                Fagområde.SykepengerRefusjon,
                sisteDato ?: tidslinje.sisteDato()
            ).result().let {
                FagsystemId.kobleTil(fagsystemIder, it, aktivitetslogg)
            }
        }
    }

}
