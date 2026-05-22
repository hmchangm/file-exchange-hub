package tw.brandy.ironman.hub

import io.nats.client.Nats
import io.nats.client.api.StorageType
import io.nats.client.api.StreamConfiguration
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

class NatsTestResource : QuarkusTestResourceLifecycleManager {
    private val container =
        GenericContainer(DockerImageName.parse("nats:2.10-alpine"))
            .withCommand("-js")
            .withExposedPorts(4222)

    override fun start(): Map<String, String> {
        container.start()
        val natsUrl = "nats://${container.host}:${container.getMappedPort(4222)}"

        Nats.connect(natsUrl).use { nc ->
            val jsm = nc.jetStreamManagement()
            val streamConfig =
                StreamConfiguration
                    .builder()
                    .name("FILES")
                    .subjects("files.registered")
                    .storageType(StorageType.Memory)
                    .build()
            jsm.addStream(streamConfig)
        }

        return mapOf("quarkus.messaging.nats.servers" to natsUrl)
    }

    override fun stop() {
        container.stop()
    }
}
