package ca.home.salonreceipts

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds the tax-time CSV reports and the full backup, and hands them to Android's share menu. */
object Export {

    private fun csv(rows: List<List<Any?>>): String = rows.joinToString("\r\n", postfix = "\r\n") { row ->
        row.joinToString(",") { v ->
            val s = v?.toString() ?: ""
            if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        }
    }

    fun itemsCsv(receipts: List<Receipt>): String {
        val rows = mutableListOf<List<Any?>>(
            listOf(
                "Date", "Store", "Used for", "Client paid back", "Category", "Item", "Qty", "Price each", "Line total",
                "Receipt subtotal", "GST/HST", "PST/other tax", "Receipt total", "Paid with", "Notes", "Claimable",
            )
        )
        for (r in receipts.sortedBy { it.date }) {
            val items = r.items.ifEmpty { listOf(Item("", 0.0, null, null, "")) }
            items.forEachIndexed { ix, i ->
                val first = ix == 0
                rows.add(
                    listOf(
                        r.date, r.vendor, r.use.label,
                        if (r.use == Use.HOME) (if (r.reimbursed) "Yes" else "No") else "",
                        i.category, i.description,
                        if (i.description.isBlank() && i.lineTotal == null) "" else trimQty(i.qty),
                        plain(i.unitPrice), plain(i.lineTotal),
                        if (first) plain(r.subtotal) else "",
                        if (first) plain(r.gstHst) else "",
                        if (first) plain(r.pst) else "",
                        if (first) plain(r.total) else "",
                        if (first) r.paymentMethod else "",
                        if (first) r.notes else "",
                        if (r.claimable) "Yes" else "No",
                    )
                )
            }
        }
        return csv(rows)
    }

    fun summaryCsv(year: String, receipts: List<Receipt>): String {
        val claim = receipts.filter { it.claimable }
        val salon = mutableMapOf<String, Double>()
        val home = mutableMapOf<String, Double>()
        for (r in claim) for (i in r.items) {
            val target = if (r.use == Use.SALON) salon else home
            target[i.category] = (target[i.category] ?: 0.0) + (i.lineTotal ?: 0.0)
        }
        val rows = mutableListOf<List<Any?>>(
            listOf("Salon supply expenses $year"),
            emptyList(),
            listOf("Category", "Salon job", "Home chair (not paid back)", "Total"),
        )
        var ts = 0.0
        var th = 0.0
        for (c in CATEGORIES) {
            val a = salon[c] ?: 0.0
            val b = home[c] ?: 0.0
            if (a == 0.0 && b == 0.0) continue
            ts += a; th += b
            rows.add(listOf(c, plain(a), plain(b), plain(a + b)))
        }
        rows.add(listOf("Total before tax", plain(ts), plain(th), plain(ts + th)))
        rows.add(emptyList())
        rows.add(listOf("GST/HST paid on these purchases", plain(claim.sumOf { it.gstHst ?: 0.0 })))
        rows.add(listOf("PST/other tax paid on these purchases", plain(claim.sumOf { it.pst ?: 0.0 })))
        rows.add(emptyList())
        rows.add(listOf("By store", "Spent (before tax)"))
        claim.groupBy { it.vendor.ifBlank { "Unknown" } }
            .mapValues { (_, rs) -> rs.sumOf { it.itemsTotal } }
            .entries.sortedByDescending { it.value }
            .forEach { rows.add(listOf(it.key, plain(it.value))) }
        rows.add(emptyList())
        rows.add(listOf("Receipts counted: ${claim.size} of ${receipts.size}. Personal and paid-back receipts are left out."))
        return csv(rows)
    }

    private fun trimQty(q: Double): String = if (q == Math.floor(q)) q.toLong().toString() else q.toString()

    private fun exportDir(ctx: Context): File = File(ctx.cacheDir, "exports").apply {
        mkdirs()
        listFiles()?.forEach { it.delete() } // old exports
    }

    private fun uriFor(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)

    /** Writes both CSV reports for [year] and opens the share menu (email, Drive, Files…). */
    fun shareReports(ctx: Context, year: String) {
        val rs = ReceiptStore.receipts.filter { it.year == year }
        val dir = exportDir(ctx)
        val items = File(dir, "salon-receipts-items-$year.csv").apply { writeText(itemsCsv(rs)) }
        val summary = File(dir, "salon-receipts-summary-$year.csv").apply { writeText(summaryCsv(year, rs)) }
        share(ctx, listOf(items, summary), "text/csv", "Salon receipts $year")
    }

    /** Zips every receipt, every photo and this year's reports into one file and opens the share menu. */
    fun shareBackup(ctx: Context) {
        val dir = exportDir(ctx)
        val zip = File(dir, "salon-receipts-backup-${LocalDate.now()}.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { z ->
            z.putNextEntry(ZipEntry("receipts.json"))
            z.write(ReceiptStore.rawJson().toByteArray())
            z.closeEntry()
            val all = ReceiptStore.receipts.toList()
            z.putNextEntry(ZipEntry("all-items.csv"))
            z.write(itemsCsv(all).toByteArray())
            z.closeEntry()
            for (r in all) {
                val f = ReceiptStore.photoFile(r.photo) ?: continue
                val safeVendor = r.vendor.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').take(30).ifBlank { "receipt" }
                z.putNextEntry(ZipEntry("photos/${r.date}_${safeVendor}_${r.id.take(6)}.jpg"))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        share(ctx, listOf(zip), "application/zip", "Salon receipts backup")
    }

    private fun share(ctx: Context, files: List<File>, mime: String, subject: String) {
        val uris = ArrayList(files.map { uriFor(ctx, it) })
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.type = mime
        // ClipData carries the read permission through the share menu to whichever app is picked.
        val clip = ClipData.newRawUri(subject, uris[0])
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        send.clipData = clip
        send.putExtra(Intent.EXTRA_SUBJECT, subject)
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Send $subject"))
    }
}
