package mlid.enghub.statusgui.templates

import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.textInput
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import mlid.enghub.statusgui.repository.FileRow
import java.time.LocalDate

fun renderFileSearch(
    rows: List<FileRow>,
    total: Long,
    page: Int,
    uploaderId: String?,
    bucket: String?,
    status: String?,
    since: LocalDate?,
): String = layout("File Search", "search") {
    h2 { +"File Search" }
    form(action = "/files", method = FormMethod.get, classes = "filter-bar") {
        label {
            +"Uploader ID"
            textInput(name = "uploaderId") {
                placeholder = "any"
                value = uploaderId ?: ""
            }
        }
        label {
            +"Bucket"
            textInput(name = "bucket") {
                placeholder = "any"
                value = bucket ?: ""
            }
        }
        label {
            +"Status"
            select {
                name = "status"
                option { value = ""; +"any" }
                option {
                    value = "REGISTERED"
                    selected = status == "REGISTERED"
                    +"REGISTERED"
                }
                option {
                    value = "FAILED"
                    selected = status == "FAILED"
                    +"FAILED"
                }
            }
        }
        label {
            +"Since"
            input(type = InputType.date, name = "since") {
                value = since?.toString() ?: ""
            }
        }
        button { +"Search" }
    }
    if (rows.isEmpty()) {
        p(classes = "empty-msg") { +"No files found." }
    } else {
        table {
            thead {
                tr {
                    th { +"ID" }
                    th { +"Filename" }
                    th { +"Bucket" }
                    th { +"Uploader" }
                    th { +"Status" }
                    th { +"Registered At" }
                }
            }
            tbody {
                rows.forEach { row ->
                    tr {
                        td { a(href = "/files/${row.id}") { +(row.id.take(8) + "…") } }
                        td { +row.filename }
                        td { +row.bucket }
                        td { +row.uploaderId }
                        td {
                            val css = if (row.status == "FAILED") "badge-failed" else "badge-registered"
                            span(classes = css) { +row.status }
                        }
                        td { +row.registeredAt.toString().replace("T", " ").take(16) }
                    }
                }
            }
        }
        div(classes = "pagination") {
            +"Showing ${page * 20 + 1}–${minOf((page + 1) * 20, total.toInt())} of $total"
            if (page > 0) {
                a(href = searchUrl(uploaderId, bucket, status, since, page - 1)) { +" ← Prev" }
            }
            if ((page + 1) * 20 < total) {
                a(href = searchUrl(uploaderId, bucket, status, since, page + 1)) { +" Next →" }
            }
        }
    }
}

private fun searchUrl(uploaderId: String?, bucket: String?, status: String?, since: LocalDate?, page: Int): String {
    val params = buildList {
        if (!uploaderId.isNullOrBlank()) add("uploaderId=$uploaderId")
        if (!bucket.isNullOrBlank()) add("bucket=$bucket")
        if (!status.isNullOrBlank()) add("status=$status")
        if (since != null) add("since=$since")
        if (page > 0) add("page=$page")
    }
    return "/files" + if (params.isEmpty()) "" else "?" + params.joinToString("&")
}
