package no.nav.helse.utbetalingstidslinje

import no.nav.helse.person.UtbetalingsdagVisitor
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.NavDag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.NavHelgDag
import no.nav.helse.økonomi.Økonomi
import java.time.LocalDate

@Deprecated("Is now the responsibility of the Økonomi object")
internal class Sykdomsgrader(tidslinjer: List<Utbetalingstidslinje>): UtbetalingsdagVisitor {

    private val grader = mutableMapOf<LocalDate, Double>()

    internal operator fun get(dato: LocalDate) = grader[dato] ?: Double.NaN

    init {
        require(tidslinjer.size == 1) { "Flere arbeidsgivere støttes ikke (epic 7)" }
        tidslinjer.first().accept(this)
    }
    override fun visit(
        dag: NavDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        grader[dato] = økonomi.toMap()["grad"] as Double
    }
    override fun visit(
        dag: NavHelgDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        grader[dato] = økonomi.toMap()["grad"] as Double
    }
}
