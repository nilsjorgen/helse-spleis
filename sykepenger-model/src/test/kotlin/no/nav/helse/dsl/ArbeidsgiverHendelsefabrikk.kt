package no.nav.helse.dsl

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.Temporal
import java.util.UUID
import no.nav.helse.Personidentifikator
import no.nav.helse.hendelser.AnmodningOmForkasting
import no.nav.helse.hendelser.Arbeidsavklaringspenger
import no.nav.helse.hendelser.Dagpenger
import no.nav.helse.hendelser.Foreldrepenger
import no.nav.helse.hendelser.ForkastSykmeldingsperioder
import no.nav.helse.hendelser.GjenopplivVilkårsgrunnlag
import no.nav.helse.hendelser.Grunnbeløpsregulering
import no.nav.helse.hendelser.IdentOpphørt
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Institusjonsopphold
import no.nav.helse.hendelser.KanIkkeBehandlesHer
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.Omsorgspenger
import no.nav.helse.hendelser.Opplæringspenger
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Pleiepenger
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Simulering
import no.nav.helse.dto.SimuleringResultatDto
import no.nav.helse.hendelser.GradertPeriode
import no.nav.helse.hendelser.AvbruttSøknad
import no.nav.helse.hendelser.InntektsmeldingerReplay
import no.nav.helse.hendelser.Svangerskapspenger
import no.nav.helse.hendelser.Sykmelding
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Utbetalingshistorikk
import no.nav.helse.hendelser.UtbetalingshistorikkEtterInfotrygdendring
import no.nav.helse.hendelser.VedtakFattet
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.hendelser.utbetaling.AnnullerUtbetaling
import no.nav.helse.hendelser.utbetaling.UtbetalingHendelse
import no.nav.helse.hendelser.utbetaling.Utbetalingsgodkjenning
import no.nav.helse.person.AbstractPersonTest.Companion.AKTØRID
import no.nav.helse.person.TilstandType
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.infotrygdhistorikk.InfotrygdhistorikkElement
import no.nav.helse.person.infotrygdhistorikk.Infotrygdperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.spill_av_im.Forespørsel
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.økonomi.Inntekt
import no.nav.inntektsmeldingkontrakt.Arbeidsgivertype
import no.nav.inntektsmeldingkontrakt.AvsenderSystem
import no.nav.inntektsmeldingkontrakt.Refusjon
import no.nav.inntektsmeldingkontrakt.Status


internal class ArbeidsgiverHendelsefabrikk(
    private val aktørId: String,
    private val personidentifikator: Personidentifikator,
    private val organisasjonsnummer: String
) {

    private val sykmeldinger = mutableListOf<Sykmelding>()
    private val søknader = mutableListOf<Søknad>()
    private val inntektsmeldinger = mutableMapOf<UUID, AbstractEndToEndTest.InnsendtInntektsmelding>()

    internal fun lagSykmelding(
        vararg sykeperioder: Sykmeldingsperiode,
        id: UUID = UUID.randomUUID()
    ): Sykmelding {
        return Sykmelding(
            meldingsreferanseId = id,
            fnr = personidentifikator.toString(),
            aktørId = aktørId,
            orgnummer = organisasjonsnummer,
            sykeperioder = listOf(*sykeperioder)
        ).apply {
            sykmeldinger.add(this)
        }
    }

    internal fun lagSøknad(
        vararg perioder: Søknad.Søknadsperiode,
        andreInntektskilder: Boolean = false,
        sendtTilNAVEllerArbeidsgiver: Temporal? = null,
        sykmeldingSkrevet: LocalDateTime? = null,
        ikkeJobbetIDetSisteFraAnnetArbeidsforhold: Boolean = false,
        id: UUID = UUID.randomUUID(),
        merknaderFraSykmelding: List<Søknad.Merknad> = emptyList(),
        permittert: Boolean = false,
        korrigerer: UUID? = null,
        utenlandskSykmelding: Boolean = false,
        arbeidUtenforNorge: Boolean = false,
        sendTilGosys: Boolean = false,
        opprinneligSendt: LocalDate? = null,
        yrkesskade: Boolean = false,
        aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
        egenmeldinger: List<Søknad.Søknadsperiode.Arbeidsgiverdag> = emptyList(),
        søknadstype: Søknad.Søknadstype = Søknad.Søknadstype.Arbeidstaker,
        registrert: LocalDateTime = LocalDateTime.now()
    ): Søknad {
        val innsendt = (sendtTilNAVEllerArbeidsgiver ?: Søknad.Søknadsperiode.søknadsperiode(perioder.toList())!!.endInclusive).let {
            when (it) {
                is LocalDateTime -> it
                is LocalDate -> it.atStartOfDay()
                else -> throw IllegalStateException("Innsendt må være enten LocalDate eller LocalDateTime")
            }
        }
        return Søknad(
            meldingsreferanseId = id,
            fnr = personidentifikator.toString(),
            aktørId = aktørId,
            orgnummer = organisasjonsnummer,
            perioder = listOf(*perioder),
            andreInntektskilder = andreInntektskilder,
            ikkeJobbetIDetSisteFraAnnetArbeidsforhold = ikkeJobbetIDetSisteFraAnnetArbeidsforhold,
            sendtTilNAVEllerArbeidsgiver = innsendt,
            permittert = permittert,
            merknaderFraSykmelding = merknaderFraSykmelding,
            sykmeldingSkrevet = sykmeldingSkrevet ?: Søknad.Søknadsperiode.søknadsperiode(perioder.toList())!!.start.atStartOfDay(),
            opprinneligSendt = opprinneligSendt?.atStartOfDay(),
            utenlandskSykmelding = utenlandskSykmelding,
            arbeidUtenforNorge = arbeidUtenforNorge,
            sendTilGosys = sendTilGosys,
            aktivitetslogg = aktivitetslogg,
            yrkesskade = yrkesskade,
            egenmeldinger = egenmeldinger,
            søknadstype = søknadstype,
            registrert = registrert
        ).apply {
            søknader.add(this)
        }
    }

    fun lagAvbruttSøknad(sykmeldingsperiode: Periode): AvbruttSøknad =
        AvbruttSøknad(sykmeldingsperiode, UUID.randomUUID(), organisasjonsnummer, personidentifikator.toString(), aktørId)

    internal fun lagInntektsmelding(
        arbeidsgiverperioder: List<Periode>,
        beregnetInntekt: Inntekt,
        førsteFraværsdag: LocalDate? = arbeidsgiverperioder.maxOf { it.start },
        refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
        harOpphørAvNaturalytelser: Boolean = false,
        arbeidsforholdId: String? = null,
        begrunnelseForReduksjonEllerIkkeUtbetalt: String? = null,
        id: UUID = UUID.randomUUID(),
        aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
        harFlereInntektsmeldinger: Boolean = false,
        mottatt: LocalDateTime = LocalDateTime.now()
    ): Inntektsmelding {
        val inntektsmeldinggenerator = { aktivitetslogg: Aktivitetslogg ->
            Inntektsmelding(
                meldingsreferanseId = id,
                refusjon = refusjon,
                orgnummer = organisasjonsnummer,
                fødselsnummer = personidentifikator.toString(),
                aktørId = aktørId,
                førsteFraværsdag = førsteFraværsdag,
                inntektsdato = null,
                beregnetInntekt = beregnetInntekt,
                arbeidsgiverperioder = arbeidsgiverperioder,
                arbeidsforholdId = arbeidsforholdId,
                begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
                harOpphørAvNaturalytelser = harOpphørAvNaturalytelser,
                harFlereInntektsmeldinger = harFlereInntektsmeldinger,
                avsendersystem = Inntektsmelding.Avsendersystem.LPS,
                mottatt = mottatt,
                aktivitetslogg = aktivitetslogg
            )
        }
        val kontrakten = no.nav.inntektsmeldingkontrakt.Inntektsmelding(
            inntektsmeldingId = UUID.randomUUID().toString(),
            arbeidstakerFnr = personidentifikator.toString(),
            arbeidstakerAktorId = aktørId,
            virksomhetsnummer = organisasjonsnummer,
            arbeidsgiverFnr = null,
            arbeidsgiverAktorId = null,
            arbeidsgivertype = Arbeidsgivertype.VIRKSOMHET,
            arbeidsforholdId = null,
            beregnetInntekt = BigDecimal.valueOf(beregnetInntekt.reflection { _, månedlig, _, _ -> månedlig }),
            refusjon = Refusjon(BigDecimal.valueOf(beregnetInntekt.reflection { _, månedlig, _, _ -> månedlig }), null),
            endringIRefusjoner = emptyList(),
            opphoerAvNaturalytelser = emptyList(),
            gjenopptakelseNaturalytelser = emptyList(),
            arbeidsgiverperioder = arbeidsgiverperioder.map {
                no.nav.inntektsmeldingkontrakt.Periode(it.start, it.endInclusive)
            },
            status = Status.GYLDIG,
            arkivreferanse = "",
            ferieperioder = emptyList(),
            foersteFravaersdag = førsteFraværsdag,
            mottattDato = LocalDateTime.now(),
            begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
            naerRelasjon = null,
            avsenderSystem = AvsenderSystem("SpleisModell"),
            innsenderTelefon = "tlfnr",
            innsenderFulltNavn = "SPLEIS Modell"
        )
        inntektsmeldinger[id] = AbstractEndToEndTest.InnsendtInntektsmelding(
            tidspunkt = LocalDateTime.now(),
            generator = inntektsmeldinggenerator,
            inntektsmeldingkontrakt = kontrakten
        )
        return inntektsmeldinggenerator(aktivitetslogg)
    }

    internal fun lagPortalinntektsmelding(
        arbeidsgiverperioder: List<Periode>,
        beregnetInntekt: Inntekt,
        førsteFraværsdag: LocalDate? = arbeidsgiverperioder.maxOf { it.start },
        inntektsdato: LocalDate,
        refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
        harOpphørAvNaturalytelser: Boolean = false,
        arbeidsforholdId: String? = null,
        begrunnelseForReduksjonEllerIkkeUtbetalt: String? = null,
        id: UUID = UUID.randomUUID(),
        aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
        harFlereInntektsmeldinger: Boolean = false,
        mottatt: LocalDateTime = LocalDateTime.now()
    ): Inntektsmelding {
        val inntektsmeldinggenerator = { aktivitetslogg: Aktivitetslogg ->
            Inntektsmelding(
                meldingsreferanseId = id,
                refusjon = refusjon,
                orgnummer = organisasjonsnummer,
                fødselsnummer = personidentifikator.toString(),
                aktørId = aktørId,
                førsteFraværsdag = førsteFraværsdag,
                inntektsdato = inntektsdato,
                beregnetInntekt = beregnetInntekt,
                arbeidsgiverperioder = arbeidsgiverperioder,
                arbeidsforholdId = arbeidsforholdId,
                begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
                harOpphørAvNaturalytelser = harOpphørAvNaturalytelser,
                harFlereInntektsmeldinger = harFlereInntektsmeldinger,
                avsendersystem = Inntektsmelding.Avsendersystem.NAV_NO,
                mottatt = mottatt,
                aktivitetslogg = aktivitetslogg
            )
        }
        val kontrakten = no.nav.inntektsmeldingkontrakt.Inntektsmelding(
            inntektsmeldingId = UUID.randomUUID().toString(),
            arbeidstakerFnr = personidentifikator.toString(),
            arbeidstakerAktorId = aktørId,
            virksomhetsnummer = organisasjonsnummer,
            arbeidsgiverFnr = null,
            arbeidsgiverAktorId = null,
            arbeidsgivertype = Arbeidsgivertype.VIRKSOMHET,
            arbeidsforholdId = null,
            beregnetInntekt = BigDecimal.valueOf(beregnetInntekt.reflection { _, månedlig, _, _ -> månedlig }),
            refusjon = Refusjon(BigDecimal.valueOf(beregnetInntekt.reflection { _, månedlig, _, _ -> månedlig }), null),
            endringIRefusjoner = emptyList(),
            opphoerAvNaturalytelser = emptyList(),
            gjenopptakelseNaturalytelser = emptyList(),
            arbeidsgiverperioder = arbeidsgiverperioder.map {
                no.nav.inntektsmeldingkontrakt.Periode(it.start, it.endInclusive)
            },
            status = Status.GYLDIG,
            arkivreferanse = "",
            ferieperioder = emptyList(),
            foersteFravaersdag = førsteFraværsdag,
            mottattDato = LocalDateTime.now(),
            begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
            naerRelasjon = null,
            avsenderSystem = AvsenderSystem("NAV_NO"),
            innsenderTelefon = "tlfnr",
            innsenderFulltNavn = "SPLEIS Modell"
        )
        inntektsmeldinger[id] = AbstractEndToEndTest.InnsendtInntektsmelding(
            tidspunkt = LocalDateTime.now(),
            generator = inntektsmeldinggenerator,
            inntektsmeldingkontrakt = kontrakten
        )
        return inntektsmeldinggenerator(aktivitetslogg)
    }

    internal fun lagInntektsmeldingReplay(forespørsel: Forespørsel, håndterteInntektsmeldinger: Set<UUID>, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) =
        InntektsmeldingerReplay(
            meldingsreferanseId = UUID.randomUUID(),
            aktørId = aktørId,
            fødselsnummer = personidentifikator.toString(),
            organisasjonsnummer = organisasjonsnummer,
            aktivitetslogg = aktivitetslogg,
            vedtaksperiodeId = forespørsel.vedtaksperiodeId,
            inntektsmeldinger = inntektsmeldinger
                .filter { forespørsel.erInntektsmeldingRelevant(it.value.inntektsmeldingkontrakt) }
                .map { (_, im) -> im.generator(aktivitetslogg.barn()) }
                .filterNot { it.meldingsreferanseId() in håndterteInntektsmeldinger }
        )

    internal fun lagUtbetalingshistorikk(
        vedtaksperiodeId: UUID,
        utbetalinger: List<Infotrygdperiode> = listOf(),
        inntektshistorikk: List<Inntektsopplysning> = emptyList(),
        harStatslønn: Boolean = false,
        besvart: LocalDateTime = LocalDateTime.now()
    ) =
        Utbetalingshistorikk(
            meldingsreferanseId = UUID.randomUUID(),
            aktørId = aktørId,
            fødselsnummer = personidentifikator.toString(),
            organisasjonsnummer = organisasjonsnummer,
            vedtaksperiodeId = vedtaksperiodeId.toString(),
            element = InfotrygdhistorikkElement.opprett(
                oppdatert = besvart,
                hendelseId = UUID.randomUUID(),
                perioder = utbetalinger,
                inntekter = inntektshistorikk,
                arbeidskategorikoder = emptyMap()
            ),
            besvart = LocalDateTime.now()
        )

    internal fun lagUtbetalingshistorikkEtterInfotrygdendring(
        utbetalinger: List<Infotrygdperiode> = listOf(),
        inntektshistorikk: List<Inntektsopplysning> = emptyList(),
        besvart: LocalDateTime = LocalDateTime.now(),
        id: UUID = UUID.randomUUID()
    ) =
        UtbetalingshistorikkEtterInfotrygdendring(
            meldingsreferanseId = id,
            aktørId = aktørId,
            fødselsnummer = personidentifikator.toString(),
            element = InfotrygdhistorikkElement.opprett(
                oppdatert = besvart,
                hendelseId = UUID.randomUUID(),
                perioder = utbetalinger,
                inntekter = inntektshistorikk,
                arbeidskategorikoder = emptyMap()
            ),
            besvart = LocalDateTime.now()
        )


    internal fun lagVilkårsgrunnlag(
        vedtaksperiodeId: UUID,
        skjæringstidspunkt: LocalDate,
        medlemskapstatus: Medlemskapsvurdering.Medlemskapstatus,
        arbeidsforhold: List<Vilkårsgrunnlag.Arbeidsforhold>,
        inntektsvurderingForSykepengegrunnlag: InntektForSykepengegrunnlag
    ): Vilkårsgrunnlag {
        return Vilkårsgrunnlag(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = vedtaksperiodeId.toString(),
            skjæringstidspunkt = skjæringstidspunkt,
            aktørId = aktørId,
            personidentifikator = personidentifikator,
            orgnummer = organisasjonsnummer,
            medlemskapsvurdering = Medlemskapsvurdering(medlemskapstatus),
            inntektsvurderingForSykepengegrunnlag = inntektsvurderingForSykepengegrunnlag,
            arbeidsforhold = arbeidsforhold
        )
    }

    internal fun lagYtelser(
        vedtaksperiodeId: UUID,
        foreldrepenger: List<GradertPeriode> = emptyList(),
        svangerskapspenger: List<GradertPeriode> = emptyList(),
        pleiepenger: List<GradertPeriode> = emptyList(),
        omsorgspenger: List<GradertPeriode> = emptyList(),
        opplæringspenger: List<GradertPeriode> = emptyList(),
        institusjonsoppholdsperioder: List<Institusjonsopphold.Institusjonsoppholdsperiode> = emptyList(),
        arbeidsavklaringspenger: List<Periode> = emptyList(),
        dagpenger: List<Periode> = emptyList()
    ): Ytelser {
        val meldingsreferanseId = UUID.randomUUID()
        return Ytelser(
            meldingsreferanseId = meldingsreferanseId,
            aktørId = aktørId,
            fødselsnummer = personidentifikator.toString(),
            organisasjonsnummer = organisasjonsnummer,
            vedtaksperiodeId = vedtaksperiodeId.toString(),
            foreldrepenger = Foreldrepenger(
                foreldrepengeytelse = foreldrepenger,
            ),
            svangerskapspenger = Svangerskapspenger(
                svangerskapsytelse = svangerskapspenger
            ),
            pleiepenger = Pleiepenger(
                perioder = pleiepenger
            ),
            omsorgspenger = Omsorgspenger(
                perioder = omsorgspenger
            ),
            opplæringspenger = Opplæringspenger(
                perioder = opplæringspenger
            ),
            institusjonsopphold = Institusjonsopphold(
                perioder = institusjonsoppholdsperioder
            ),
            arbeidsavklaringspenger = Arbeidsavklaringspenger(arbeidsavklaringspenger),
            dagpenger = Dagpenger(dagpenger),
            aktivitetslogg = Aktivitetslogg()
        )
    }

    internal fun lagSimulering(
        vedtaksperiodeId: UUID,
        utbetalingId: UUID,
        fagsystemId: String,
        fagområde: String,
        simuleringOK: Boolean,
        simuleringsresultat: SimuleringResultatDto?
    ): Simulering {
        return Simulering(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = vedtaksperiodeId.toString(),
            aktørId = aktørId,
            fødselsnummer = personidentifikator.toString(),
            orgnummer = organisasjonsnummer,
            fagsystemId = fagsystemId,
            fagområde = fagområde,
            simuleringOK = simuleringOK,
            melding = "",
            utbetalingId = utbetalingId,
            simuleringResultat = simuleringsresultat
        )
    }

    internal fun lagUtbetalingsgodkjenning(
        vedtaksperiodeId: UUID,
        utbetalingGodkjent: Boolean,
        automatiskBehandling: Boolean,
        utbetalingId: UUID,
        godkjenttidspunkt: LocalDateTime = LocalDateTime.now()
    ) = Utbetalingsgodkjenning(
        meldingsreferanseId = UUID.randomUUID(),
        aktørId = aktørId,
        fødselsnummer = personidentifikator.toString(),
        organisasjonsnummer = organisasjonsnummer,
        utbetalingId = utbetalingId,
        vedtaksperiodeId = vedtaksperiodeId.toString(),
        saksbehandler = "Ola Nordmann",
        saksbehandlerEpost = "ola.nordmann@nav.no",
        utbetalingGodkjent = utbetalingGodkjent,
        godkjenttidspunkt = godkjenttidspunkt,
        automatiskBehandling = automatiskBehandling,
    )

    internal fun lagVedtakFattet(
        vedtaksperiodeId: UUID,
        utbetalingId: UUID,
        automatisert: Boolean = true,
        vedtakFattetTidspunkt: LocalDateTime = LocalDateTime.now()
    ) = VedtakFattet(
        meldingsreferanseId = UUID.randomUUID(),
        aktørId = aktørId,
        fødselsnummer = personidentifikator.toString(),
        organisasjonsnummer = organisasjonsnummer,
        utbetalingId = utbetalingId,
        vedtaksperiodeId = vedtaksperiodeId,
        saksbehandlerIdent = "Vedtak fattesen",
        saksbehandlerEpost = "vedtak.fattesen@nav.no",
        vedtakFattetTidspunkt = vedtakFattetTidspunkt,
        automatisert = automatisert
    )
    internal fun lagKanIkkeBehandlesHer(
        vedtaksperiodeId: UUID,
        utbetalingId: UUID,
        automatisert: Boolean = true
    ) = KanIkkeBehandlesHer(
        meldingsreferanseId = UUID.randomUUID(),
        aktørId = aktørId,
        fødselsnummer = personidentifikator.toString(),
        organisasjonsnummer = organisasjonsnummer,
        utbetalingId = utbetalingId,
        vedtaksperiodeId = vedtaksperiodeId,
        saksbehandlerIdent = "Info trygdesen",
        saksbehandlerEpost = "info.trygdesen@nav.no",
        opprettet = LocalDateTime.now(),
        automatisert = automatisert
    )

    internal fun lagUtbetalinghendelse(
        utbetalingId: UUID,
        fagsystemId: String,
        status: Oppdragstatus,
        meldingsreferanseId: UUID = UUID.randomUUID()
    ) =
        UtbetalingHendelse(
            meldingsreferanseId = meldingsreferanseId,
            aktørId = aktørId,
            fødselsnummer = personidentifikator.toString(),
            orgnummer = organisasjonsnummer,
            fagsystemId = fagsystemId,
            utbetalingId = utbetalingId.toString(),
            status = status,
            melding = "hei",
            avstemmingsnøkkel = 123456L,
            overføringstidspunkt = LocalDateTime.now()
        )

    internal fun lagAnnullering(utbetalingId: UUID) =
        AnnullerUtbetaling(
            meldingsreferanseId = UUID.randomUUID(),
            aktørId = aktørId,
            fødselsnummer = personidentifikator.toString(),
            organisasjonsnummer = organisasjonsnummer,
            fagsystemId = null,
            utbetalingId = utbetalingId,
            saksbehandlerIdent = "Ola Nordmann",
            saksbehandlerEpost = "tbd@nav.no",
            opprettet = LocalDateTime.now()
        )

    internal fun lagIdentOpphørt() =
        IdentOpphørt(
            meldingsreferanseId = UUID.randomUUID(),
            aktørId = aktørId,
            fødselsnummer = personidentifikator.toString()
        )

    internal fun lagPåminnelse(vedtaksperiodeId: UUID, tilstand: TilstandType, tilstandsendringstidspunkt: LocalDateTime, reberegning: Boolean = false) =
        Påminnelse(
            UUID.randomUUID(),
            aktørId,
            personidentifikator.toString(),
            organisasjonsnummer,
            vedtaksperiodeId.toString(),
            0,
            tilstand,
            tilstandsendringstidspunkt,
            LocalDateTime.now(),
            LocalDateTime.now(),
            opprettet = LocalDateTime.now(),
            ønskerReberegning = reberegning
        )

    internal fun lagGrunnbeløpsregulering(skjæringstidspunkt: LocalDate) =
        Grunnbeløpsregulering(
            meldingsreferanseId = UUID.randomUUID(),
            aktørId,
            personidentifikator.toString(),
            skjæringstidspunkt = skjæringstidspunkt,
            opprettet = LocalDateTime.now()
        )

    internal fun lagGjenopplivVilkårsgrunnlag(skjæringstidspunkt: LocalDate?, vilkårsgrunnlagId: UUID) =
        GjenopplivVilkårsgrunnlag(
            meldingsreferanseId = UUID.randomUUID(),
            aktørId = aktørId,
            fødselsnummer = personidentifikator.toString(),
            vilkårsgrunnlagId = vilkårsgrunnlagId,
            nyttSkjæringstidspunkt = skjæringstidspunkt,
            arbeidsgiveropplysninger = emptyMap()
        )
    internal fun lagHåndterForkastSykmeldingsperioder(periode: Periode) =
        ForkastSykmeldingsperioder(
            meldingsreferanseId = UUID.randomUUID(),
            aktørId = aktørId,
            fødselsnummer = personidentifikator.toString(),
            organisasjonsnummer = organisasjonsnummer,
            periode = periode
        )

    internal fun lagAnmodningOmForkasting(vedtaksperiodeId: UUID, force: Boolean = false) =
        AnmodningOmForkasting(
            meldingsreferanseId = UUID.randomUUID(),
            aktørId = aktørId,
            fødselsnummer = personidentifikator.toString(),
            organisasjonsnummer = organisasjonsnummer,
            vedtaksperiodeId = vedtaksperiodeId,
            force = force
        )

    internal fun lagHåndterOverstyrTidslinje(overstyringsdager: List<ManuellOverskrivingDag>) =
        OverstyrTidslinje(
            meldingsreferanseId = UUID.randomUUID(),
            fødselsnummer = personidentifikator.toString(),
            aktørId = aktørId,
            organisasjonsnummer = organisasjonsnummer,
            dager = overstyringsdager,
            opprettet = LocalDateTime.now()
        )

    internal fun lagOverstyrInntekt(hendelseId: UUID, skjæringstidspunkt: LocalDate, inntekt: Inntekt, orgnummer: String) =
        PersonHendelsefabrikk(aktørId, personidentifikator).lagOverstyrArbeidsgiveropplysninger(skjæringstidspunkt, listOf(
            OverstyrtArbeidsgiveropplysning(orgnummer, inntekt, "forklaring", null, emptyList())
        ), meldingsreferanseId = hendelseId)


}