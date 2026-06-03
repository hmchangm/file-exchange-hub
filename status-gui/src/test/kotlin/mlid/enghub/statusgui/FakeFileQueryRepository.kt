package mlid.enghub.statusgui

import mlid.enghub.statusgui.repository.DeliveryRow
import mlid.enghub.statusgui.repository.FileQueryRepository
import mlid.enghub.statusgui.repository.FileRow
import mlid.enghub.statusgui.repository.PagedResult
import java.time.LocalDateTime

class FakeFileQueryRepository : FileQueryRepository {
    var searchResult = PagedResult(emptyList(), 0)
    var findByIdResult: FileRow? = null
    var findDeliveriesResult: List<DeliveryRow> = emptyList()
    var findMissingResult = PagedResult(emptyList(), 0)

    override fun search(uploaderId: String?, bucket: String?, status: String?, since: LocalDateTime?, page: Int, size: Int) = searchResult
    override fun findById(id: String) = findByIdResult
    override fun findDeliveries(fileId: String) = findDeliveriesResult
    override fun findMissing(consumerId: String, bucket: String?, since: LocalDateTime, page: Int, size: Int) = findMissingResult
}

fun sampleFileRow(
    id: String = "test-id-001",
    filename: String = "report_Q1.csv",
    bucket: String = "finance",
    uploaderId: String = "uploader-01",
    status: String = "REGISTERED",
) = FileRow(
    id = id,
    bucket = bucket,
    reportId = "r1",
    reportCategory = "cat1",
    objectKey = "finance/report_Q1.csv",
    filename = filename,
    contentType = "text/csv",
    fileSize = 48320,
    checksum = null,
    uploaderId = uploaderId,
    tags = null,
    status = status,
    remark = null,
    errorCode = null,
    registeredAt = LocalDateTime.of(2026, 6, 3, 9, 12, 34),
)

fun sampleDeliveryRow(fileId: String = "test-id-001") = DeliveryRow(
    id = "delivery-001",
    fileId = fileId,
    consumerId = "consumer-reporting",
    note = null,
    processedAt = LocalDateTime.of(2026, 6, 3, 9, 15, 2),
)
