package no.nav.helse.spleis.spanner

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.YearMonth
import java.util.UUID
import no.nav.helse.dto.ArbeidsgiverInntektsopplysningDto
import no.nav.helse.dto.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagDto
import no.nav.helse.dto.AvsenderDto
import no.nav.helse.dto.BegrunnelseDto
import no.nav.helse.dto.DokumentsporingDto
import no.nav.helse.dto.DokumenttypeDto
import no.nav.helse.dto.EndringIRefusjonDto
import no.nav.helse.dto.EndringskodeDto
import no.nav.helse.dto.FagområdeDto
import no.nav.helse.dto.GenerasjonTilstandDto
import no.nav.helse.dto.GenerasjonkildeDto
import no.nav.helse.dto.HendelseskildeDto
import no.nav.helse.dto.InfotrygdArbeidsgiverutbetalingsperiodeDto
import no.nav.helse.dto.InfotrygdFerieperiodeDto
import no.nav.helse.dto.InfotrygdInntektsopplysningDto
import no.nav.helse.dto.InfotrygdPersonutbetalingsperiodeDto
import no.nav.helse.dto.InfotrygdhistorikkelementDto
import no.nav.helse.dto.InntektDto
import no.nav.helse.dto.InntektsopplysningDto
import no.nav.helse.dto.InntekttypeDto
import no.nav.helse.dto.KlassekodeDto
import no.nav.helse.dto.MedlemskapsvurderingDto
import no.nav.helse.dto.OppdragstatusDto
import no.nav.helse.dto.OpptjeningDto
import no.nav.helse.dto.RefusjonDto
import no.nav.helse.dto.RefusjonsopplysningDto
import no.nav.helse.dto.SammenligningsgrunnlagDto
import no.nav.helse.dto.SatstypeDto
import no.nav.helse.dto.SkatteopplysningDto
import no.nav.helse.dto.SykdomshistorikkElementDto
import no.nav.helse.dto.SykdomstidslinjeDagDto
import no.nav.helse.dto.SykdomstidslinjeDto
import no.nav.helse.dto.SykmeldingsperioderDto
import no.nav.helse.dto.UtbetalingTilstandDto
import no.nav.helse.dto.UtbetalingVurderingDto
import no.nav.helse.dto.UtbetalingsdagDto
import no.nav.helse.dto.UtbetalingstidslinjeDto
import no.nav.helse.dto.UtbetalingtypeDto
import no.nav.helse.dto.UtbetaltDagDto
import no.nav.helse.dto.VedtaksperiodetilstandDto
import no.nav.helse.dto.serialisering.ArbeidsgiverUtDto
import no.nav.helse.dto.serialisering.FeriepengeUtDto
import no.nav.helse.dto.serialisering.ForkastetVedtaksperiodeUtDto
import no.nav.helse.dto.serialisering.GenerasjonEndringUtDto
import no.nav.helse.dto.serialisering.GenerasjonUtDto
import no.nav.helse.dto.serialisering.OppdragUtDto
import no.nav.helse.dto.serialisering.PersonUtDto
import no.nav.helse.dto.serialisering.SykepengegrunnlagUtDto
import no.nav.helse.dto.serialisering.UtbetalingUtDto
import no.nav.helse.dto.serialisering.UtbetalingslinjeUtDto
import no.nav.helse.dto.serialisering.VedtaksperiodeUtDto
import no.nav.helse.dto.serialisering.VilkårsgrunnlagInnslagUtDto
import no.nav.helse.dto.serialisering.VilkårsgrunnlagUtDto
import no.nav.helse.dto.SimuleringResultatDto
import no.nav.helse.nesteDag
import no.nav.helse.person.TilstandType

internal data class SpannerPersonDto(
    val aktørId: String,
    val fødselsnummer: String,
    val fødselsdato: LocalDate,
    val arbeidsgivere: List<ArbeidsgiverData>,
    val opprettet: LocalDateTime,
    val infotrygdhistorikk: List<InfotrygdhistorikkElementData>,
    val vilkårsgrunnlagHistorikk: List<VilkårsgrunnlagInnslagData>,
    val dødsdato: LocalDate?
) {
    data class InfotrygdhistorikkElementData(
        val id: UUID,
        val tidsstempel: LocalDateTime,
        val hendelseId: UUID?,
        val ferieperioder: List<FerieperiodeData>,
        val arbeidsgiverutbetalingsperioder: List<ArbeidsgiverutbetalingsperiodeData>,
        val personutbetalingsperioder: List<PersonutbetalingsperiodeData>,
        val inntekter: List<InntektsopplysningData>,
        val arbeidskategorikoder: Map<String, LocalDate>,
        val oppdatert: LocalDateTime
    ) {
        data class FerieperiodeData(
            val fom: LocalDate,
            val tom: LocalDate
        )
        data class PersonutbetalingsperiodeData(
            val orgnr: String,
            val fom: LocalDate,
            val tom: LocalDate,
            val grad: Double,
            val inntekt: Int
        )
        data class ArbeidsgiverutbetalingsperiodeData(
            val orgnr: String,
            val fom: LocalDate,
            val tom: LocalDate,
            val grad: Double,
            val inntekt: Int
        )

        data class InntektsopplysningData(
            val orgnr: String,
            val sykepengerFom: LocalDate,
            val inntekt: Double,
            val refusjonTilArbeidsgiver: Boolean,
            val refusjonTom: LocalDate?,
            val lagret: LocalDateTime?
        )
    }

    data class VilkårsgrunnlagInnslagData(
        val id: UUID,
        val opprettet: LocalDateTime,
        val vilkårsgrunnlag: List<VilkårsgrunnlagElementData>
    )

    data class VilkårsgrunnlagElementData(
        val skjæringstidspunkt: LocalDate,
        val type: GrunnlagsdataType,
        val sykepengegrunnlag: SykepengegrunnlagData,
        val opptjening: OpptjeningData?,
        val medlemskapstatus: MedlemskapstatusDto?,
        val vurdertOk: Boolean?,
        val meldingsreferanseId: UUID?,
        val vilkårsgrunnlagId: UUID
    ) {
        internal enum class MedlemskapstatusDto { JA, VET_IKKE, NEI, UAVKLART_MED_BRUKERSPØRSMÅL }
        enum class GrunnlagsdataType { Infotrygd, Vilkårsprøving }

        data class SykepengegrunnlagData(
            val grunnbeløp: Double?,
            val arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysningData>,
            val sammenligningsgrunnlag: SammenligningsgrunnlagData?,
            val deaktiverteArbeidsforhold: List<ArbeidsgiverInntektsopplysningData>,
            val vurdertInfotrygd: Boolean,
            val totalOmregnetÅrsinntekt: InntektDto.Årlig,
            val beregningsgrunnlag: InntektDto.Årlig,
            val er6GBegrenset: Boolean,
            val forhøyetInntektskrav: Boolean,
            val minsteinntekt: InntektDto.Årlig,
            val oppfyllerMinsteinntektskrav: Boolean
        )
        data class SammenligningsgrunnlagData(
            val sammenligningsgrunnlag: Double,
            val arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagData>,
        )

        data class ArbeidsgiverInntektsopplysningData(
            val orgnummer: String,
            val fom: LocalDate,
            val tom: LocalDate,
            val inntektsopplysning: InntektsopplysningData,
            val refusjonsopplysninger: List<ArbeidsgiverData.RefusjonsopplysningData>
        ) {
            data class SkatteopplysningData(
                val hendelseId: UUID,
                val beløp: Double,
                val måned: YearMonth,
                val type: InntekttypeData,
                val fordel: String,
                val beskrivelse: String,
                val tidsstempel: LocalDateTime
            ) {
                enum class InntekttypeData {
                    LØNNSINNTEKT,
                    NÆRINGSINNTEKT,
                    PENSJON_ELLER_TRYGD,
                    YTELSE_FRA_OFFENTLIGE
                }
            }

            data class InntektsopplysningData(
                val id: UUID,
                val dato: LocalDate,
                val hendelseId: UUID,
                val beløp: Double?,
                val kilde: String,
                val forklaring: String?,
                val subsumsjon: SubsumsjonData?,
                val tidsstempel: LocalDateTime,
                val overstyrtInntektId: UUID?,
                val skatteopplysninger: List<SkatteopplysningData>?
            ) {
                data class SubsumsjonData(
                    val paragraf: String,
                    val ledd: Int?,
                    val bokstav: String?
                )
            }
        }

        data class ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagData(
            val orgnummer: String,
            val skatteopplysninger: List<SammenligningsgrunnlagInntektsopplysningData>
        ) {
            data class SammenligningsgrunnlagInntektsopplysningData(
                val hendelseId: UUID,
                val beløp: Double,
                val måned: YearMonth,
                val type: InntekttypeData,
                val fordel: String,
                val beskrivelse: String,
                val tidsstempel: LocalDateTime,
            ) {
                internal enum class InntekttypeData { LØNNSINNTEKT, NÆRINGSINNTEKT, PENSJON_ELLER_TRYGD, YTELSE_FRA_OFFENTLIGE }
            }
        }

        data class OpptjeningData(
            val opptjeningFom: LocalDate,
            val opptjeningTom: LocalDate,
            val arbeidsforhold: List<ArbeidsgiverOpptjeningsgrunnlagData>
        ) {
            data class ArbeidsgiverOpptjeningsgrunnlagData(
                val orgnummer: String,
                val ansattPerioder: List<ArbeidsforholdData>
            ) {
                data class ArbeidsforholdData(
                    val ansattFom: LocalDate,
                    val ansattTom: LocalDate?,
                    val deaktivert: Boolean
                )
            }
        }
    }

    data class ArbeidsgiverData(
        val organisasjonsnummer: String,
        val id: UUID,
        val inntektshistorikk: List<InntektsmeldingData>,
        val sykdomshistorikk: List<SykdomshistorikkData>,
        val sykmeldingsperioder: List<SykmeldingsperiodeData>,
        val vedtaksperioder: List<VedtaksperiodeData>,
        val forkastede: List<ForkastetVedtaksperiodeData>,
        val utbetalinger: List<UtbetalingData>,
        val feriepengeutbetalinger: List<FeriepengeutbetalingData>,
        val refusjonshistorikk: List<RefusjonData>
    ) {
        data class InntektsmeldingData(
            val id: UUID,
            val dato: LocalDate,
            val hendelseId: UUID,
            val beløp: Double,
            val tidsstempel: LocalDateTime
        )

        data class RefusjonsopplysningData(
            val meldingsreferanseId: UUID,
            val fom: LocalDate,
            val tom: LocalDate?,
            val beløp: Double
        )

        data class PeriodeData(val fom: LocalDate, val tom: LocalDate)
        data class SykdomstidslinjeData(
            val dager: List<DagData>,
            val periode: PeriodeData?,
            val låstePerioder: List<PeriodeData>?
        ) {
            data class DagData(
                val type: JsonDagType,
                val kilde: KildeData,
                val grad: Double,
                val other: KildeData?,
                val melding: String?,
                val fom: LocalDate?,
                val tom: LocalDate?,
                val dato: LocalDate?
            ) {
                init {
                    check (dato != null || (fom != null && tom != null)) {
                        "enten må dato være satt eller så må både fom og tom være satt"
                    }
                }
            }

            enum class JsonDagType {
                ARBEIDSDAG,
                ARBEIDSGIVERDAG,

                FERIEDAG,
                ARBEID_IKKE_GJENOPPTATT_DAG,
                FRISK_HELGEDAG,
                FORELDET_SYKEDAG,
                PERMISJONSDAG,
                PROBLEMDAG,
                SYKEDAG,
                SYKEDAG_NAV,
                ANDRE_YTELSER_FORELDREPENGER,
                ANDRE_YTELSER_AAP,
                ANDRE_YTELSER_OMSORGSPENGER,
                ANDRE_YTELSER_PLEIEPENGER,
                ANDRE_YTELSER_SVANGERSKAPSPENGER,
                ANDRE_YTELSER_OPPLÆRINGSPENGER,
                ANDRE_YTELSER_DAGPENGER,

                UKJENT_DAG
            }

            data class KildeData(
                val type: String,
                val id: UUID,
                val tidsstempel: LocalDateTime
            )
        }

        data class ForkastetVedtaksperiodeData(
            val vedtaksperiode: VedtaksperiodeData
        )

        data class FeriepengeutbetalingData(
            val infotrygdFeriepengebeløpPerson: Double,
            val infotrygdFeriepengebeløpArbeidsgiver: Double,
            val spleisFeriepengebeløpArbeidsgiver: Double,
            val spleisFeriepengebeløpPerson: Double,
            val oppdrag: OppdragData,
            val personoppdrag: OppdragData,
            val opptjeningsår: Year,
            val utbetalteDager: List<UtbetaltDagData>,
            val feriepengedager: List<UtbetaltDagData>,
            val utbetalingId: UUID,
            val sendTilOppdrag: Boolean,
            val sendPersonoppdragTilOS: Boolean,
        ) {
            data class UtbetaltDagData(
                val type: String,
                val orgnummer: String,
                val dato: LocalDate,
                val beløp: Int,
            )
        }

        data class SykmeldingsperiodeData(
            val fom: LocalDate,
            val tom: LocalDate
        )

        data class VedtaksperiodeData(
            val id: UUID,
            val tilstand: TilstandType,
            val skjæringstidspunkt: LocalDate,
            val fom: LocalDate,
            val tom: LocalDate,
            val sykmeldingFom: LocalDate,
            val sykmeldingTom: LocalDate,
            val generasjoner: List<GenerasjonData>,
            val opprettet: LocalDateTime,
            val oppdatert: LocalDateTime
        ) {
            data class DokumentsporingData(
                val dokumentId: UUID,
                val dokumenttype: DokumentTypeData
            )
            enum class DokumentTypeData {
                Sykmelding,
                Søknad,
                InntektsmeldingInntekt,
                InntektsmeldingDager,
                OverstyrTidslinje,
                OverstyrInntekt,
                OverstyrRefusjon,
                OverstyrArbeidsgiveropplysninger,
                OverstyrArbeidsforhold,
                SkjønnsmessigFastsettelse
            }

            data class GenerasjonData(
                val id: UUID,
                val tilstand: TilstandData,
                val vedtakFattet: LocalDateTime?,
                val avsluttet: LocalDateTime?,
                val kilde: KildeData,
                val endringer: List<EndringData>
            ) {
                internal enum class TilstandData {
                    UBEREGNET, UBEREGNET_OMGJØRING, UBEREGNET_REVURDERING, BEREGNET, BEREGNET_OMGJØRING, BEREGNET_REVURDERING, VEDTAK_FATTET, REVURDERT_VEDTAK_AVVIST, VEDTAK_IVERKSATT, AVSLUTTET_UTEN_VEDTAK, TIL_INFOTRYGD
                }
                internal enum class AvsenderData {
                    SYKMELDT, ARBEIDSGIVER, SAKSBEHANDLER, SYSTEM
                }

                internal data class KildeData(
                    val meldingsreferanseId: UUID,
                    val innsendt: LocalDateTime,
                    val registrert: LocalDateTime,
                    val avsender: AvsenderData
                )

                data class EndringData(
                    val id: UUID,
                    val tidsstempel: LocalDateTime,
                    val sykmeldingsperiodeFom: LocalDate,
                    val sykmeldingsperiodeTom: LocalDate,
                    val fom: LocalDate,
                    val tom: LocalDate,
                    val utbetalingId: UUID?,
                    val skjæringstidspunkt: LocalDate?,
                    val utbetalingstatus: UtbetalingData.UtbetalingstatusData?,
                    val vilkårsgrunnlagId: UUID?,
                    val sykdomstidslinje: SykdomstidslinjeData,
                    val dokumentsporing: DokumentsporingData
                )
            }
            data class DataForSimuleringData(
                val totalbeløp: Int,
                val perioder: List<SimulertPeriode>
            ) {
                data class SimulertPeriode(
                    val fom: LocalDate,
                    val tom: LocalDate,
                    val utbetalinger: List<SimulertUtbetaling>
                )

                data class SimulertUtbetaling(
                    val forfallsdato: LocalDate,
                    val utbetalesTil: Mottaker,
                    val feilkonto: Boolean,
                    val detaljer: List<Detaljer>
                )

                data class Detaljer(
                    val fom: LocalDate,
                    val tom: LocalDate,
                    val konto: String,
                    val beløp: Int,
                    val klassekode: Klassekode,
                    val uføregrad: Int,
                    val utbetalingstype: String,
                    val tilbakeføring: Boolean,
                    val sats: Sats,
                    val refunderesOrgnummer: String
                )

                data class Sats(
                    val sats: Double,
                    val antall: Int,
                    val type: String
                )

                data class Klassekode(
                    val kode: String,
                    val beskrivelse: String
                )

                data class Mottaker(
                    val id: String,
                    val navn: String
                )
            }
        }

        data class RefusjonData(
            val meldingsreferanseId: UUID,
            val førsteFraværsdag: LocalDate?,
            val arbeidsgiverperioder: List<PeriodeData>,
            val beløp: Double?,
            val sisteRefusjonsdag: LocalDate?,
            val endringerIRefusjon: List<EndringIRefusjonData>,
            val tidsstempel: LocalDateTime
        ) {
            data class EndringIRefusjonData(
                val beløp: Double,
                val endringsdato: LocalDate
            )
        }
    }

    data class SykdomshistorikkData(
        val tidsstempel: LocalDateTime,
        val id: UUID,
        val hendelseId: UUID?,
        val hendelseSykdomstidslinje: ArbeidsgiverData.SykdomstidslinjeData,
        val beregnetSykdomstidslinje: ArbeidsgiverData.SykdomstidslinjeData
    )

    data class UtbetalingData(
        val id: UUID,
        val korrelasjonsId: UUID,
        val fom: LocalDate,
        val tom: LocalDate,
        val annulleringer: List<UUID>?,
        val utbetalingstidslinje: UtbetalingstidslinjeData,
        val arbeidsgiverOppdrag: OppdragData,
        val personOppdrag: OppdragData,
        val tidsstempel: LocalDateTime,
        val type: UtbetalingtypeData,
        val status: UtbetalingstatusData,
        val maksdato: LocalDate,
        val forbrukteSykedager: Int?,
        val gjenståendeSykedager: Int?,
        val vurdering: VurderingData?,
        val overføringstidspunkt: LocalDateTime?,
        val avstemmingsnøkkel: Long?,
        val avsluttet: LocalDateTime?,
        val oppdatert: LocalDateTime
    ) {
        enum class UtbetalingtypeData { UTBETALING, ETTERUTBETALING, ANNULLERING, REVURDERING, FERIEPENGER }
        enum class UtbetalingstatusData {
            NY,
            IKKE_UTBETALT,
            IKKE_GODKJENT,
            OVERFØRT,
            UTBETALT,
            GODKJENT,
            GODKJENT_UTEN_UTBETALING,
            ANNULLERT,
            FORKASTET
        }
        data class VurderingData(
            val godkjent: Boolean,
            val ident: String,
            val epost: String,
            val tidspunkt: LocalDateTime,
            val automatiskBehandling: Boolean
        )
    }

    data class OppdragData(
        val mottaker: String,
        val fagområde: String,
        val linjer: List<UtbetalingslinjeData>,
        val fagsystemId: String,
        val endringskode: String,
        val tidsstempel: LocalDateTime,
        val nettoBeløp: Int,
        val stønadsdager: Int,
        val totalbeløp: Int,
        val avstemmingsnøkkel: Long?,
        val status: OppdragstatusData?,
        val overføringstidspunkt: LocalDateTime?,
        val erSimulert: Boolean,
        val simuleringsResultat: ArbeidsgiverData.VedtaksperiodeData.DataForSimuleringData?
    ) {
        enum class OppdragstatusData { OVERFØRT, AKSEPTERT, AKSEPTERT_MED_FEIL, AVVIST, FEIL }
    }

    data class UtbetalingslinjeData(
        val fom: LocalDate,
        val tom: LocalDate,
        val satstype: String,
        val sats: Int,
        val grad: Int?,
        val stønadsdager: Int,
        val totalbeløp: Int,
        val refFagsystemId: String?,
        val delytelseId: Int,
        val refDelytelseId: Int?,
        val endringskode: String,
        val klassekode: String,
        val datoStatusFom: LocalDate?,
        val statuskode: String?
    )

    data class UtbetalingstidslinjeData(
        val dager: List<UtbetalingsdagData>
    ) {
        enum class BegrunnelseData {
            SykepengedagerOppbrukt,
            SykepengedagerOppbruktOver67,
            MinimumInntekt,
            MinimumInntektOver67,
            EgenmeldingUtenforArbeidsgiverperiode,
            MinimumSykdomsgrad,
            AndreYtelserAap,
            AndreYtelserDagpenger,
            AndreYtelserForeldrepenger,
            AndreYtelserOmsorgspenger,
            AndreYtelserOpplaringspenger,
            AndreYtelserPleiepenger,
            AndreYtelserSvangerskapspenger,
            EtterDødsdato,
            ManglerMedlemskap,
            ManglerOpptjening,
            Over70,
            NyVilkårsprøvingNødvendig
        }

        enum class TypeData {
            ArbeidsgiverperiodeDag,
            NavDag,
            NavHelgDag,
            Arbeidsdag,
            Fridag,
            AvvistDag,
            UkjentDag,
            ForeldetDag,
            ArbeidsgiverperiodedagNav
        }

        data class UtbetalingsdagData(
            val type: TypeData,
            val aktuellDagsinntekt: Double,
            val beregningsgrunnlag: Double,
            val dekningsgrunnlag: Double,
            val grunnbeløpgrense: Double?,
            val begrunnelser: List<BegrunnelseData>?,
            val grad: Double,
            val totalGrad: Double,
            val arbeidsgiverRefusjonsbeløp: Double,
            val arbeidsgiverbeløp: Double?,
            val personbeløp: Double?,
            val er6GBegrenset: Boolean?,
            val dato: LocalDate?,
            val fom: LocalDate?,
            val tom: LocalDate?
        )
    }
}

internal fun PersonUtDto.tilSpannerPersonDto() = SpannerPersonDto(
    aktørId = this.aktørId,
    fødselsdato = this.alder.fødselsdato,
    fødselsnummer = this.fødselsnummer,
    opprettet = this.opprettet,
    arbeidsgivere = this.arbeidsgivere.map { it.tilPersonData() },
    infotrygdhistorikk = this.infotrygdhistorikk.elementer.map { it.tilPersonData() },
    vilkårsgrunnlagHistorikk = vilkårsgrunnlagHistorikk.historikk.map { it.tilPersonData() },
    dødsdato = this.alder.dødsdato
)

private fun ArbeidsgiverUtDto.tilPersonData() = SpannerPersonDto.ArbeidsgiverData(
    id = this.id,
    organisasjonsnummer = this.organisasjonsnummer,
    inntektshistorikk = this.inntektshistorikk.historikk.map { it.tilPersonData() },
    sykdomshistorikk = this.sykdomshistorikk.elementer.map { it.tilPersonData() },
    sykmeldingsperioder = this.sykmeldingsperioder.tilPersonData(),
    vedtaksperioder = this.vedtaksperioder.map { it.tilPersonData() },
    forkastede = this.forkastede.map { it.tilPersonData() },
    utbetalinger = this.utbetalinger.map { it.tilPersonData() },
    feriepengeutbetalinger = this.feriepengeutbetalinger.map { it.tilPersonData() },
    refusjonshistorikk = this.refusjonshistorikk.refusjoner.map { it.tilPersonData() }
)

private fun InntektsopplysningDto.InntektsmeldingDto.tilPersonData() = SpannerPersonDto.ArbeidsgiverData.InntektsmeldingData(
    id = this.id,
    dato = this.dato,
    hendelseId = this.hendelseId,
    beløp = this.beløp.beløp,
    tidsstempel = this.tidsstempel
)

private fun SykdomshistorikkElementDto.tilPersonData() = SpannerPersonDto.SykdomshistorikkData(
    id = this.id,
    tidsstempel = this.tidsstempel,
    hendelseId = this.hendelseId,
    hendelseSykdomstidslinje = this.hendelseSykdomstidslinje.tilPersonData(),
    beregnetSykdomstidslinje = this.beregnetSykdomstidslinje.tilPersonData(),
)
private fun SykdomstidslinjeDto.tilPersonData() = SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData(
    dager = dager.map { it.tilPersonData() }.forkortSykdomstidslinje(),
    låstePerioder = this.låstePerioder.map { SpannerPersonDto.ArbeidsgiverData.PeriodeData(it.fom, it.tom) },
    periode = this.periode?.let { SpannerPersonDto.ArbeidsgiverData.PeriodeData(it.fom, it.tom) }
)

private fun List<SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData>.forkortSykdomstidslinje(): List<SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData> {
    return this.fold(emptyList()) { result, neste ->
        val slåttSammen = result.lastOrNull()?.utvideMed(neste) ?: return@fold result + neste
        result.dropLast(1) + slåttSammen
    }
}

private fun SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData.utvideMed(other: SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData): SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData? {
    if (!kanUtvidesMed(other)) return null
    val otherDato = checkNotNull(other.dato) { "dato må være satt" }
    if (this.dato != null) {
        return this.copy(dato = null, fom = dato, tom = otherDato)
    }
    return this.copy(tom = other.dato)
}

private fun SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData.kanUtvidesMed(other: SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData): Boolean {
    // alle verdier må være like (untatt datoene)
    val utenDatoer = { dag: SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData -> dag.copy(fom = null, tom = null, dato = LocalDate.EPOCH) }
    return utenDatoer(this) == utenDatoer(other) && (dato ?: tom!!).nesteDag == other.dato
}

private fun SykdomstidslinjeDagDto.tilPersonData() = when (this) {
    is SykdomstidslinjeDagDto.UkjentDagDto -> SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData(
        type = SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.UKJENT_DAG,
        kilde = this.kilde.tilPersonData(),
        grad = 0.0,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.AndreYtelserDto -> SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData(
        type = when (this.ytelse) {
            SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto.AAP -> SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ANDRE_YTELSER_AAP
            SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto.Foreldrepenger -> SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ANDRE_YTELSER_FORELDREPENGER
            SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto.Omsorgspenger -> SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ANDRE_YTELSER_OMSORGSPENGER
            SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto.Pleiepenger -> SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ANDRE_YTELSER_PLEIEPENGER
            SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto.Svangerskapspenger -> SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ANDRE_YTELSER_SVANGERSKAPSPENGER
            SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto.Opplæringspenger -> SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ANDRE_YTELSER_OPPLÆRINGSPENGER
            SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto.Dagpenger -> SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ANDRE_YTELSER_DAGPENGER
        },
        kilde = this.kilde.tilPersonData(),
        grad = 0.0,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.ArbeidIkkeGjenopptattDagDto -> SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData(
        type = SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ARBEID_IKKE_GJENOPPTATT_DAG,
        kilde = this.kilde.tilPersonData(),
        grad = 0.0,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.ArbeidsdagDto -> SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData(
        type = SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ARBEIDSDAG,
        kilde = this.kilde.tilPersonData(),
        grad = 0.0,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.ArbeidsgiverHelgedagDto -> SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData(
        type = SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ARBEIDSGIVERDAG,
        kilde = this.kilde.tilPersonData(),
        grad = this.grad.prosent,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.ArbeidsgiverdagDto -> SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData(
        type = SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ARBEIDSGIVERDAG,
        kilde = this.kilde.tilPersonData(),
        grad = this.grad.prosent,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.FeriedagDto -> SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData(
        type = SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.FERIEDAG,
        kilde = this.kilde.tilPersonData(),
        grad = 0.0,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.ForeldetSykedagDto -> SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData(
        type = SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.FORELDET_SYKEDAG,
        kilde = this.kilde.tilPersonData(),
        grad = this.grad.prosent,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.FriskHelgedagDto -> SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData(
        type = SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.FRISK_HELGEDAG,
        kilde = this.kilde.tilPersonData(),
        grad = 0.0,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.PermisjonsdagDto -> SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData(
        type = SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.PERMISJONSDAG,
        kilde = this.kilde.tilPersonData(),
        grad = 0.0,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.ProblemDagDto -> SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData(
        type = SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.PROBLEMDAG,
        kilde = this.kilde.tilPersonData(),
        grad = 0.0,
        other = this.other.tilPersonData(),
        melding = this.melding,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.SykHelgedagDto -> SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData(
        type = SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.SYKEDAG,
        kilde = this.kilde.tilPersonData(),
        grad = this.grad.prosent,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.SykedagDto -> SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData(
        type = SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.SYKEDAG,
        kilde = this.kilde.tilPersonData(),
        grad = this.grad.prosent,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.SykedagNavDto -> SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.DagData(
        type = SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.SYKEDAG_NAV,
        kilde = this.kilde.tilPersonData(),
        grad = this.grad.prosent,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
}
private fun HendelseskildeDto.tilPersonData() = SpannerPersonDto.ArbeidsgiverData.SykdomstidslinjeData.KildeData(
    type = this.type,
    id = this.meldingsreferanseId,
    tidsstempel = this.tidsstempel
)
private fun RefusjonDto.tilPersonData() = SpannerPersonDto.ArbeidsgiverData.RefusjonData(
    meldingsreferanseId = this.meldingsreferanseId,
    førsteFraværsdag = this.førsteFraværsdag,
    arbeidsgiverperioder = this.arbeidsgiverperioder.map { SpannerPersonDto.ArbeidsgiverData.PeriodeData(it.fom, it.tom) },
    beløp = this.beløp?.beløp,
    sisteRefusjonsdag = this.sisteRefusjonsdag,
    endringerIRefusjon = this.endringerIRefusjon.map { it.tilPersonData() },
    tidsstempel = this.tidsstempel
)
private fun EndringIRefusjonDto.tilPersonData() = SpannerPersonDto.ArbeidsgiverData.RefusjonData.EndringIRefusjonData(
    beløp = this.beløp.beløp,
    endringsdato = this.endringsdato
)

private fun SykmeldingsperioderDto.tilPersonData() = perioder.map {
    SpannerPersonDto.ArbeidsgiverData.SykmeldingsperiodeData(it.fom, it.tom)
}
private fun ForkastetVedtaksperiodeUtDto.tilPersonData() = SpannerPersonDto.ArbeidsgiverData.ForkastetVedtaksperiodeData(
    vedtaksperiode = this.vedtaksperiode.tilPersonData()
)
private fun VedtaksperiodeUtDto.tilPersonData() = SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData(
    id = id,
    tilstand = when (tilstand) {
        VedtaksperiodetilstandDto.AVSLUTTET -> TilstandType.AVSLUTTET
        VedtaksperiodetilstandDto.AVSLUTTET_UTEN_UTBETALING -> TilstandType.AVSLUTTET_UTEN_UTBETALING
        VedtaksperiodetilstandDto.AVVENTER_BLOKKERENDE_PERIODE -> TilstandType.AVVENTER_BLOKKERENDE_PERIODE
        VedtaksperiodetilstandDto.AVVENTER_GODKJENNING -> TilstandType.AVVENTER_GODKJENNING
        VedtaksperiodetilstandDto.AVVENTER_GODKJENNING_REVURDERING -> TilstandType.AVVENTER_GODKJENNING_REVURDERING
        VedtaksperiodetilstandDto.AVVENTER_HISTORIKK -> TilstandType.AVVENTER_HISTORIKK
        VedtaksperiodetilstandDto.AVVENTER_HISTORIKK_REVURDERING -> TilstandType.AVVENTER_HISTORIKK_REVURDERING
        VedtaksperiodetilstandDto.AVVENTER_INFOTRYGDHISTORIKK -> TilstandType.AVVENTER_INFOTRYGDHISTORIKK
        VedtaksperiodetilstandDto.AVVENTER_INNTEKTSMELDING -> TilstandType.AVVENTER_INNTEKTSMELDING
        VedtaksperiodetilstandDto.AVVENTER_REVURDERING -> TilstandType.AVVENTER_REVURDERING
        VedtaksperiodetilstandDto.AVVENTER_SIMULERING -> TilstandType.AVVENTER_SIMULERING
        VedtaksperiodetilstandDto.AVVENTER_SIMULERING_REVURDERING -> TilstandType.AVVENTER_SIMULERING_REVURDERING
        VedtaksperiodetilstandDto.AVVENTER_VILKÅRSPRØVING -> TilstandType.AVVENTER_VILKÅRSPRØVING
        VedtaksperiodetilstandDto.AVVENTER_VILKÅRSPRØVING_REVURDERING -> TilstandType.AVVENTER_VILKÅRSPRØVING_REVURDERING
        VedtaksperiodetilstandDto.REVURDERING_FEILET -> TilstandType.REVURDERING_FEILET
        VedtaksperiodetilstandDto.START -> TilstandType.START
        VedtaksperiodetilstandDto.TIL_INFOTRYGD -> TilstandType.TIL_INFOTRYGD
        VedtaksperiodetilstandDto.TIL_UTBETALING -> TilstandType.TIL_UTBETALING
    },
    generasjoner = generasjoner.generasjoner.map { it.tilPersonData() },
    opprettet = opprettet,
    oppdatert = oppdatert,
    skjæringstidspunkt = skjæringstidspunkt,
    fom = fom,
    tom = tom,
    sykmeldingFom = sykmeldingFom,
    sykmeldingTom = sykmeldingTom
)
private fun GenerasjonUtDto.tilPersonData() = SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.GenerasjonData(
    id = this.id,
    tilstand = when (this.tilstand) {
        GenerasjonTilstandDto.ANNULLERT_PERIODE -> error("Forventer ikke å serialisere ${this.tilstand}")
        GenerasjonTilstandDto.AVSLUTTET_UTEN_VEDTAK -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.GenerasjonData.TilstandData.AVSLUTTET_UTEN_VEDTAK
        GenerasjonTilstandDto.BEREGNET -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.GenerasjonData.TilstandData.BEREGNET
        GenerasjonTilstandDto.BEREGNET_OMGJØRING -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.GenerasjonData.TilstandData.BEREGNET_OMGJØRING
        GenerasjonTilstandDto.BEREGNET_REVURDERING -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.GenerasjonData.TilstandData.BEREGNET_REVURDERING
        GenerasjonTilstandDto.REVURDERT_VEDTAK_AVVIST -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.GenerasjonData.TilstandData.REVURDERT_VEDTAK_AVVIST
        GenerasjonTilstandDto.TIL_INFOTRYGD -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.GenerasjonData.TilstandData.TIL_INFOTRYGD
        GenerasjonTilstandDto.UBEREGNET -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.GenerasjonData.TilstandData.UBEREGNET
        GenerasjonTilstandDto.UBEREGNET_OMGJØRING -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.GenerasjonData.TilstandData.UBEREGNET_OMGJØRING
        GenerasjonTilstandDto.UBEREGNET_REVURDERING -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.GenerasjonData.TilstandData.UBEREGNET_REVURDERING
        GenerasjonTilstandDto.VEDTAK_FATTET -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.GenerasjonData.TilstandData.VEDTAK_FATTET
        GenerasjonTilstandDto.VEDTAK_IVERKSATT -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.GenerasjonData.TilstandData.VEDTAK_IVERKSATT
    },
    vedtakFattet = this.vedtakFattet,
    avsluttet = this.avsluttet,
    kilde = this.kilde.tilPersonData(),
    endringer = this.endringer.map { it.tilPersonData() }
)
private fun GenerasjonkildeDto.tilPersonData() = SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.GenerasjonData.KildeData(
    meldingsreferanseId = this.meldingsreferanseId,
    innsendt = this.innsendt,
    registrert = this.registert,
    avsender = when (this.avsender) {
        AvsenderDto.ARBEIDSGIVER -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.GenerasjonData.AvsenderData.ARBEIDSGIVER
        AvsenderDto.SAKSBEHANDLER -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.GenerasjonData.AvsenderData.SAKSBEHANDLER
        AvsenderDto.SYKMELDT -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.GenerasjonData.AvsenderData.SYKMELDT
        AvsenderDto.SYSTEM -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.GenerasjonData.AvsenderData.SYSTEM
    }
)
private fun GenerasjonEndringUtDto.tilPersonData() = SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.GenerasjonData.EndringData(
    id = id,
    tidsstempel = tidsstempel,
    sykmeldingsperiodeFom = sykmeldingsperiode.fom,
    sykmeldingsperiodeTom = sykmeldingsperiode.tom,
    fom = periode.fom,
    tom = periode.tom,
    utbetalingId = utbetalingId,
    utbetalingstatus = this.utbetalingstatus?.tilPersonData(),
    skjæringstidspunkt = this.skjæringstidspunkt,
    vilkårsgrunnlagId = vilkårsgrunnlagId,
    sykdomstidslinje = sykdomstidslinje.tilPersonData(),
    dokumentsporing = dokumentsporing.tilPersonData()
)
private fun DokumentsporingDto.tilPersonData() = SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.DokumentsporingData(
    dokumentId = this.id,
    dokumenttype = when (type) {
        DokumenttypeDto.InntektsmeldingDager -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.InntektsmeldingDager
        DokumenttypeDto.InntektsmeldingInntekt -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.InntektsmeldingInntekt
        DokumenttypeDto.OverstyrArbeidsforhold -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.OverstyrArbeidsforhold
        DokumenttypeDto.OverstyrArbeidsgiveropplysninger -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.OverstyrArbeidsgiveropplysninger
        DokumenttypeDto.OverstyrInntekt -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.OverstyrInntekt
        DokumenttypeDto.OverstyrRefusjon -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.OverstyrRefusjon
        DokumenttypeDto.OverstyrTidslinje -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.OverstyrTidslinje
        DokumenttypeDto.SkjønnsmessigFastsettelse -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.SkjønnsmessigFastsettelse
        DokumenttypeDto.Sykmelding -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.Sykmelding
        DokumenttypeDto.Søknad -> SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.Søknad
    }
)
private fun UtbetalingUtDto.tilPersonData() = SpannerPersonDto.UtbetalingData(
    id = this.id,
    korrelasjonsId = this.korrelasjonsId,
    fom = this.periode.fom,
    tom = this.periode.tom,
    annulleringer = this.annulleringer,
    utbetalingstidslinje = this.utbetalingstidslinje.tilPersonData(),
    arbeidsgiverOppdrag = this.arbeidsgiverOppdrag.tilPersonData(),
    personOppdrag = this.personOppdrag.tilPersonData(),
    tidsstempel = this.tidsstempel,
    type = when (this.type) {
        UtbetalingtypeDto.ANNULLERING -> SpannerPersonDto.UtbetalingData.UtbetalingtypeData.ANNULLERING
        UtbetalingtypeDto.ETTERUTBETALING -> SpannerPersonDto.UtbetalingData.UtbetalingtypeData.ETTERUTBETALING
        UtbetalingtypeDto.FERIEPENGER -> SpannerPersonDto.UtbetalingData.UtbetalingtypeData.FERIEPENGER
        UtbetalingtypeDto.REVURDERING -> SpannerPersonDto.UtbetalingData.UtbetalingtypeData.REVURDERING
        UtbetalingtypeDto.UTBETALING -> SpannerPersonDto.UtbetalingData.UtbetalingtypeData.UTBETALING
    },
    status = this.tilstand.tilPersonData(),
    maksdato = this.maksdato,
    forbrukteSykedager = this.forbrukteSykedager,
    gjenståendeSykedager = this.gjenståendeSykedager,
    vurdering = this.vurdering?.tilPersonData(),
    overføringstidspunkt = overføringstidspunkt,
    avstemmingsnøkkel = avstemmingsnøkkel,
    avsluttet = avsluttet,
    oppdatert = oppdatert
)

private fun UtbetalingTilstandDto.tilPersonData() = when (this) {
    UtbetalingTilstandDto.ANNULLERT -> SpannerPersonDto.UtbetalingData.UtbetalingstatusData.ANNULLERT
    UtbetalingTilstandDto.FORKASTET -> SpannerPersonDto.UtbetalingData.UtbetalingstatusData.FORKASTET
    UtbetalingTilstandDto.GODKJENT -> SpannerPersonDto.UtbetalingData.UtbetalingstatusData.GODKJENT
    UtbetalingTilstandDto.GODKJENT_UTEN_UTBETALING -> SpannerPersonDto.UtbetalingData.UtbetalingstatusData.GODKJENT_UTEN_UTBETALING
    UtbetalingTilstandDto.IKKE_GODKJENT -> SpannerPersonDto.UtbetalingData.UtbetalingstatusData.IKKE_GODKJENT
    UtbetalingTilstandDto.IKKE_UTBETALT -> SpannerPersonDto.UtbetalingData.UtbetalingstatusData.IKKE_UTBETALT
    UtbetalingTilstandDto.NY -> SpannerPersonDto.UtbetalingData.UtbetalingstatusData.NY
    UtbetalingTilstandDto.OVERFØRT -> SpannerPersonDto.UtbetalingData.UtbetalingstatusData.OVERFØRT
    UtbetalingTilstandDto.UTBETALT -> SpannerPersonDto.UtbetalingData.UtbetalingstatusData.UTBETALT
}

private fun UtbetalingstidslinjeDto.tilPersonData() = SpannerPersonDto.UtbetalingstidslinjeData(
    dager = this.dager.map { it.tilPersonData() }.forkortUtbetalingstidslinje()
)


private fun List<SpannerPersonDto.UtbetalingstidslinjeData.UtbetalingsdagData>.forkortUtbetalingstidslinje(): List<SpannerPersonDto.UtbetalingstidslinjeData.UtbetalingsdagData> {
    return this.fold(emptyList()) { result, neste ->
        val slåttSammen = result.lastOrNull()?.utvideMed(neste) ?: return@fold result + neste
        result.dropLast(1) + slåttSammen
    }
}

private fun SpannerPersonDto.UtbetalingstidslinjeData.UtbetalingsdagData.utvideMed(other: SpannerPersonDto.UtbetalingstidslinjeData.UtbetalingsdagData): SpannerPersonDto.UtbetalingstidslinjeData.UtbetalingsdagData? {
    if (!kanUtvidesMed(other)) return null
    val otherDato = checkNotNull(other.dato) { "dato må være satt" }
    if (this.dato != null) {
        return this.copy(dato = null, fom = dato, tom = otherDato)
    }
    return this.copy(tom = other.dato)
}

private fun SpannerPersonDto.UtbetalingstidslinjeData.UtbetalingsdagData.kanUtvidesMed(other: SpannerPersonDto.UtbetalingstidslinjeData.UtbetalingsdagData): Boolean {
    // alle verdier må være like (untatt datoene)
    val utenDatoer = { dag: SpannerPersonDto.UtbetalingstidslinjeData.UtbetalingsdagData -> dag.copy(fom = null, tom = null, dato = LocalDate.EPOCH) }
    return utenDatoer(this) == utenDatoer(other) && (dato ?: tom!!).nesteDag == other.dato
}

private fun UtbetalingsdagDto.tilPersonData() = SpannerPersonDto.UtbetalingstidslinjeData.UtbetalingsdagData(
    type = when (this) {
        is UtbetalingsdagDto.ArbeidsdagDto -> SpannerPersonDto.UtbetalingstidslinjeData.TypeData.Arbeidsdag
        is UtbetalingsdagDto.ArbeidsgiverperiodeDagDto -> SpannerPersonDto.UtbetalingstidslinjeData.TypeData.ArbeidsgiverperiodeDag
        is UtbetalingsdagDto.ArbeidsgiverperiodeDagNavDto -> SpannerPersonDto.UtbetalingstidslinjeData.TypeData.ArbeidsgiverperiodedagNav
        is UtbetalingsdagDto.AvvistDagDto -> SpannerPersonDto.UtbetalingstidslinjeData.TypeData.AvvistDag
        is UtbetalingsdagDto.ForeldetDagDto -> SpannerPersonDto.UtbetalingstidslinjeData.TypeData.ForeldetDag
        is UtbetalingsdagDto.FridagDto -> SpannerPersonDto.UtbetalingstidslinjeData.TypeData.Fridag
        is UtbetalingsdagDto.NavDagDto -> SpannerPersonDto.UtbetalingstidslinjeData.TypeData.NavDag
        is UtbetalingsdagDto.NavHelgDagDto -> SpannerPersonDto.UtbetalingstidslinjeData.TypeData.NavHelgDag
        is UtbetalingsdagDto.UkjentDagDto -> SpannerPersonDto.UtbetalingstidslinjeData.TypeData.UkjentDag
    },
    aktuellDagsinntekt = this.økonomi.aktuellDagsinntekt.beløp,
    beregningsgrunnlag = this.økonomi.beregningsgrunnlag.beløp,
    dekningsgrunnlag = this.økonomi.dekningsgrunnlag.beløp,
    grunnbeløpgrense = this.økonomi.grunnbeløpgrense?.beløp,
    begrunnelser = when (this) {
        is UtbetalingsdagDto.AvvistDagDto -> this.begrunnelser.map { it.tilPersonData() }
        else -> null
    },
    grad = this.økonomi.grad.prosent,
    totalGrad = this.økonomi.totalGrad.prosent,
    arbeidsgiverRefusjonsbeløp = økonomi.arbeidsgiverRefusjonsbeløp.beløp,
    arbeidsgiverbeløp = this.økonomi.arbeidsgiverbeløp?.beløp,
    personbeløp = this.økonomi.personbeløp?.beløp,
    er6GBegrenset = this.økonomi.er6GBegrenset,
    dato = this.dato,
    fom = null,
    tom = null
)

private fun BegrunnelseDto.tilPersonData() = when (this) {
    BegrunnelseDto.AndreYtelserAap -> SpannerPersonDto.UtbetalingstidslinjeData.BegrunnelseData.AndreYtelserAap
    BegrunnelseDto.AndreYtelserDagpenger -> SpannerPersonDto.UtbetalingstidslinjeData.BegrunnelseData.AndreYtelserDagpenger
    BegrunnelseDto.AndreYtelserForeldrepenger -> SpannerPersonDto.UtbetalingstidslinjeData.BegrunnelseData.AndreYtelserForeldrepenger
    BegrunnelseDto.AndreYtelserOmsorgspenger -> SpannerPersonDto.UtbetalingstidslinjeData.BegrunnelseData.AndreYtelserOmsorgspenger
    BegrunnelseDto.AndreYtelserOpplaringspenger -> SpannerPersonDto.UtbetalingstidslinjeData.BegrunnelseData.AndreYtelserOpplaringspenger
    BegrunnelseDto.AndreYtelserPleiepenger -> SpannerPersonDto.UtbetalingstidslinjeData.BegrunnelseData.AndreYtelserPleiepenger
    BegrunnelseDto.AndreYtelserSvangerskapspenger -> SpannerPersonDto.UtbetalingstidslinjeData.BegrunnelseData.AndreYtelserSvangerskapspenger
    BegrunnelseDto.EgenmeldingUtenforArbeidsgiverperiode -> SpannerPersonDto.UtbetalingstidslinjeData.BegrunnelseData.EgenmeldingUtenforArbeidsgiverperiode
    BegrunnelseDto.EtterDødsdato -> SpannerPersonDto.UtbetalingstidslinjeData.BegrunnelseData.EtterDødsdato
    BegrunnelseDto.ManglerMedlemskap -> SpannerPersonDto.UtbetalingstidslinjeData.BegrunnelseData.ManglerMedlemskap
    BegrunnelseDto.ManglerOpptjening -> SpannerPersonDto.UtbetalingstidslinjeData.BegrunnelseData.ManglerOpptjening
    BegrunnelseDto.MinimumInntekt -> SpannerPersonDto.UtbetalingstidslinjeData.BegrunnelseData.MinimumInntekt
    BegrunnelseDto.MinimumInntektOver67 -> SpannerPersonDto.UtbetalingstidslinjeData.BegrunnelseData.MinimumInntektOver67
    BegrunnelseDto.MinimumSykdomsgrad -> SpannerPersonDto.UtbetalingstidslinjeData.BegrunnelseData.MinimumSykdomsgrad
    BegrunnelseDto.NyVilkårsprøvingNødvendig -> SpannerPersonDto.UtbetalingstidslinjeData.BegrunnelseData.NyVilkårsprøvingNødvendig
    BegrunnelseDto.Over70 -> SpannerPersonDto.UtbetalingstidslinjeData.BegrunnelseData.Over70
    BegrunnelseDto.SykepengedagerOppbrukt -> SpannerPersonDto.UtbetalingstidslinjeData.BegrunnelseData.SykepengedagerOppbrukt
    BegrunnelseDto.SykepengedagerOppbruktOver67 -> SpannerPersonDto.UtbetalingstidslinjeData.BegrunnelseData.SykepengedagerOppbruktOver67
}

private fun UtbetalingVurderingDto.tilPersonData() = SpannerPersonDto.UtbetalingData.VurderingData(
    godkjent = godkjent,
    ident = ident,
    epost = epost,
    tidspunkt = tidspunkt,
    automatiskBehandling = automatiskBehandling
)
private fun OppdragUtDto.tilPersonData() = SpannerPersonDto.OppdragData(
    mottaker = this.mottaker,
    fagområde = when (this.fagområde) {
        FagområdeDto.SP -> "SP"
        FagområdeDto.SPREF -> "SPREF"
    },
    linjer = this.linjer.map { it.tilPersonData() },
    fagsystemId = this.fagsystemId,
    endringskode = this.endringskode.tilPersonData(),
    tidsstempel = this.tidsstempel,
    nettoBeløp = this.nettoBeløp,
    totalbeløp = this.totalbeløp,
    stønadsdager = this.stønadsdager,
    avstemmingsnøkkel = this.avstemmingsnøkkel,
    status = when (this.status) {
        OppdragstatusDto.AKSEPTERT -> SpannerPersonDto.OppdragData.OppdragstatusData.AKSEPTERT
        OppdragstatusDto.AKSEPTERT_MED_FEIL -> SpannerPersonDto.OppdragData.OppdragstatusData.AKSEPTERT_MED_FEIL
        OppdragstatusDto.AVVIST -> SpannerPersonDto.OppdragData.OppdragstatusData.AVVIST
        OppdragstatusDto.FEIL -> SpannerPersonDto.OppdragData.OppdragstatusData.FEIL
        OppdragstatusDto.OVERFØRT -> SpannerPersonDto.OppdragData.OppdragstatusData.OVERFØRT
        null -> null
    },
    overføringstidspunkt = this.overføringstidspunkt,
    erSimulert = this.erSimulert,
    simuleringsResultat = this.simuleringsResultat?.tilPersonData()
)
private fun EndringskodeDto.tilPersonData() = when (this) {
    EndringskodeDto.ENDR -> "ENDR"
    EndringskodeDto.NY -> "NY"
    EndringskodeDto.UEND -> "UEND"
}
private fun UtbetalingslinjeUtDto.tilPersonData() = SpannerPersonDto.UtbetalingslinjeData(
    fom = this.fom,
    tom = this.tom,
    satstype = when (this.satstype) {
        SatstypeDto.Daglig -> "DAG"
        SatstypeDto.Engang -> "ENG"
    },
    sats = this.beløp!!,
    grad = this.grad,
    totalbeløp = this.totalbeløp,
    stønadsdager = this.stønadsdager,
    refFagsystemId = this.refFagsystemId,
    delytelseId = this.delytelseId,
    refDelytelseId = this.refDelytelseId,
    endringskode = this.endringskode.tilPersonData(),
    klassekode = this.klassekode.tilPersonData(),
    datoStatusFom = this.datoStatusFom,
    statuskode = this.statuskode
)
private fun KlassekodeDto.tilPersonData() = when (this) {
    KlassekodeDto.RefusjonFeriepengerIkkeOpplysningspliktig -> "SPREFAGFER-IOP"
    KlassekodeDto.RefusjonIkkeOpplysningspliktig -> "SPREFAG-IOP"
    KlassekodeDto.SykepengerArbeidstakerFeriepenger -> "SPATFER"
    KlassekodeDto.SykepengerArbeidstakerOrdinær -> "SPATORD"
}
private fun SimuleringResultatDto.tilPersonData() = SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.DataForSimuleringData(
    totalbeløp = this.totalbeløp,
    perioder = this.perioder.map {
        SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.DataForSimuleringData.SimulertPeriode(
            fom = it.fom,
            tom = it.tom,

            utbetalinger = it.utbetalinger.map {
                SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.DataForSimuleringData.SimulertUtbetaling(
                    forfallsdato = it.forfallsdato,
                    utbetalesTil = SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.DataForSimuleringData.Mottaker(
                        id = it.utbetalesTil.id,
                        navn = it.utbetalesTil.navn
                    ),
                    feilkonto = it.feilkonto,
                    detaljer = it.detaljer.map {
                        SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.DataForSimuleringData.Detaljer(
                            fom = it.fom,
                            tom = it.tom,
                            konto = it.konto,
                            beløp = it.beløp,
                            klassekode = SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.DataForSimuleringData.Klassekode(
                                kode = it.klassekode.kode,
                                beskrivelse = it.klassekode.beskrivelse
                            ),
                            uføregrad = it.uføregrad,
                            utbetalingstype = it.utbetalingstype,
                            tilbakeføring = it.tilbakeføring,
                            sats = SpannerPersonDto.ArbeidsgiverData.VedtaksperiodeData.DataForSimuleringData.Sats(
                                sats = it.sats.sats,
                                antall = it.sats.antall,
                                type = it.sats.type
                            ),
                            refunderesOrgnummer = it.refunderesOrgnummer
                        )
                    }
                )
            }
        )
    }
)
private fun FeriepengeUtDto.tilPersonData() = SpannerPersonDto.ArbeidsgiverData.FeriepengeutbetalingData(
    infotrygdFeriepengebeløpPerson = this.infotrygdFeriepengebeløpPerson,
    infotrygdFeriepengebeløpArbeidsgiver = this.infotrygdFeriepengebeløpArbeidsgiver,
    spleisFeriepengebeløpArbeidsgiver = this.spleisFeriepengebeløpArbeidsgiver,
    spleisFeriepengebeløpPerson = this.spleisFeriepengebeløpPerson,
    oppdrag = this.oppdrag.tilPersonData(),
    personoppdrag = this.personoppdrag.tilPersonData(),
    opptjeningsår = this.feriepengeberegner.opptjeningsår,
    utbetalteDager = this.feriepengeberegner.utbetalteDager.map { it.tilPersonData() },
    feriepengedager = this.feriepengeberegner.feriepengedager.map { it.tilPersonData() },
    utbetalingId = utbetalingId,
    sendTilOppdrag = sendTilOppdrag,
    sendPersonoppdragTilOS = sendPersonoppdragTilOS
)
private fun UtbetaltDagDto.tilPersonData() = SpannerPersonDto.ArbeidsgiverData.FeriepengeutbetalingData.UtbetaltDagData(
    type = when (this) {
        is UtbetaltDagDto.InfotrygdArbeidsgiver -> "InfotrygdArbeidsgiverDag"
        is UtbetaltDagDto.InfotrygdPerson -> "InfotrygdPersonDag"
        is UtbetaltDagDto.SpleisArbeidsgiver -> "SpleisArbeidsgiverDag"
        is UtbetaltDagDto.SpleisPerson -> "SpleisPersonDag"
    },
    orgnummer = orgnummer,
    dato = dato,
    beløp = beløp
)
private fun InfotrygdhistorikkelementDto.tilPersonData() = SpannerPersonDto.InfotrygdhistorikkElementData(
    id = this.id,
    tidsstempel = this.tidsstempel,
    hendelseId = this.hendelseId,
    ferieperioder = this.ferieperioder.map { it.tilPersonData() },
    arbeidsgiverutbetalingsperioder = this.arbeidsgiverutbetalingsperioder.map { it.tilPersonData() },
    personutbetalingsperioder = this.personutbetalingsperioder.map { it.tilPersonData() },
    inntekter = this.inntekter.map { it.tilPersonData() },
    arbeidskategorikoder = arbeidskategorikoder,
    oppdatert = oppdatert
)
private fun InfotrygdFerieperiodeDto.tilPersonData() = SpannerPersonDto.InfotrygdhistorikkElementData.FerieperiodeData(
    fom = this.periode.fom,
    tom = this.periode.tom
)
private fun InfotrygdArbeidsgiverutbetalingsperiodeDto.tilPersonData() = SpannerPersonDto.InfotrygdhistorikkElementData.ArbeidsgiverutbetalingsperiodeData(
    orgnr = this.orgnr,
    fom = this.periode.fom,
    tom = this.periode.tom,
    grad = this.grad.prosent,
    inntekt = this.inntekt.beløp
)
private fun InfotrygdPersonutbetalingsperiodeDto.tilPersonData() = SpannerPersonDto.InfotrygdhistorikkElementData.PersonutbetalingsperiodeData(
    orgnr = this.orgnr,
    fom = this.periode.fom,
    tom = this.periode.tom,
    grad = this.grad.prosent,
    inntekt = this.inntekt.beløp
)
private fun InfotrygdInntektsopplysningDto.tilPersonData() = SpannerPersonDto.InfotrygdhistorikkElementData.InntektsopplysningData(
    orgnr = this.orgnummer,
    sykepengerFom = this.sykepengerFom,
    inntekt = this.inntekt.beløp,
    refusjonTilArbeidsgiver = refusjonTilArbeidsgiver,
    refusjonTom = refusjonTom,
    lagret = lagret
)
private fun VilkårsgrunnlagInnslagUtDto.tilPersonData() = SpannerPersonDto.VilkårsgrunnlagInnslagData(
    id = this.id,
    opprettet = this.opprettet,
    vilkårsgrunnlag = this.vilkårsgrunnlag.map { it.tilPersonData() }
)
private fun VilkårsgrunnlagUtDto.tilPersonData() = SpannerPersonDto.VilkårsgrunnlagElementData(
    skjæringstidspunkt = this.skjæringstidspunkt,
    type = when (this) {
        is VilkårsgrunnlagUtDto.Infotrygd -> SpannerPersonDto.VilkårsgrunnlagElementData.GrunnlagsdataType.Infotrygd
        is VilkårsgrunnlagUtDto.Spleis -> SpannerPersonDto.VilkårsgrunnlagElementData.GrunnlagsdataType.Vilkårsprøving
    },
    sykepengegrunnlag = this.sykepengegrunnlag.tilPersonData(),
    opptjening = when (this) {
        is VilkårsgrunnlagUtDto.Spleis -> this.opptjening.tilPersonData()
        is VilkårsgrunnlagUtDto.Infotrygd -> null
    },
    medlemskapstatus = when (this) {
        is VilkårsgrunnlagUtDto.Spleis -> when (this.medlemskapstatus) {
            MedlemskapsvurderingDto.Ja -> SpannerPersonDto.VilkårsgrunnlagElementData.MedlemskapstatusDto.JA
            MedlemskapsvurderingDto.Nei -> SpannerPersonDto.VilkårsgrunnlagElementData.MedlemskapstatusDto.NEI
            MedlemskapsvurderingDto.UavklartMedBrukerspørsmål -> SpannerPersonDto.VilkårsgrunnlagElementData.MedlemskapstatusDto.UAVKLART_MED_BRUKERSPØRSMÅL
            MedlemskapsvurderingDto.VetIkke -> SpannerPersonDto.VilkårsgrunnlagElementData.MedlemskapstatusDto.VET_IKKE
        }
        else -> null
    },
    vurdertOk = when (this) {
        is VilkårsgrunnlagUtDto.Spleis -> this.vurdertOk
        else -> null
    },
    meldingsreferanseId = when (this) {
        is VilkårsgrunnlagUtDto.Spleis -> this.meldingsreferanseId
        else -> null
    },
    vilkårsgrunnlagId = this.vilkårsgrunnlagId
)

private fun OpptjeningDto.tilPersonData() = SpannerPersonDto.VilkårsgrunnlagElementData.OpptjeningData(
    opptjeningFom = this.opptjeningsperiode.fom,
    opptjeningTom = this.opptjeningsperiode.tom,
    arbeidsforhold = this.arbeidsforhold.map {
        SpannerPersonDto.VilkårsgrunnlagElementData.OpptjeningData.ArbeidsgiverOpptjeningsgrunnlagData(
            orgnummer = it.orgnummer,
            ansattPerioder = it.ansattPerioder.map {
                SpannerPersonDto.VilkårsgrunnlagElementData.OpptjeningData.ArbeidsgiverOpptjeningsgrunnlagData.ArbeidsforholdData(
                    ansattFom = it.ansattFom,
                    ansattTom = it.ansattTom,
                    deaktivert = it.deaktivert
                )
            }
        )
    }
)
private fun SykepengegrunnlagUtDto.tilPersonData() = SpannerPersonDto.VilkårsgrunnlagElementData.SykepengegrunnlagData(
    grunnbeløp = this.`6G`.beløp,
    arbeidsgiverInntektsopplysninger = this.arbeidsgiverInntektsopplysninger.map { it.tilPersonData() },
    sammenligningsgrunnlag = this.sammenligningsgrunnlag.tilPersonData(),
    deaktiverteArbeidsforhold = this.deaktiverteArbeidsforhold.map { it.tilPersonData() },
    vurdertInfotrygd = this.vurdertInfotrygd,
    totalOmregnetÅrsinntekt = totalOmregnetÅrsinntekt,
    beregningsgrunnlag = beregningsgrunnlag,
    er6GBegrenset = er6GBegrenset,
    forhøyetInntektskrav = forhøyetInntektskrav,
    minsteinntekt = minsteinntekt,
    oppfyllerMinsteinntektskrav = oppfyllerMinsteinntektskrav
)

private fun ArbeidsgiverInntektsopplysningDto.tilPersonData() = SpannerPersonDto.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningData(
    orgnummer = this.orgnummer,
    fom = this.gjelder.fom,
    tom = this.gjelder.tom,
    inntektsopplysning = this.inntektsopplysning.tilPersonData(),
    refusjonsopplysninger = this.refusjonsopplysninger.opplysninger.map {
        it.tilPersonData()
    }
)

private fun InntektsopplysningDto.tilPersonData() = SpannerPersonDto.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningData.InntektsopplysningData(
    id = this.id,
    dato = this.dato,
    hendelseId = this.hendelseId,
    beløp = when (this) {
        is InntektsopplysningDto.SkattSykepengegrunnlagDto -> null
        is InntektsopplysningDto.IkkeRapportertDto -> null
        is InntektsopplysningDto.InfotrygdDto -> this.beløp.beløp
        is InntektsopplysningDto.InntektsmeldingDto -> this.beløp.beløp
        is InntektsopplysningDto.SaksbehandlerDto -> this.beløp.beløp
        is InntektsopplysningDto.SkjønnsmessigFastsattDto -> this.beløp.beløp
    },
    kilde = when (this) {
        is InntektsopplysningDto.IkkeRapportertDto -> "IKKE_RAPPORTERT"
        is InntektsopplysningDto.InfotrygdDto -> "INFOTRYGD"
        is InntektsopplysningDto.InntektsmeldingDto -> "INNTEKTSMELDING"
        is InntektsopplysningDto.SaksbehandlerDto -> "SAKSBEHANDLER"
        is InntektsopplysningDto.SkattSykepengegrunnlagDto -> "SKATT_SYKEPENGEGRUNNLAG"
        is InntektsopplysningDto.SkjønnsmessigFastsattDto -> "SKJØNNSMESSIG_FASTSATT"
    },
    forklaring = when (this) {
        is InntektsopplysningDto.SaksbehandlerDto -> this.forklaring
        else -> null
    },
    subsumsjon = when (this) {
        is InntektsopplysningDto.SaksbehandlerDto -> this.subsumsjon?.let {
            SpannerPersonDto.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningData.InntektsopplysningData.SubsumsjonData(
                paragraf = it.paragraf,
                bokstav = it.bokstav,
                ledd = it.ledd
            )
        }
        else -> null
    },
    tidsstempel = this.tidsstempel,
    overstyrtInntektId = when (this) {
        is InntektsopplysningDto.SaksbehandlerDto -> this.overstyrtInntekt
        is InntektsopplysningDto.SkjønnsmessigFastsattDto -> this.overstyrtInntekt
        else -> null
    },
    skatteopplysninger = when (this) {
        is InntektsopplysningDto.SkattSykepengegrunnlagDto -> this.inntektsopplysninger.map { it.tilPersonDataSkattopplysning() }
        else -> null
    }
)

private fun RefusjonsopplysningDto.tilPersonData() = SpannerPersonDto.ArbeidsgiverData.RefusjonsopplysningData(
    meldingsreferanseId = this.meldingsreferanseId,
    fom = this.fom,
    tom = this.tom,
    beløp = this.beløp.beløp
)

private fun SammenligningsgrunnlagDto.tilPersonData() = SpannerPersonDto.VilkårsgrunnlagElementData.SammenligningsgrunnlagData(
    sammenligningsgrunnlag = this.sammenligningsgrunnlag.beløp,
    arbeidsgiverInntektsopplysninger = this.arbeidsgiverInntektsopplysninger.map { it.tilPersonData() }
)

private fun ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagDto.tilPersonData() = SpannerPersonDto.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagData(
    orgnummer = this.orgnummer,
    skatteopplysninger = this.inntektsopplysninger.map { it.tilPersonData() }
)

private fun SkatteopplysningDto.tilPersonData() =
    SpannerPersonDto.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagData.SammenligningsgrunnlagInntektsopplysningData(
        hendelseId = this.hendelseId,
        beløp = this.beløp.beløp,
        måned = this.måned,
        type = when (this.type) {
            InntekttypeDto.LØNNSINNTEKT -> SpannerPersonDto.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagData.SammenligningsgrunnlagInntektsopplysningData.InntekttypeData.LØNNSINNTEKT
            InntekttypeDto.NÆRINGSINNTEKT -> SpannerPersonDto.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagData.SammenligningsgrunnlagInntektsopplysningData.InntekttypeData.NÆRINGSINNTEKT
            InntekttypeDto.PENSJON_ELLER_TRYGD -> SpannerPersonDto.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagData.SammenligningsgrunnlagInntektsopplysningData.InntekttypeData.PENSJON_ELLER_TRYGD
            InntekttypeDto.YTELSE_FRA_OFFENTLIGE -> SpannerPersonDto.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagData.SammenligningsgrunnlagInntektsopplysningData.InntekttypeData.YTELSE_FRA_OFFENTLIGE
        },
        fordel = fordel,
        beskrivelse = beskrivelse,
        tidsstempel = tidsstempel
    )
private fun SkatteopplysningDto.tilPersonDataSkattopplysning() =
    SpannerPersonDto.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningData.SkatteopplysningData(
        hendelseId = this.hendelseId,
        beløp = this.beløp.beløp,
        måned = this.måned,
        type = when (this.type) {
            InntekttypeDto.LØNNSINNTEKT -> SpannerPersonDto.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningData.SkatteopplysningData.InntekttypeData.LØNNSINNTEKT
            InntekttypeDto.NÆRINGSINNTEKT -> SpannerPersonDto.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningData.SkatteopplysningData.InntekttypeData.NÆRINGSINNTEKT
            InntekttypeDto.PENSJON_ELLER_TRYGD -> SpannerPersonDto.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningData.SkatteopplysningData.InntekttypeData.PENSJON_ELLER_TRYGD
            InntekttypeDto.YTELSE_FRA_OFFENTLIGE -> SpannerPersonDto.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningData.SkatteopplysningData.InntekttypeData.YTELSE_FRA_OFFENTLIGE
        },
        fordel = fordel,
        beskrivelse = beskrivelse,
        tidsstempel = tidsstempel
    )