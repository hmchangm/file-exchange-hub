package mlid.enghub.statusgui.templates

import kotlinx.html.BODY
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe

fun layout(title: String, activeTab: String, block: BODY.() -> Unit): String =
    createHTML().html {
        head {
            meta(charset = "UTF-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1")
            title("File Hub — $title")
            style {
                unsafe {
                    raw(
                        """
                        body{font-family:sans-serif;margin:0;color:#333}
                        nav{background:#1565c0;padding:10px 20px;display:flex;gap:24px}
                        nav a{color:white;text-decoration:none;padding-bottom:2px}
                        nav a.active{border-bottom:2px solid white}
                        .container{max-width:1200px;margin:0 auto;padding:20px}
                        .filter-bar{background:#f5f5f5;padding:12px 16px;display:flex;gap:12px;flex-wrap:wrap;align-items:flex-end;margin-bottom:16px;border-radius:4px}
                        .filter-bar label{display:flex;flex-direction:column;font-size:.75rem;font-weight:bold;text-transform:uppercase;color:#666;gap:4px}
                        input,select{padding:5px 8px;border:1px solid #ccc;border-radius:3px;font-size:.9rem}
                        button{padding:6px 16px;background:#1565c0;color:white;border:none;border-radius:3px;cursor:pointer;font-size:.9rem}
                        table{width:100%;border-collapse:collapse}
                        th{background:#e3f2fd;text-align:left;padding:8px 12px;font-size:.85rem}
                        td{padding:7px 12px;font-size:.85rem;border-top:1px solid #eee}
                        tr:nth-child(even) td{background:#fafafa}
                        .badge-registered{background:#e8f5e9;color:#2e7d32;padding:2px 8px;border-radius:10px;font-size:.8rem}
                        .badge-failed{background:#ffebee;color:#c62828;padding:2px 8px;border-radius:10px;font-size:.8rem}
                        .pagination{margin-top:12px;font-size:.85rem;color:#666}
                        .pagination a{color:#1565c0;margin:0 6px}
                        .detail-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px 24px;margin-bottom:20px}
                        .detail-label{font-size:.75rem;font-weight:bold;text-transform:uppercase;color:#666}
                        h2{margin-bottom:16px}
                        a.back-link{color:#1565c0;font-size:.85rem;display:inline-block;margin-top:12px}
                        .error-msg{color:#c62828;background:#ffebee;padding:10px;border-radius:4px;margin-bottom:12px}
                        .empty-msg{color:#666;padding:40px;text-align:center}
                        """.trimIndent(),
                    )
                }
            }
        }
        body {
            val bodyCtx = this
            nav {
                a(href = "/files", classes = if (activeTab == "search") "active" else null) { +"File Search" }
                a(href = "/missing", classes = if (activeTab == "missing") "active" else null) { +"Missing Files" }
            }
            div(classes = "container") { bodyCtx.block() }
        }
    }
