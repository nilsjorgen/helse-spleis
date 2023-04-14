package no.nav.helse.spleis.e2e

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.februar
import no.nav.helse.flex.sykepengesoknad.kafka.FravarDTO
import no.nav.helse.flex.sykepengesoknad.kafka.FravarstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.januar
import no.nav.helse.spleis.e2e.KontraktAssertions.assertOgFjernLocalDateTime
import no.nav.helse.spleis.e2e.KontraktAssertions.assertOgFjernUUID
import no.nav.helse.spleis.e2e.KontraktAssertions.assertUtgåendeMelding
import no.nav.helse.spleis.meldinger.model.SimuleringMessage
import no.nav.inntektsmeldingkontrakt.Periode
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class UtbetalingkontraktTest : AbstractEndToEndMediatorTest() {

    @Test
    fun `ny utbetaling`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        val utbetalingEndret = testRapid.inspektør.siste("utbetaling_endret")
        assertUtbetalingEndret(utbetalingEndret, "NY", "IKKE_UTBETALT")
        @Language("JSON")
        val forventet = """
           {
              "@event_name": "vedtaksperiode_ny_utbetaling",
              "aktørId": "$AKTØRID",
              "fødselsnummer": "$UNG_PERSON_FNR_2018",
              "organisasjonsnummer": "$ORGNUMMER"
           }
        """
        assertNyUtbetaling(forventet)
    }

    @Test
    fun `tildeler utbetaling til vedtaksperioder som treffes av revurderingen`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendUtbetalingsgodkjenning(0)
        sendUtbetaling()
        sendNySøknad(SoknadsperiodeDTO(fom = 27.januar, tom = 26.februar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 27.januar, tom = 26.februar, sykmeldingsgrad = 100))
        )
        sendYtelser(1)
        sendSimulering(1, SimuleringMessage.Simuleringstatus.OK)
        sendUtbetalingsgodkjenning(1)
        sendUtbetaling()

        assertEquals(2, testRapid.inspektør.meldinger("vedtaksperiode_ny_utbetaling").size)

        sendOverstyringTidslinje(listOf(ManuellOverskrivingDag(25.januar, Dagtype.Feriedag)))
        sendYtelser(1)

        val revurdering = testRapid.inspektør.siste("utbetaling_endret")

        val utbetalingId = revurdering.path("utbetalingId").asText()
        val vedtaksperiodeId1 = testRapid.inspektør.vedtaksperiodeId(0)
        val vedtaksperiodeId2 = testRapid.inspektør.vedtaksperiodeId(1)

        val nyeUtbetalinger = testRapid.inspektør.meldinger("vedtaksperiode_ny_utbetaling")
        assertEquals(4, nyeUtbetalinger.size)

        val fordelteRevurderinger = nyeUtbetalinger.takeLast(2)

        fordelteRevurderinger.first().also { førsteTildeling ->
            assertEquals(utbetalingId, førsteTildeling.path("utbetalingId").asText())
            assertEquals(vedtaksperiodeId1.toString(), førsteTildeling.path("vedtaksperiodeId").asText())
        }
        fordelteRevurderinger.last().also { andreTildeling ->
            assertEquals(utbetalingId, andreTildeling.path("utbetalingId").asText())
            assertEquals(vedtaksperiodeId2.toString(), andreTildeling.path("vedtaksperiodeId").asText())
        }
    }

    @Test
    fun `utbetaling utbetalt`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendUtbetalingsgodkjenning(0)
        sendUtbetaling()
        val utbetalt = testRapid.inspektør.siste("utbetaling_utbetalt")
        assertUtbetalt(utbetalt)
        val utbetalingEndret = testRapid.inspektør.siste("utbetaling_endret")
        assertUtbetalingEndret(utbetalingEndret, "OVERFØRT", "UTBETALT")
    }

    @Test
    fun `manuell behandling`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendUtbetalingsgodkjenning(
            vedtaksperiodeIndeks = 0,
            automatiskBehandling = false
        )
        sendUtbetaling()
        val utbetaltEvent = testRapid.inspektør.siste("utbetaling_utbetalt")
        assertFalse(utbetaltEvent["automatiskBehandling"].booleanValue())
    }

    @Test
    fun `automatisk behandling`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendUtbetalingsgodkjenning(
            vedtaksperiodeIndeks = 0,
            automatiskBehandling = true
        )
        sendUtbetaling()
        val utbetaltEvent = testRapid.inspektør.siste("utbetaling_utbetalt")
        assertTrue(utbetaltEvent["automatiskBehandling"].booleanValue())
    }

    @Test
    fun `spleis sender korrekt grad (avrundet) ut`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 30))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 30, faktiskGrad = 80))
        )
        sendInntektsmelding(listOf(Periode(fom = 1.januar, tom = 16.januar)), førsteFraværsdag = 1.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendUtbetalingsgodkjenning(0, true)
        sendUtbetaling()
        val utbetaling = testRapid.inspektør.siste("utbetaling_utbetalt")
        assertEquals(20.0, utbetaling.path("arbeidsgiverOppdrag").path("linjer").first().path("grad").asDouble())
    }

    @Test
    fun `Feriedager og permisjonsdager blir mappet riktig fra utbetalingstidslinjen for utbetaling_utbetalt`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100)),
            fravær = listOf(
                FravarDTO(fom = 20.januar, tom = 21.januar, type = FravarstypeDTO.FERIE),
                FravarDTO(fom = 22.januar, tom = 22.januar, type = FravarstypeDTO.PERMISJON)
            )
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendUtbetalingsgodkjenning(0)
        sendUtbetaling()
        val utbetaling = testRapid.inspektør.siste("utbetaling_utbetalt")

        assertEquals(2, utbetaling.path("utbetalingsdager").toList().filter { it["type"].asText() == "Feriedag" }.size)
        assertEquals(1, utbetaling.path("utbetalingsdager").toList().filter { it["type"].asText() == "Permisjonsdag" }.size)
    }

    @Test
    fun `Feriedager og permisjonsdager blir mappet riktig fra utbetalingstidslinjen for utbetaling_uten_utbetaling`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100)),
            fravær = listOf(FravarDTO(fom = 25.januar, tom = 26.januar, FravarstypeDTO.FERIE))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendUtbetalingsgodkjenning(0)
        sendUtbetaling()

        sendNySøknad(SoknadsperiodeDTO(fom = 27.januar, tom = 31.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 27.januar, tom = 31.januar, sykmeldingsgrad = 100)),
            fravær = listOf(FravarDTO(fom = 27.januar, tom = 31.januar, FravarstypeDTO.PERMISJON))
        )
        sendYtelser(1)
        sendUtbetalingsgodkjenning(1)

        val utbetaling = testRapid.inspektør.siste("utbetaling_uten_utbetaling")

        assertEquals(2, utbetaling.path("utbetalingsdager").toList().filter { it["type"].asText() == "Feriedag" }.size)
        assertEquals(5, utbetaling.path("utbetalingsdager").toList().filter { it["type"].asText() == "Permisjonsdag" }.size)
    }

    @Test
    fun `utbetaling med avviste dager`() {
        sendNySøknad(
            SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100),
            SoknadsperiodeDTO(fom = 27.januar, tom = 30.januar, sykmeldingsgrad = 15)
        )
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100), SoknadsperiodeDTO(fom = 27.januar, tom = 30.januar, sykmeldingsgrad = 15))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendUtbetalingsgodkjenning(0)
        sendUtbetaling()
        val utbetalt = testRapid.inspektør.siste("utbetaling_utbetalt")
        assertUtbetaltInkluderAvviste(utbetalt)
    }

    @Test
    fun `utbetaling uten utbetaling`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendUtbetalingsgodkjenning(0)
        sendUtbetaling()

        sendNySøknad(SoknadsperiodeDTO(fom = 27.januar, tom = 31.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 27.januar, tom = 31.januar, sykmeldingsgrad = 100)),
            fravær = listOf(FravarDTO(fom = 27.januar, tom = 31.januar, FravarstypeDTO.FERIE))
        )
        sendYtelser(1)
        sendUtbetalingsgodkjenning(1)
        val utbetalt = testRapid.inspektør.siste("utbetaling_uten_utbetaling")
        assertUtbetalt(utbetalt)
        val utbetalingEndret = testRapid.inspektør.siste("utbetaling_endret")
        assertUtbetalingEndret(utbetalingEndret, "IKKE_UTBETALT", "GODKJENT_UTEN_UTBETALING")
    }

    @Test
    fun `annullering full refusjon`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendUtbetalingsgodkjenning(0)
        sendUtbetaling()
        sendAnnullering(arbeidsgiverFagsystemId)
        sendUtbetaling()
        @Language("JSON")
        val forventet = """
            {
                "@event_name": "utbetaling_annullert",
                "organisasjonsnummer": "$ORGNUMMER",
                "aktørId": "$AKTØRID",
                "fødselsnummer": "$UNG_PERSON_FNR_2018",
                "epost" : "siri.saksbehandler@nav.no",
                "ident" : "S1234567",
                "fom" : "2018-01-03",
                "tom" : "2018-01-26",
                "arbeidsgiverFagsystemId": "$arbeidsgiverFagsystemId",
                "personFagsystemId": "$personFagsystemId",
                "korrelasjonsId": "$korrelasjonsId"
            }
        """
        assertUtbetalingAnnullert(forventet)
        val utbetalingEndret = testRapid.inspektør.siste("utbetaling_endret")
        assertUtbetalingEndret(utbetalingEndret, "OVERFØRT", "ANNULLERT", true)
    }

    @Test
    fun `annullering delvis refusjon`()  {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(
            listOf(Periode(fom = 3.januar, tom = 18.januar)),
            førsteFraværsdag = 3.januar,
            opphørsdatoForRefusjon = 20.januar
        )
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK, forventedeFagområder = setOf("SPREF", "SP"))
        sendUtbetalingsgodkjenning(0)
        sendUtbetaling()
        sendAnnullering(arbeidsgiverFagsystemId)
        sendUtbetaling()
        @Language("JSON")
        val forventet = """
            {
                "@event_name": "utbetaling_annullert",
                "organisasjonsnummer": "$ORGNUMMER",
                "aktørId": "$AKTØRID",
                "fødselsnummer": "$UNG_PERSON_FNR_2018",
                "epost" : "siri.saksbehandler@nav.no",
                "ident" : "S1234567",
                "fom" : "2018-01-03",
                "tom" : "2018-01-26",
                "arbeidsgiverFagsystemId": "$arbeidsgiverFagsystemId",
                "personFagsystemId": "$personFagsystemId",
                "korrelasjonsId": "$korrelasjonsId"
            }
        """
        assertUtbetalingAnnullert(forventet)
    }

    @Test
    fun `annullering ingen refusjon`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(
            listOf(Periode(fom = 3.januar, tom = 18.januar)),
            førsteFraværsdag = 3.januar,
            opphørsdatoForRefusjon = 3.januar
        )
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK, forventedeFagområder = setOf("SP"))
        sendUtbetalingsgodkjenning(0)
        sendUtbetaling()
        sendAnnullering(arbeidsgiverFagsystemId)
        sendUtbetaling()
        @Language("JSON")
        val forventet = """
            {
                "@event_name": "utbetaling_annullert",
                "organisasjonsnummer": "$ORGNUMMER",
                "aktørId": "$AKTØRID",
                "fødselsnummer": "$UNG_PERSON_FNR_2018",
                "epost" : "siri.saksbehandler@nav.no",
                "ident" : "S1234567",
                "fom" : "2018-01-03",
                "tom" : "2018-01-26",
                "arbeidsgiverFagsystemId": "$arbeidsgiverFagsystemId",
                "personFagsystemId": "$personFagsystemId",
                "korrelasjonsId": "$korrelasjonsId"
            }
        """
        assertUtbetalingAnnullert(forventet)

    }

    private fun assertUtbetaltInkluderAvviste(melding: JsonNode) {
        assertUtbetalt(melding)
        melding.path("utbetalingsdager").toList().filter { it["type"].asText() == "AvvistDag" }.also { avvisteDager ->
            assertTrue(avvisteDager.isNotEmpty())
            assertTrue(avvisteDager.all { it.hasNonNull("begrunnelser") })
        }
    }

    private fun assertUtbetalingEndret(melding: JsonNode, fra: String, til: String, annullering: Boolean = false) {
        assertTrue(melding.path("fødselsnummer").asText().isNotEmpty())
        assertTrue(melding.path("aktørId").asText().isNotEmpty())
        assertTrue(melding.path("organisasjonsnummer").asText().isNotEmpty())
        assertTrue(melding.path("utbetalingId").asText().isNotEmpty())
        assertTrue(melding.path("korrelasjonsId").asText().isNotEmpty())
        assertTrue(melding.path("type").asText().isNotEmpty())
        assertTrue(melding.path("forrigeStatus").asText().isNotEmpty())
        assertTrue(melding.path("gjeldendeStatus").asText().isNotEmpty())
        assertEquals(fra, melding.path("forrigeStatus").asText())
        assertEquals(til, melding.path("gjeldendeStatus").asText())
        assertOppdragdetaljer(melding.path("arbeidsgiverOppdrag"), annullering)
        assertOppdragdetaljer(melding.path("personOppdrag"), annullering)
    }

    private fun assertUtbetalt(melding: JsonNode) {
        assertTrue(melding.path("fødselsnummer").asText().isNotEmpty())
        assertTrue(melding.path("aktørId").asText().isNotEmpty())
        assertTrue(melding.path("organisasjonsnummer").asText().isNotEmpty())
        assertTrue(melding.path("utbetalingId").asText().isNotEmpty())
        assertTrue(melding.path("korrelasjonsId").asText().isNotEmpty())
        assertTrue(melding.path("type").asText().isNotEmpty())
        assertDato(melding.path("fom").asText())
        assertDato(melding.path("tom").asText())
        assertDato(melding.path("maksdato").asText())
        assertTrue(melding.path("forbrukteSykedager").isInt)
        assertTrue(melding.path("gjenståendeSykedager").isInt)
        assertTrue(melding.path("stønadsdager").isInt)
        assertTrue(melding.path("ident").asText().isNotEmpty())
        assertTrue(melding.path("epost").asText().isNotEmpty())
        assertTrue(melding.path("utbetalingsdager").toList().isNotEmpty())
        assertDatotid(melding.path("tidspunkt").asText())
        assertTrue(melding.path("automatiskBehandling").isBoolean)
        assertOppdragdetaljer(melding.path("arbeidsgiverOppdrag"), false)
        assertOppdragdetaljer(melding.path("personOppdrag"), false)
    }

    private fun assertOppdragdetaljer(oppdrag: JsonNode, erAnnullering: Boolean) {
        assertTrue(oppdrag.path("mottaker").asText().isNotEmpty())
        assertTrue(oppdrag.path("fagsystemId").asText().isNotEmpty())
        assertTrue(oppdrag.path("fagområde").asText().isNotEmpty())
        assertTrue(oppdrag.path("endringskode").asText().isNotEmpty())
        assertTrue(oppdrag.path("stønadsdager").isInt)
        assertDato(oppdrag.path("fom").asText())
        assertDato(oppdrag.path("tom").asText())
        if (erAnnullering) {
            assertEquals(0, oppdrag.path("stønadsdager").asInt())
        }
        val linjer = oppdrag.path("linjer")
        assertTrue(linjer.isArray)
        linjer.forEach { linje ->
            assertDato(linje.path("fom").asText())
            assertDato(linje.path("tom").asText())
            assertTrue(linje.path("dagsats").isInt)
            assertTrue(linje.path("sats").isInt)
            assertTrue(linje.path("lønn").isInt)
            assertTrue(linje.path("grad").isDouble)
            assertTrue(linje.path("stønadsdager").isInt)
            assertTrue(linje.path("totalbeløp").isInt)
            if (erAnnullering) {
                assertEquals(0, linje.path("stønadsdager").asInt())
                assertEquals(0, linje.path("totalbeløp").asInt())
            }
            assertTrue(linje.path("delytelseId").isInt)
            assertTrue(linje.has("refFagsystemId"))
            assertTrue(linje.has("refDelytelseId"))
            if (!erAnnullering) {
                assertTrue(linje.has("datoStatusFom"))
                assertTrue(linje.has("statuskode"))
            } else {
                assertDato(linje.path("datoStatusFom").asText())
                assertTrue(linje.path("statuskode").asText().isNotEmpty())
            }
            assertTrue(linje.path("endringskode").asText().isNotEmpty())
            assertTrue(linje.path("klassekode").asText().isNotEmpty())
        }
    }

    private fun assertDato(tekst: String) {
        assertTrue(tekst.isNotEmpty())
        assertDoesNotThrow { LocalDate.parse(tekst) }
    }

    private fun assertDatotid(tekst: String) {
        assertTrue(tekst.isNotEmpty())
        assertDoesNotThrow { LocalDateTime.parse(tekst) }
    }
    private fun assertNyUtbetaling(forventetMelding: String) {
        testRapid.assertUtgåendeMelding(forventetMelding) {
            it.assertOgFjernUUID("utbetalingId")
            it.assertOgFjernUUID("vedtaksperiodeId")
        }
    }

    private fun assertUtbetalingAnnullert(forventetMelding: String) {
        testRapid.assertUtgåendeMelding(forventetMelding) {
            it.assertOgFjernLocalDateTime("tidspunkt")
            it.assertOgFjernUUID("utbetalingId")
        }
    }
    private val arbeidsgiverFagsystemId get() = testRapid.inspektør.siste("utbetaling_utbetalt").path("arbeidsgiverOppdrag").path("fagsystemId").asText().also { check(it.matches(FagsystemIdRegex)) }
    private val personFagsystemId get() = testRapid.inspektør.siste("utbetaling_utbetalt").path("personOppdrag").path("fagsystemId").asText().also { check(it.matches(FagsystemIdRegex)) }
    private val korrelasjonsId get() = testRapid.inspektør.siste("utbetaling_utbetalt").path("korrelasjonsId").let { UUID.fromString(it.asText()) }
    private companion object {
        private val FagsystemIdRegex = "[A-Z,2-7]{26}".toRegex()
    }
}


