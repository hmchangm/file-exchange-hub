package mlid.enghub.statusgui

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

fun connectDatabase(): Database {
    val config = HikariConfig().apply {
        jdbcUrl = "jdbc:mariadb://${env("DB_HOST")}:${env("DB_PORT", "3306")}/${env("DB_NAME")}"
        username = env("DB_USER")
        password = env("DB_PASS")
        maximumPoolSize = 5
        isReadOnly = true
        poolName = "status-gui"
    }
    return Database.connect(HikariDataSource(config))
}

internal fun env(name: String, default: String? = null): String =
    System.getenv(name) ?: default ?: error("Missing required env var: $name")
