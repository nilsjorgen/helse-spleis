package no.nav.helse.utbetalingslinjer

import java.time.LocalDateTime
import java.time.Month
import java.time.Year
import java.util.UUID
import no.nav.helse.Personidentifikator
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.hendelser.UtbetalingshistorikkForFeriepenger
import no.nav.helse.hendelser.utbetaling.UtbetalingHendelse
import no.nav.helse.person.aktivitetslogg.Aktivitetskontekst
import no.nav.helse.person.FeriepengeutbetalingVisitor
import no.nav.helse.person.Person
import no.nav.helse.hendelser.PersonHendelse
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.aktivitetslogg.SpesifikkKontekst
import no.nav.helse.person.infotrygdhistorikk.Nødnummer
import no.nav.helse.utbetalingslinjer.Utbetaling.Utbetalingtype
import no.nav.helse.utbetalingstidslinje.Feriepengeberegner
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt

internal class Feriepengeutbetaling private constructor(
    private val feriepengeberegner: Feriepengeberegner,
    private val infotrygdFeriepengebeløpPerson: Double,
    private val infotrygdFeriepengebeløpArbeidsgiver: Double,
    private val spleisFeriepengebeløpArbeidsgiver: Double,
    private val spleisFeriepengebeløpPerson: Double,
    private val oppdrag: Oppdrag,
    private val personoppdrag: Oppdrag,
    private val utbetalingId: UUID,
    private val sendTilOppdrag: Boolean,
    private val sendPersonoppdragTilOS: Boolean,
) : Aktivitetskontekst {
    var overføringstidspunkt: LocalDateTime? = null
    var avstemmingsnøkkel: Long? = null

    companion object {
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
        fun List<Feriepengeutbetaling>.gjelderFeriepengeutbetaling(hendelse: UtbetalingHendelse) = any { hendelse.erRelevant(it.oppdrag.fagsystemId()) || hendelse.erRelevant(it.personoppdrag.fagsystemId()) }
        internal fun ferdigFeriepengeutbetaling(
            feriepengeberegner: Feriepengeberegner,
            infotrygdFeriepengebeløpPerson: Double,
            infotrygdFeriepengebeløpArbeidsgiver: Double,
            spleisFeriepengebeløpArbeidsgiver: Double,
            spleisFeriepengebeløpPerson: Double,
            oppdrag: Oppdrag,
            personoppdrag: Oppdrag,
            utbetalingId: UUID,
            sendTilOppdrag: Boolean,
            sendPersonoppdragTilOS: Boolean,
        ): Feriepengeutbetaling =
            Feriepengeutbetaling(
                feriepengeberegner = feriepengeberegner,
                infotrygdFeriepengebeløpPerson = infotrygdFeriepengebeløpPerson,
                infotrygdFeriepengebeløpArbeidsgiver = infotrygdFeriepengebeløpArbeidsgiver,
                spleisFeriepengebeløpArbeidsgiver = spleisFeriepengebeløpArbeidsgiver,
                spleisFeriepengebeløpPerson = spleisFeriepengebeløpPerson,
                oppdrag = oppdrag,
                personoppdrag = personoppdrag,
                utbetalingId = utbetalingId,
                sendTilOppdrag = sendTilOppdrag,
                sendPersonoppdragTilOS = sendPersonoppdragTilOS,
            )
    }

    internal fun accept(visitor: FeriepengeutbetalingVisitor) {
        visitor.preVisitFeriepengeutbetaling(
            this,
            infotrygdFeriepengebeløpPerson,
            infotrygdFeriepengebeløpArbeidsgiver,
            spleisFeriepengebeløpArbeidsgiver,
            spleisFeriepengebeløpPerson,
            overføringstidspunkt,
            avstemmingsnøkkel,
            utbetalingId,
            sendTilOppdrag,
            sendPersonoppdragTilOS,
        )
        feriepengeberegner.accept(visitor)
        visitor.preVisitFeriepengerArbeidsgiveroppdrag()
        oppdrag.accept(visitor)
        visitor.preVisitFeriepengerPersonoppdrag()
        personoppdrag.accept(visitor)
        visitor.postVisitFeriepengeutbetaling(
            this,
            infotrygdFeriepengebeløpPerson,
            infotrygdFeriepengebeløpArbeidsgiver,
            spleisFeriepengebeløpArbeidsgiver,
            overføringstidspunkt,
            avstemmingsnøkkel,
            utbetalingId,
            sendTilOppdrag,
            sendPersonoppdragTilOS,
        )
    }

    fun håndter(utbetalingHendelse: UtbetalingHendelse, organisasjonsnummer: String, person: Person) {
        if (!utbetalingHendelse.erRelevant(oppdrag.fagsystemId(), personoppdrag.fagsystemId(), utbetalingId)) return

        utbetalingHendelse.info("Behandler svar fra Oppdrag/UR/spenn for feriepenger")
        utbetalingHendelse.valider()
        val utbetaltOk = !utbetalingHendelse.harFunksjonelleFeilEllerVerre()
        lagreInformasjon(utbetalingHendelse, utbetaltOk)

        if (!utbetaltOk) {
            sikkerLogg.info("Utbetaling av feriepenger med utbetalingId $utbetalingId og fagsystemIder ${oppdrag.fagsystemId()} og ${personoppdrag.fagsystemId()} feilet.")
            return
        }

        person.feriepengerUtbetalt(
            PersonObserver.FeriepengerUtbetaltEvent(
                organisasjonsnummer = organisasjonsnummer,
                arbeidsgiverOppdrag = oppdrag.toHendelseMap(),
                personOppdrag = personoppdrag.toHendelseMap()
            )
        )

        person.utbetalingEndret(
            PersonObserver.UtbetalingEndretEvent(
                organisasjonsnummer = organisasjonsnummer,
                utbetalingId = utbetalingId,
                type = Utbetalingtype.FERIEPENGER.name,
                forrigeStatus = Utbetalingstatus.fraTilstand(Utbetaling.Ubetalt).name,
                gjeldendeStatus = Utbetalingstatus.fraTilstand(Utbetaling.Utbetalt).name,
                arbeidsgiverOppdrag = oppdrag.toHendelseMap(),
                personOppdrag = personoppdrag.toHendelseMap(),
                korrelasjonsId = UUID.randomUUID()
            )
        )
    }

    private fun lagreInformasjon(hendelse: UtbetalingHendelse, gikkBra: Boolean) {
        overføringstidspunkt = hendelse.overføringstidspunkt
        avstemmingsnøkkel = hendelse.avstemmingsnøkkel
        hendelse.info("Data for feriepenger fra Oppdrag/UR: tidspunkt: $overføringstidspunkt, avstemmingsnøkkel $avstemmingsnøkkel og utbetalt ok: ${if (gikkBra) "ja" else "nei"}")
    }

    override fun toSpesifikkKontekst() =
        SpesifikkKontekst("Feriepengeutbetaling", mapOf("utbetalingId" to "$utbetalingId"))

    internal fun overfør(hendelse: PersonHendelse) {
        hendelse.kontekst(this)
        if (sendTilOppdrag) oppdrag.overfør(hendelse, null, "SPLEIS")
        if (sendPersonoppdragTilOS) personoppdrag.overfør(hendelse, null, "SPLEIS")
    }

    internal fun gjelderForÅr(år: Year) = feriepengeberegner.gjelderForÅr(år)

    internal class Builder(
        private val aktørId: String,
        private val personidentifikator: Personidentifikator,
        private val orgnummer: String,
        private val feriepengeberegner: Feriepengeberegner,
        private val utbetalingshistorikkForFeriepenger: UtbetalingshistorikkForFeriepenger,
        private val tidligereFeriepengeutbetalinger: List<Feriepengeutbetaling>
    ) {
        internal fun build(): Feriepengeutbetaling {
            val infotrygdHarUtbetaltTilArbeidsgiver = utbetalingshistorikkForFeriepenger.utbetalteFeriepengerTilArbeidsgiver(orgnummer)
            val hvaViHarBeregnetAtInfotrygdHarUtbetaltTilArbeidsgiver = feriepengeberegner.beregnUtbetalteFeriepengerForInfotrygdArbeidsgiver(orgnummer)
            val hvaViHarBeregnetAtInfotrygdHarUtbetaltTilPersonForDenneAktuelleArbeidsgiver = feriepengeberegner.beregnUtbetalteFeriepengerForInfotrygdPersonForEnArbeidsgiver(orgnummer)

            if (hvaViHarBeregnetAtInfotrygdHarUtbetaltTilArbeidsgiver.roundToInt() != 0 &&
                hvaViHarBeregnetAtInfotrygdHarUtbetaltTilArbeidsgiver.roundToInt() !in infotrygdHarUtbetaltTilArbeidsgiver
            ) {
                sikkerLogg.info(
                    """
                    Beregnet feriepengebeløp til arbeidsgiver i IT samsvarer ikke med faktisk utbetalt beløp
                    AktørId: $aktørId
                    Arbeidsgiver: $orgnummer
                    Infotrygd har utbetalt $infotrygdHarUtbetaltTilArbeidsgiver
                    Vi har beregnet at infotrygd har utbetalt ${hvaViHarBeregnetAtInfotrygdHarUtbetaltTilArbeidsgiver.roundToInt()}
                    """.trimIndent()
                )
            }

            val infotrygdFeriepengebeløpPerson = feriepengeberegner.beregnFeriepengerForInfotrygdPerson(orgnummer)
            val infotrygdFeriepengebeløpArbeidsgiver = feriepengeberegner.beregnFeriepengerForInfotrygdArbeidsgiver(orgnummer)
            val spleisFeriepengebeløpArbeidsgiver = feriepengeberegner.beregnFeriepengerForSpleisArbeidsgiver(orgnummer)
            val spleisFeriepengebeløpPerson = feriepengeberegner.beregnFeriepengerForSpleisPerson(orgnummer)

            val totaltFeriepengebeløpArbeidsgiver: Double = feriepengeberegner.beregnFeriepengerForArbeidsgiver(orgnummer)
            val differanseMellomTotalOgAlleredeUtbetaltAvInfotrygd: Double = feriepengeberegner.beregnFeriepengedifferansenForArbeidsgiver(orgnummer)
            val beløp = differanseMellomTotalOgAlleredeUtbetaltAvInfotrygd.roundToInt()

            val forrigeSendteOppdrag =
                tidligereFeriepengeutbetalinger
                    .lastOrNull { it.gjelderForÅr(utbetalingshistorikkForFeriepenger.opptjeningsår) && it.sendTilOppdrag }
                    ?.oppdrag
                    ?.takeIf { it.linjerUtenOpphør().isNotEmpty() }

            val fagsystemId =
                forrigeSendteOppdrag
                    ?.fagsystemId()
                    ?: genererUtbetalingsreferanse(UUID.randomUUID())

            var oppdrag = Oppdrag(
                mottaker = orgnummer,
                fagområde = Fagområde.SykepengerRefusjon,
                linjer = listOf(
                    Utbetalingslinje(
                        fom = utbetalingshistorikkForFeriepenger.opptjeningsår.plusYears(1).atMonth(Month.MAY).atDay(1),
                        tom = utbetalingshistorikkForFeriepenger.opptjeningsår.plusYears(1).atMonth(Month.MAY).atEndOfMonth(),
                        satstype = Satstype.Engang,
                        beløp = beløp,
                        aktuellDagsinntekt = null,
                        grad = null,
                        klassekode = Klassekode.RefusjonFeriepengerIkkeOpplysningspliktig
                    )
                ),
                fagsystemId = fagsystemId,
                sisteArbeidsgiverdag = null
            )

            if (beløp != 0 && orgnummer == "0") sikkerLogg.warn("Forventer ikke arbeidsgiveroppdrag til orgnummer \"0\", aktørId=$aktørId.")

            val sendTilOppdrag = if (forrigeSendteOppdrag == null) { beløp != 0 } else { beløp != forrigeSendteOppdrag.totalbeløp() || beløp == 0 }

            if (forrigeSendteOppdrag != null) {
                oppdrag = if (beløp == 0) {
                    forrigeSendteOppdrag.annuller(utbetalingshistorikkForFeriepenger)
                } else {
                    oppdrag.minus(forrigeSendteOppdrag, utbetalingshistorikkForFeriepenger)
                }
            }

            val differanseMellomTotalOgAlleredeUtbetaltAvInfotrygdTilPerson =  infotrygdFeriepengebeløpPerson - hvaViHarBeregnetAtInfotrygdHarUtbetaltTilPersonForDenneAktuelleArbeidsgiver
            val personbeløp = differanseMellomTotalOgAlleredeUtbetaltAvInfotrygdTilPerson.roundToInt()

            val forrigeSendteOppdragForPerson =
                tidligereFeriepengeutbetalinger
                    .lastOrNull { it.gjelderForÅr(utbetalingshistorikkForFeriepenger.opptjeningsår) && it.sendPersonoppdragTilOS }
                    ?.personoppdrag
                    ?.takeIf { it.linjerUtenOpphør().isNotEmpty() }

            val fagsystemIdPersonoppdrag =
                forrigeSendteOppdragForPerson
                    ?.fagsystemId()
                    ?: genererUtbetalingsreferanse(UUID.randomUUID())

            var personoppdrag = Oppdrag(
                mottaker = personidentifikator.toString(),
                fagområde = Fagområde.Sykepenger,
                linjer = listOf(
                    Utbetalingslinje(
                        fom = utbetalingshistorikkForFeriepenger.opptjeningsår.plusYears(1).atMonth(Month.MAY).atDay(1),
                        tom = utbetalingshistorikkForFeriepenger.opptjeningsår.plusYears(1).atMonth(Month.MAY).atEndOfMonth(),
                        satstype = Satstype.Engang,
                        beløp = personbeløp,
                        aktuellDagsinntekt = null,
                        grad = null,
                        klassekode = Klassekode.SykepengerArbeidstakerFeriepenger,
                    )
                ),
                fagsystemId = fagsystemIdPersonoppdrag,
                sisteArbeidsgiverdag = null,
            )

            val sendPersonoppdragTilOS = if (forrigeSendteOppdragForPerson == null) { personbeløp != 0 } else { personbeløp != forrigeSendteOppdragForPerson.totalbeløp() || personbeløp == 0 }

            if (forrigeSendteOppdragForPerson != null) {
                personoppdrag = if (personbeløp == 0) {
                    forrigeSendteOppdragForPerson.annuller(utbetalingshistorikkForFeriepenger)
                } else {
                    personoppdrag.minus(forrigeSendteOppdragForPerson, utbetalingshistorikkForFeriepenger)
                }
            }

            if (differanseMellomTotalOgAlleredeUtbetaltAvInfotrygdTilPerson < -499 || differanseMellomTotalOgAlleredeUtbetaltAvInfotrygdTilPerson > 100) sikkerLogg.info(
                """
                ${if (differanseMellomTotalOgAlleredeUtbetaltAvInfotrygdTilPerson < 0) "Differanse mellom det IT har utbetalt og det spleis har beregnet at IT skulle betale" else "Utbetalt for lite i Infotrygd"} for person & orgnr-kombo:
                AktørId: $aktørId
                Arbeidsgiver: $orgnummer
                Diff: $differanseMellomTotalOgAlleredeUtbetaltAvInfotrygdTilPerson
                Hva vi har beregnet at IT har utbetalt til person for denne AG: $hvaViHarBeregnetAtInfotrygdHarUtbetaltTilPersonForDenneAktuelleArbeidsgiver
                IT sin personandel: $infotrygdFeriepengebeløpPerson
                """.trimIndent()
            )
            sikkerLogg.info(
                """
                Nøkkelverdier om feriepengeberegning
                AktørId: $aktørId
                Arbeidsgiver: $orgnummer${if(Nødnummer.Sykepenger.contains(orgnummer)) " (NØDNUMMER)" else ""}
                
                - ARBEIDSGIVER:
                Alle feriepengeutbetalinger fra Infotrygd (alle ytelser): $infotrygdHarUtbetaltTilArbeidsgiver
                Vår beregning av hva Infotrygd burde utbetalt av feriepenger for sykepenger: $hvaViHarBeregnetAtInfotrygdHarUtbetaltTilArbeidsgiver
                Infotrygd skal betale: $infotrygdFeriepengebeløpArbeidsgiver
                Spleis skal betale: $spleisFeriepengebeløpArbeidsgiver
                Totalt feriepengebeløp: $totaltFeriepengebeløpArbeidsgiver
                Infotrygd-utbetalingen må korrigeres med: $differanseMellomTotalOgAlleredeUtbetaltAvInfotrygd
                
                - PERSON:
                Alle feriepengeutbetalinger fra Infotrygd (alle ytelser): ${utbetalingshistorikkForFeriepenger.utbetalteFeriepengerTilPerson()}
                Vår beregning av hva Infotrygd burde utbetalt av feriepenger for sykepenger: $hvaViHarBeregnetAtInfotrygdHarUtbetaltTilPersonForDenneAktuelleArbeidsgiver
                Infotrygd skal betale: $infotrygdFeriepengebeløpPerson
                Spleis skal betale: $spleisFeriepengebeløpPerson
                Infotrygd-utbetalingen må korrigeres med: $differanseMellomTotalOgAlleredeUtbetaltAvInfotrygdTilPerson

                - GENERELT:         
                ${feriepengeberegner.feriepengedatoer().let { datoer -> "Datoer vi skal utbetale feriepenger for (${datoer.size}): ${datoer.grupperSammenhengendePerioder()}"}}
                
                - OPPDRAG:
                Skal sende arbeidsgiveroppdrag til OS: $sendTilOppdrag
                Differanse fra forrige sendte arbeidsgoiveroppdrag: ${forrigeSendteOppdrag?.totalbeløp()?.minus(oppdrag.totalbeløp())}
                Arbeidsgiveroppdrag: ${oppdrag.toHendelseMap()}
                
                Skal sende personoppdrag til OS: $sendPersonoppdragTilOS
                Differanse fra forrige sendte personoppdrag: ${forrigeSendteOppdragForPerson?.totalbeløp()?.minus(personoppdrag.totalbeløp())}
                Personoppdrag: ${personoppdrag.toHendelseMap()}
                """.trimIndent()
            )

            return Feriepengeutbetaling(
                feriepengeberegner = feriepengeberegner,
                infotrygdFeriepengebeløpPerson = infotrygdFeriepengebeløpPerson,
                infotrygdFeriepengebeløpArbeidsgiver = infotrygdFeriepengebeløpArbeidsgiver,
                spleisFeriepengebeløpArbeidsgiver = spleisFeriepengebeløpArbeidsgiver,
                spleisFeriepengebeløpPerson = spleisFeriepengebeløpPerson,
                oppdrag = oppdrag,
                personoppdrag = personoppdrag,
                utbetalingId = UUID.randomUUID(),
                sendTilOppdrag = sendTilOppdrag,
                sendPersonoppdragTilOS = sendPersonoppdragTilOS,
            )
        }
    }
}
