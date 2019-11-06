package no.nav.helse.person.domain

interface PersonObserver : SakskompleksObserver {
    data class PersonEndretEvent(val aktørId: String,
                                 val sykdomshendelse: ArbeidstakerHendelse,
                                 val memento: Person.Memento)

    fun personEndret(personEndretEvent: PersonEndretEvent)
}
