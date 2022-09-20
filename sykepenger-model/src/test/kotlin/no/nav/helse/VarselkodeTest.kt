package no.nav.helse

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import no.nav.helse.person.AktivitetsloggObserverTest
import no.nav.helse.person.AktivitetsloggTest
import no.nav.helse.person.Varselkode
import no.nav.helse.person.Varselkode.*
import no.nav.helse.person.varselkodeformat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.io.path.extension
import kotlin.io.path.pathString

internal class VarselkodeTest {

    @Test
    fun `alle varselkoder testes eksplisitt`() {
        val aktiveVarselkoder = Varselkode.aktiveVarselkoder.toSet()
        val ikkeTestedeVarselkoder = aktiveVarselkoder.toMutableSet().apply {
            removeAll(finnAlleVarselkoderITest().toSet())
        }.toSet()

        val varselkoderSomKjentManglerTest = listOf(
            RV_SY_1, RV_SØ_5, RV_SØ_6, RV_SØ_7, RV_SØ_8, RV_SØ_9,
            RV_SØ_10, RV_IM_1, RV_IM_2, RV_IM_3, RV_IM_4, RV_IM_5, RV_IM_6, RV_RE_1, RV_IT_1, RV_IT_2,
            RV_IT_3, RV_IT_4, RV_IT_5, RV_VV_1, RV_VV_2, RV_VV_4, RV_VV_5, RV_VV_6, RV_VV_7, RV_OV_1,
            RV_OV_2, RV_MV_1, RV_MV_2, RV_IV_1, RV_IV_2, RV_SV_1, RV_SV_2, RV_AY_1, RV_AY_2, RV_AY_3,
            RV_AY_4, RV_AY_5, RV_AY_6, RV_AY_7, RV_AY_8, RV_AY_9, RV_SI_1, RV_SI_2, RV_UT_1, RV_UT_2,
            RV_OS_1, RV_OS_2, RV_OS_3, RV_RV_1
        )

        val varselkoderSomNåManglerTest = ikkeTestedeVarselkoder.minus(varselkoderSomKjentManglerTest.toSet())
        val (varselkoderSomFortsattBrukes, varselkoderSomIkkeBrukesLenger) = varselkoderSomKjentManglerTest.partition { it in aktiveVarselkoder }
        val varselkoderSomNåTestesEksplisitt = varselkoderSomFortsattBrukes.minus(ikkeTestedeVarselkoder)

        assertForventetFeil(
            forklaring = "Ikke alle varselkoder testes eksplisitt",
            ønsket = { assertEquals(emptySet<Varselkode>(), aktiveVarselkoder) },
            nå = {
                assertEquals(emptySet<Varselkode>(), varselkoderSomNåManglerTest) {
                    "Du har tatt i bruk en ny varselkode! Legg til eksplisitt test for nye varselkoder. _Ikke_ legg den i listen av varselkoder som mangler eksplisitt test."
                }
            }
        )

        assertEquals(emptySet<Varselkode>(), varselkoderSomIkkeBrukesLenger.toSet()) {
            "Disse varselkodene er ikke lenger i bruk. Du kan fjerne de fra listen av varsler som kjent mangler test."
        }

        assertEquals(emptySet<Varselkode>(), varselkoderSomNåTestesEksplisitt.toSet()) {
            "Disse varselkodene finnes det nå test for. Du kan fjerne de fra listen av varsler som kjent mangler test."
        }
    }

    private companion object {

        private fun Path.slutterPåEnAv(vararg suffix: String) = let { path -> suffix.firstOrNull { path.endsWith(it) } != null }

        private fun finn(
            scope: String,
            regex: Regex,
            ignorePath: (path: Path) -> Boolean = { false },
            ignoreLinje: (linje: String) -> Boolean = { false }) =
            Files.walk(Paths.get("../")).use { paths ->
                paths
                    .filter(Files::isRegularFile)
                    .filter { it.pathString.contains("/src/$scope/") }
                    .filter { it.fileName.extension == "kt" }
                    .filter { !ignorePath(it) }
                    .map { Files.readAllLines(it) }
                    .toList()
                    .asSequence()
                    .flatten()
                    .filterNot(ignoreLinje)
                    .map { linje -> regex.findAll(linje).toList().map { it.groupValues[1] } }
                    .flatten()
                    .toSet()
            }

        private fun finnAlleVarselkoderITest() = finn("test", "($varselkodeformat)".toRegex(), ignorePath = { path ->
            path.slutterPåEnAv("${VarselkodeTest::class.simpleName}.kt", "${this::class.simpleName}.kt", "${AktivitetsloggTest::class.simpleName}.kt", "${AktivitetsloggObserverTest::class.simpleName}.kt")
        }).map { enumValueOf<Varselkode>(it) }.distinct()
    }
}