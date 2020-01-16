package no.nav.helse.hendelser

import no.nav.helse.fixtures.januar
import no.nav.helse.person.Problemer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class ModelNySøknadTest {

    companion object {
        internal const val UNG_PERSON_FNR_2018 = "12020052345"
    }

    private lateinit var nySøknad: ModelNySøknad
    private lateinit var problemer: Problemer

    @BeforeEach
    internal fun setup() {
        problemer = Problemer()
    }

    @Test
    internal fun `sykdomsgrad som er 100% støttes`() {
        nySøknad(Triple(1.januar, 10.januar, 100), Triple(12.januar, 16.januar, 100))
        assertFalse(nySøknad.valider().hasErrors())
        assertEquals(16, nySøknad.sykdomstidslinje().length())
    }

    @Test
    internal fun `sykdomsgrad under 100% støttes ikke`() {
        nySøknad(Triple(1.januar, 10.januar, 50), Triple(12.januar, 16.januar, 100))
        assertTrue(nySøknad.valider().hasErrors())
    }

    @Test
    internal fun `sykeperioder mangler`() {
        assertThrows<Problemer> { nySøknad() }
    }

    @Test
    internal fun `overlappende sykeperioder`() {
        assertThrows<Problemer> {
            nySøknad(Triple(10.januar, 12.januar, 100), Triple(1.januar, 12.januar, 100))
        }
    }

    private fun nySøknad(vararg sykeperioder: Triple<LocalDate, LocalDate, Int>) {
        nySøknad = ModelNySøknad(
            UUID.randomUUID(),
            UNG_PERSON_FNR_2018,
            "12345",
            "987654321",
            LocalDateTime.now(),
            listOf(*sykeperioder),
            problemer,
            "{}"
        )
    }

}
