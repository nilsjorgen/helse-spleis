package no.nav.helse.sakskompleks.db

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.createHikariConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection

class PostgresTest {

    private lateinit var embeddedPostgres: EmbeddedPostgres
    private lateinit var postgresConnection: Connection

    private lateinit var hikariConfig: HikariConfig

    @BeforeEach
    fun `start postgres`() {
        embeddedPostgres = EmbeddedPostgres.builder()
                .start()

        postgresConnection = embeddedPostgres.postgresDatabase.connection

        hikariConfig = createHikariConfig(embeddedPostgres.getJdbcUrl("postgres", "postgres"))
    }

    @AfterEach
    fun `stop postgres`() {
        postgresConnection.close()
        embeddedPostgres.close()
    }

    @Test
    fun `migreringer skal kjøre på en tom database`() {
        val migrations = runMigration(HikariDataSource(hikariConfig))
        assertEquals(1, migrations)
    }

    @Test
    fun `migreringer skal ikke kjøres flere ganger`() {
        runMigration(HikariDataSource(hikariConfig))

        val migrations = runMigration(HikariDataSource(hikariConfig))
        assertEquals(0, migrations)
    }
}
