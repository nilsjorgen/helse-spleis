package no.nav.helse.unit.spleis.hendelser.model

import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.Aktivitetslogger
import no.nav.helse.spleis.hendelser.model.YtelserMessage
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*

class YtelserMessageTest {

    @Test
    fun `Kan mappe om message til modell uten feil`() {
        val aktivitetslogger = Aktivitetslogger()
        val aktivitetslogg = Aktivitetslogg()
        YtelserMessage(json, aktivitetslogger, aktivitetslogg).asYtelser().valider()

        assertFalse(aktivitetslogg.hasErrors())
    }

    @Test
    fun `ukjente perioder gir feil i mapping`() {
        val aktivitetslogger = Aktivitetslogger()
        val aktivitetslogg = Aktivitetslogg()
        YtelserMessage(ukjentPeriode, aktivitetslogger, aktivitetslogg).asYtelser().valider()

        assertTrue(aktivitetslogg.hasErrors(), aktivitetslogg.toString())
    }
}

private val json = """
    {
      "utgangspunktForBeregningAvYtelse": "2019-12-08",
      "@behov": [
        "Sykepengehistorikk",
        "Foreldrepenger"
      ],
      "@id": "${UUID.randomUUID()}",
      "@opprettet": "2020-01-24T11:25:00",
      "hendelse": "Ytelser",
      "aktørId": "aktørId",
      "fødselsnummer": "fnr",
      "organisasjonsnummer": "orgnummer",
      "vedtaksperiodeId": "${UUID.randomUUID()}",
      "@løsning": {
        "Foreldrepenger": {
          "Foreldrepengeytelse": null,
          "Svangerskapsytelse": null
        },
        "Sykepengehistorikk": [
          {
            "fom": "2019-03-11",
            "tom": "2019-04-12",
            "grad": "100",
            "inntektsopplysninger": [
              {
                "sykepengerFom": "2019-03-27",
                "inntekt": 36000,
                "orgnummer": "orgnummer",
                "refusjonTom": null
              }
            ],
            "utbetalteSykeperioder": [
              {
                "fom": "2019-03-28",
                "tom": "2019-04-12",
                "utbetalingsGrad": "100",
                "oppgjorsType": "",
                "utbetalt": "2019-04-23",
                "dagsats": 1400.0,
                "typeKode": "5",
                "typeTekst": "ArbRef",
                "orgnummer": "orgnummer",
                "inntektPerMåned": 36000
              }
            ]
          }
        ]
      },
      "@final": true,
      "@besvart": "2020-01-24T11:25:00"
    }
""".trimIndent()

private val ukjentPeriode = """
    {
      "utgangspunktForBeregningAvYtelse": "2019-12-08",
      "@behov": [
        "Sykepengehistorikk",
        "Foreldrepenger"
      ],
      "@id": "${UUID.randomUUID()}",
      "@opprettet": "2020-01-24T11:25:00",
      "hendelse": "Ytelser",
      "aktørId": "aktørId",
      "fødselsnummer": "fnr",
      "organisasjonsnummer": "orgnummer",
      "vedtaksperiodeId": "${UUID.randomUUID()}",
      "@løsning": {
        "Foreldrepenger": {
          "Foreldrepengeytelse": null,
          "Svangerskapsytelse": null
        },
        "Sykepengehistorikk": [
          {
            "fom": "2019-03-28",
            "tom": "2019-04-12",
            "grad": "100",
            "inntektsopplysninger": [
              {
                "sykepengerFom": "2019-03-27",
                "inntekt": 36000,
                "orgnummer": "orgnummer",
                "refusjonTom": null
              }
            ],
            "ukjentePerioder": [{
                "key": "value"
            }],
            "utbetalteSykeperioder": [
              {
                "fom": "2019-03-28",
                "tom": "2019-04-12",
                "utbetalingsGrad": "100",
                "oppgjorsType": "",
                "utbetalt": "2019-04-23",
                "dagsats": 1400.0,
                "typeKode": "5",
                "typeTekst": "ArbRef",
                "orgnummer": "orgnummer",
                "inntektPerMåned": 36000
              }
            ]
          }
        ]
      },
      "@final": true,
      "@besvart": "2020-01-24T11:25:00"
    }
""".trimIndent()
