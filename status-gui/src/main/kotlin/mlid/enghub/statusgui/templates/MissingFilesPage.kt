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
import kotlinx.html.p
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

fun renderMissingFiles(
    rows: List<FileRow>,
    total: Long,
    page: Int,
    consumerId: String?,
    bucket: String?,
    since: LocalDate?,
    searched: Boolean,
): String = layout("Missing Files", "missing") {
    h2 { +"Missing Files" }
    form(action = "/missing", method = FormMethod.get, classes = "filter-bar") {
        label {
            +"Consumer ID *"
            textInput(name = "consumerId") {
                placeholder = "required"
                value = consumerId ?: ""
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
            +"Since"
            input(type = InputType.date, name = "since") {
                value = since?.toString() ?: ""
            }
        }
        button { +"Search" }
    }
    when {
        !searched -> p(classes = "empty-msg") { +"Enter a Consumer ID to find files not yet processed by that consumer." }
        rows.isEmpty() -> p(classes = "empty-msg") { +"No missing files for this consumer." }
        else -> {
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
                    a(href = missingUrl(consumerId, bucket, since, page - 1)) { +" ← Prev" }
                }
                if ((page + 1) * 20 < total) {
                    a(href = missingUrl(consumerId, bucket, since, page + 1)) { +" Next →" }
                }
            }
        }
    }
}

private fun missingUrl(consumerId: String?, bucket: String?, since: LocalDate?, page: Int): String {
    val params = buildList {
        if (!consumerId.isNullOrBlank()) add("consumerId=${enc(consumerId!!)}")
        if (!bucket.isNullOrBlank()) add("bucket=${enc(bucket!!)}")
        if (since != null) add("since=$since")
        if (page > 0) add("page=$page")
    }
    return "/missing" + if (params.isEmpty()) "" else "?" + params.joinToString("&")
}

private fun enc(v: String): String = java.net.URLEncoder.encode(v, "UTF-8")
