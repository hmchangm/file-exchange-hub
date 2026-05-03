package tw.brandy.ironman.hub

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

class NatsTestResource : QuarkusTestResourceLifecycleManager {

    private val container = GenericContainer(DockerImageName.parse("nats:2.10-alpine"))
        .withCommand("-js")
        .withExposedPorts(4222)

    override fun start(): Map<String, String> {
        container.start()
        val natsUrl = "nats://${container.host}:${container.getMappedPort(4222)}"
        return mapOf("quarkus.messaging.nats.servers" to natsUrl)
    }

    override fun stop() {
        container.stop()
    }
}
