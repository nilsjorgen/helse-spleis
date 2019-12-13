package no.nav.helse.unit.behov

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import no.nav.helse.behov.Behov
import no.nav.helse.behov.Behovtype
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class BehovTest {
    @Test
    fun `Opprette ett nytt behov`() {
        val behov = Behov.nyttBehov(listOf(Behovtype.Sykepengehistorikk, Behovtype.Foreldrepenger, Behovtype.Svangerskapspenger), mapOf("id" to "1123"))

        val json = behov.toJson()

        ObjectMapper().readTree(json)["@behov"].also {
            assertTrue(it.isArray)
            assertEquals(3, (it as ArrayNode).size())
        }

        assertTrue(json.contains("1123"))
    }

    @Test
    fun `Det er ikke lov å lage et invalid behov`() {
        assertThrows<IllegalArgumentException> { Behov.fromJson("{}") }
    }


    @Test
    fun `En behovsløser må kunne opprette et behov fra json, og legge på løsning, og lage json`() {

        val orignalBehov = Behov.nyttBehov(listOf(Behovtype.Sykepengehistorikk), mapOf("id" to "1123"))


        val behov = Behov.fromJson(orignalBehov.toJson())
        assertFalse(behov.erLøst())
        behov["final"] = true
        assertTrue(behov.erLøst())

        val json = behov.toJson()
        assertTrue(json.contains("1123"))

    }
}

