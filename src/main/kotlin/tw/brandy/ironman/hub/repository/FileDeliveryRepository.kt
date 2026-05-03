package tw.brandy.ironman.hub.repository

import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import tw.brandy.ironman.hub.domain.FileDelivery

@ApplicationScoped
class FileDeliveryRepository : PanacheRepositoryBase<FileDelivery, String> {

    fun findByFileIdAndConsumerId(fileId: String, consumerId: String): FileDelivery? =
        find("fileId = :fileId and consumerId = :consumerId",
            mapOf("fileId" to fileId, "consumerId" to consumerId))
            .firstResult()
}
