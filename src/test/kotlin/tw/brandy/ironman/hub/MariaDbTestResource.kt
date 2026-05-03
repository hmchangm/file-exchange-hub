package tw.brandy.ironman.hub

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.MariaDBContainer

class MariaDbTestResource : QuarkusTestResourceLifecycleManager {

    private val container = MariaDBContainer("mariadb:11")
        .withDatabaseName("filehub")
        .withUsername("hub")
        .withPassword("hub")

    override fun start(): Map<String, String> {
        container.start()
        return mapOf(
            "quarkus.datasource.jdbc.url" to container.jdbcUrl,
            "quarkus.datasource.username" to container.username,
            "quarkus.datasource.password" to container.password,
            "quarkus.datasource.reactive.url" to
                "mysql://${container.host}:${container.getMappedPort(3306)}/filehub"
        )
    }

    override fun stop() {
        container.stop()
    }
}
