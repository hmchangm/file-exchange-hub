package mlid.enghub.statusgui.templates

import kotlinx.html.DIV
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import mlid.enghub.statusgui.repository.DeliveryRow
import mlid.enghub.statusgui.repository.FileRow

fun renderFileDetail(file: FileRow, deliveries: List<DeliveryRow>): String =
    layout("File Detail", "search") {
        a(href = "/files", classes = "back-link") { +"← Back to search" }
        h2 { +"File: ${file.filename}" }
        div(classes = "detail-grid") {
            field("ID", file.id)
            div {
                div(classes = "detail-label") { +"Status" }
                div {
                    val css = if (file.status == "FAILED") "badge-failed" else "badge-registered"
                    span(classes = css) { +file.status }
                }
            }
            field("Filename", file.filename)
            field("Bucket", file.bucket)
            field("Report ID", file.reportId)
            field("Report Category", file.reportCategory)
            field("Uploader", file.uploaderId)
            field("File Size", "${file.fileSize} bytes")
            field("Content Type", file.contentType)
            field("Checksum", file.checksum)
            field("Object Key", file.objectKey)
            field("Tags", file.tags)
            field("Remark", file.remark)
            field("Error Code", file.errorCode)
            field("Registered At", file.registeredAt.toString().replace("T", " "))
        }
        h3 { +"Delivery Records" }
        if (deliveries.isEmpty()) {
            p(classes = "empty-msg") { +"No consumer has processed this file yet." }
        } else {
            table {
                thead {
                    tr {
                        th { +"Consumer" }
                        th { +"Processed At" }
                        th { +"Note" }
                    }
                }
                tbody {
                    deliveries.forEach { d ->
                        tr {
                            td { +d.consumerId }
                            td { +d.processedAt.toString().replace("T", " ").take(19) }
                            td { +(d.note ?: "—") }
                        }
                    }
                }
            }
        }
    }

private fun DIV.field(labelText: String, value: String?) {
    div {
        div(classes = "detail-label") { +labelText }
        div { +(value ?: "—") }
    }
}
