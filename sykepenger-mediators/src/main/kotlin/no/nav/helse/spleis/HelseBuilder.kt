package no.nav.helse.spleis

import no.nav.helse.person.PersonObserver
import no.nav.helse.spleis.db.*
import no.nav.helse.spleis.rest.PersonRestInterface
import org.apache.kafka.clients.producer.KafkaProducer
import javax.sql.DataSource

// Understands how to create the mediators and create the objects they need
internal class HelseBuilder(
    dataSource: DataSource,
    kafkaRapid: KafkaRapid,
    hendelseProducer: KafkaProducer<String, String>
) {

    private val hendelseDirector: HendelseMediator
    private val hendelseProbe: HendelseProbe = HendelseProbe()
    internal val hendelseRecorder: HendelseRecorder = HendelseRecorder(dataSource)

    internal val personRestInterface: PersonRestInterface
    private val personRepository: PersonRepository = PersonPostgresRepository(dataSource)
    private val lagrePersonDao: PersonObserver = LagrePersonDao(dataSource)
    private val utbetalingsreferanseRepository: UtbetalingsreferanseRepository =
        UtbetalingsreferansePostgresRepository(dataSource)
    private val lagreUtbetalingDao: PersonObserver = LagreUtbetalingDao(dataSource)

    init {
        personRestInterface = PersonRestInterface(
            personRepository = personRepository,
            utbetalingsreferanseRepository = utbetalingsreferanseRepository
        )

        hendelseDirector = HendelseMediator(
            rapid = kafkaRapid,
            personRepository = personRepository,
            lagrePersonDao = lagrePersonDao,
            lagreUtbetalingDao = lagreUtbetalingDao,
            producer = hendelseProducer,
            hendelseProbe = hendelseProbe,
            hendelseRecorder = hendelseRecorder
        )
    }
}
