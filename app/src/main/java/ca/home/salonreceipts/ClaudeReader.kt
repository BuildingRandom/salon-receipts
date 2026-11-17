package ca.home.salonreceipts

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/** The API key and model, kept in the app's private settings on the phone. */
object Settings {
    const val DEFAULT_MODEL = "claude-sonnet-5-5"
    private const val PREFS = "settings"

    fun apiKey(ctx: Context): String = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("apiKey", "") ?: ""
    fun model(ctx: Context): String =
        (ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("model", "") ?: "").ifBlank { DEFAULT_MODEL }

    fun save(ctx: Context, apiKey: String, model: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("apiKey", apiKey.trim())
            .putString("model", model.trim())
            .apply()
    }
}

/** What Claude found on the receipt. Any field can be missing. */
data class ParsedReceipt(
    val vendor: String?,
    val date: String?,
    val paymentMethod: String?,
    val items: List<Item>,
    val subtotal: Double?,
    val gstHst: Double?,
    val pst: Double?,
    val total: Double?,
    val notes: String?,
)

class ReadException(message: String) : Exception(message)

/** Sends one receipt photo to Claude (Anthropic Messages API) and turns the answer into a [ParsedReceipt]. */
object ClaudeReader {
    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"

    private fun prompt(): String = """
This photo is a store receipt. A hairstylist bought these supplies for her work.
Read the receipt and reply with ONLY a JSON object of this shape, no other text:
{"vendor": string, "date": "YYYY-MM-DD" or null, "paymentMethod": string or null,
 "items": [{"description": string, "qty": number, "unitPrice": number, "lineTotal": number, "category": string}],
 "subtotal": number or null, "gstHst": number or null, "pst": number or null, "total": number or null,
 "notes": string or null}
Rules:
- vendor: the store or business name as a person would say it (e.g. "Sally Beauty", "Costco").
- One entry per product line. Prices are before tax, in dollars. If a line shows a quantity, unitPrice is the price of one.
- Expand cryptic abbreviations into plain words only when you are confident (e.g. "NITRL GLV BLK 100CT" -> "Nitrile gloves, black, 100 count"). Otherwise keep the receipt's wording.
- Discounts or coupons are their own item with a negative lineTotal.
- gstHst is GST or HST combined; pst is PST/QST or any other tax. Use null if not shown.
- category must be exactly one of: ${CATEGORIES.joinToString(", ") { "\"" + it + "\"" }}.
- Do not invent anything. Use null for what you cannot read, and mention unreadable parts in notes.
- If the image is not a receipt, reply {"error": "not a receipt"}.
""".trim()

    /** Blocking network call: run it on Dispatchers.IO. */
    fun read(apiKey: String, model: String, photo: File): ParsedReceipt {
        if (apiKey.isBlank()) throw ReadException("Add your Anthropic API key in Settings so Claude can read receipts.")

        val imageB64 = Base64.encodeToString(photo.readBytes(), Base64.NO_WRAP)
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 4000)
            .put(
                "messages", JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "content", JSONArray()
                                .put(
                                    JSONObject()
                                        .put("type", "image")
                                        .put(
                                            "source", JSONObject()
                                                .put("type", "base64")
                                                .put("media_type", "image/jpeg")
                                                .put("data", imageB64)
                                        )
                                )
                                .put(JSONObject().put("type", "text").put("text", prompt()))
                        )
                )
            )

        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
        }

        val responseText: String
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw ReadException(friendlyError(code, responseText))
        } catch (e: UnknownHostException) {
            throw ReadException("No internet connection. Fill it in by hand, or try again when you're online.")
        } catch (e: SocketTimeoutException) {
            throw ReadException("Claude took too long to answer. Try again, or fill it in by hand.")
        } finally {
            conn.disconnect()
        }

        val text = try {
            val content = JSONObject(responseText).getJSONArray("content")
            (0 until content.length())
                .map { content.getJSONObject(it) }
                .filter { it.optString("type") == "text" }
                .joinToString("") { it.optString("text") }
        } catch (e: Exception) {
            throw ReadException("Got an answer Claude's service didn't format as expected. Try again.")
        }
        return parse(text)
    }

    private fun friendlyError(code: Int, body: String): String {
        val apiMsg = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        } catch (e: Exception) {
            ""
        }
        return when (code) {
            401 -> "The API key was rejected. Check it in Settings."
            403 -> "This API key isn't allowed to do that. Check the key in Settings."
            404 -> "The model name in Settings wasn't found. Reset it to $DEFAULT_MODEL." + if (apiMsg.isNotBlank()) " ($apiMsg)" else ""
            400 -> if (apiMsg.contains("credit", ignoreCase = true)) "Your Anthropic account is out of credit. Add credit at console.anthropic.com."
                   else "Claude couldn't accept this request: $apiMsg"
            429 -> "Too many requests or a spending limit was reached. Wait a minute and try again."
            500, 502, 503, 529 -> "Claude is busy right now. Try again in a minute."
            else -> "Couldn't read the receipt (error $code). $apiMsg".trim()
        }
    }

    /** Pulls the JSON object out of Claude's reply, tolerating a code fence or a stray sentence. */
    fun parse(text: String): ParsedReceipt {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw ReadException("Claude couldn't make sense of this receipt. Fill it in by hand.")
        val o = try {
            JSONObject(text.substring(start, end + 1))
        } catch (e: Exception) {
            throw ReadException("Claude couldn't make sense of this receipt. Fill it in by hand.")
        }
        if (o.has("error")) throw ReadException("That photo doesn't look like a receipt. Try another photo or fill it in by hand.")

        fun num(key: String): Double? = if (!o.has(key) || o.isNull(key)) null else o.optDouble(key).takeUnless { it.isNaN() }?.let { round2(it) }
        fun str(key: String): String? = if (!o.has(key) || o.isNull(key)) null else o.optString(key).ifBlank { null }

        val arr = o.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).mapNotNull { i ->
            val it = arr.optJSONObject(i) ?: return@mapNotNull null
            val qty = it.optDouble("qty").takeUnless { d -> d.isNaN() || d == 0.0 } ?: 1.0
            val unit = it.optDouble("unitPrice").takeUnless { d -> d.isNaN() }?.let(::round2)
            val line = it.optDouble("lineTotal").takeUnless { d -> d.isNaN() }?.let(::round2)
                ?: unit?.let { u -> round2(u * qty) }
            Item(
                description = it.optString("description", "").takeUnless { s -> s == "null" } ?: "",
                qty = qty,
                unitPrice = unit,
                lineTotal = line,
                category = it.optString("category").let { c -> if (c in CATEGORIES) c else "Other" },
            )
        }
        val date = str("date")?.takeIf { Regex("""\d{4}-\d{2}-\d{2}""").matches(it) }
        return ParsedReceipt(
            vendor = str("vendor"),
            date = date,
            paymentMethod = str("paymentMethod"),
            items = items,
            subtotal = num("subtotal"),
            gstHst = num("gstHst"),
            pst = num("pst"),
            total = num("total"),
            notes = str("notes"),
        )
    }
}
