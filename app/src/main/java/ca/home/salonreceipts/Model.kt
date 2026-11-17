package ca.home.salonreceipts

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

val CATEGORIES = listOf(
    "Hair colour & lightener",
    "Mixing bowls, brushes & foils",
    "Gloves & disposables",
    "Scissor sharpening & tool repair",
    "Tools & equipment",
    "Shampoo, treatment & styling products",
    "Cleaning & sanitation",
    "Towels, capes & laundry",
    "Education & licensing",
    "Advertising & business cards",
    "Other",
)

/** What a receipt was for. */
enum class Use(val key: String, val label: String) {
    SALON("salon", "Salon job"),
    HOME("home", "Home chair"),
    PERSONAL("personal", "Personal");

    companion object {
        fun from(key: String?): Use = entries.firstOrNull { it.key == key } ?: SALON
    }
}

data class Item(
    val description: String,
    val qty: Double,
    val unitPrice: Double?,
    val lineTotal: Double?,
    val category: String,
)

data class Receipt(
    val id: String,
    val vendor: String,
    val date: String,              // yyyy-MM-dd
    val paymentMethod: String,
    val use: Use,
    val reimbursed: Boolean,       // only meaningful for HOME: a client paid her back
    val items: List<Item>,
    val subtotal: Double?,
    val gstHst: Double?,
    val pst: Double?,
    val total: Double?,
    val notes: String,
    val photo: String?,            // file name inside filesDir/photos
    val createdAt: Long,
) {
    val itemsTotal: Double get() = items.sumOf { it.lineTotal ?: 0.0 }

    /** Counts toward the tax claim: salon job supplies, or home-chair supplies nobody paid her back for. */
    val claimable: Boolean get() = use == Use.SALON || (use == Use.HOME && !reimbursed)

    val year: String get() = date.take(4)
}

fun round2(v: Double): Double = round(v * 100.0) / 100.0

fun money(v: Double?): String = if (v == null) "" else "$" + String.format(Locale.US, "%,.2f", v)

/** Plain number for CSV files and text fields: 12.50 */
fun plain(v: Double?): String = if (v == null) "" else String.format(Locale.US, "%.2f", v)

/** Parses what a person typed ("$12.50") into a number, or null. */
fun parseMoney(s: String): Double? {
    val cleaned = s.replace(Regex("[^0-9.\\-]"), "")
    return cleaned.toDoubleOrNull()?.let { round2(it) }
}

fun nearlyEqual(a: Double, b: Double) = abs(a - b) <= 0.02

// ---------- JSON ----------

private fun JSONObject.optNum(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }

private fun JSONObject.putNum(key: String, v: Double?): JSONObject = put(key, v ?: JSONObject.NULL)

fun Item.toJson(): JSONObject = JSONObject()
    .put("description", description)
    .put("qty", qty)
    .putNum("unitPrice", unitPrice)
    .putNum("lineTotal", lineTotal)
    .put("category", category)

fun itemFromJson(o: JSONObject) = Item(
    description = o.optString("description", ""),
    qty = o.optNum("qty") ?: 1.0,
    unitPrice = o.optNum("unitPrice"),
    lineTotal = o.optNum("lineTotal"),
    category = o.optString("category", "Other").let { if (it in CATEGORIES) it else "Other" },
)

fun Receipt.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("vendor", vendor)
    .put("date", date)
    .put("paymentMethod", paymentMethod)
    .put("use", use.key)
    .put("reimbursed", reimbursed)
    .put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
    .putNum("subtotal", subtotal)
    .putNum("gstHst", gstHst)
    .putNum("pst", pst)
    .putNum("total", total)
    .put("notes", notes)
    .put("photo", photo ?: JSONObject.NULL)
    .put("createdAt", createdAt)

fun receiptFromJson(o: JSONObject): Receipt {
    val arr = o.optJSONArray("items") ?: JSONArray()
    return Receipt(
        id = o.getString("id"),
        vendor = o.optString("vendor", ""),
        date = o.optString("date", ""),
        paymentMethod = o.optString("paymentMethod", ""),
        use = Use.from(o.optString("use")),
        reimbursed = o.optBoolean("reimbursed", false),
        items = (0 until arr.length()).map { itemFromJson(arr.getJSONObject(it)) },
        subtotal = o.optNum("subtotal"),
        gstHst = o.optNum("gstHst"),
        pst = o.optNum("pst"),
        total = o.optNum("total"),
        notes = o.optString("notes", ""),
        photo = if (o.isNull("photo")) null else o.optString("photo").ifBlank { null },
        createdAt = o.optLong("createdAt", 0L),
    )
}
