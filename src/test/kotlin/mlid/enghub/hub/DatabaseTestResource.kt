package mlid.enghub.hub

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.oracle.OracleContainer

class DatabaseTestResource : QuarkusTestResourceLifecycleManager {
    private var mariadb: MariaDBContainer<*>? = null
    private var oracle: OracleContainer? = null

    override fun start(): Map<String, String> = if (System.getProperty("db") == "oracle") startOracle() else startMariaDb()

    private fun startMariaDb(): Map<String, String> {
        val container =
            MariaDBContainer("mariadb:10.11")
                .withDatabaseName("filehub")
                .withUsername("hub")
                .withPassword("hub")
        container.start()
        mariadb = container
        return mapOf(
            "quarkus.datasource.jdbc.url" to container.jdbcUrl,
            "quarkus.datasource.username" to container.username,
            "quarkus.datasource.password" to container.password,
            "quarkus.datasource.reactive.url" to
                "mysql://${container.host}:${container.getMappedPort(3306)}/filehub",
        )
    }

    private fun startOracle(): Map<String, String> {
        val container =
            OracleContainer("gvenzl/oracle-free:23-slim-faststart")
                .withDatabaseName("filehub")
                .withUsername("hub")
                .withPassword("hub")
        container.start()
        oracle = container
        return mapOf(
            "quarkus.datasource.jdbc.url" to container.jdbcUrl,
            "quarkus.datasource.username" to container.username,
            "quarkus.datasource.password" to container.password,
            "quarkus.datasource.reactive.url" to container.jdbcUrl.removePrefix("jdbc:"),
        )
    }

    override fun stop() {
        mariadb?.stop()
        oracle?.stop()
    }
}
