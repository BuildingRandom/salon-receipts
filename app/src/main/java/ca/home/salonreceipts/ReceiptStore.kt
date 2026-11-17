package ca.home.salonreceipts

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import java.io.File

/**
 * Keeps every receipt in one JSON file in the app's private storage,
 * and the photos in a "photos" folder next to it. Nothing leaves the phone
 * except the photo sent to Claude for reading.
 */
object ReceiptStore {
    val receipts = mutableStateListOf<Receipt>()

    private lateinit var root: File
    private val dataFile get() = File(root, "receipts.json")

    val photosDir: File get() = File(root, "photos").apply { mkdirs() }

    fun init(context: Context) {
        if (::root.isInitialized) return
        root = context.filesDir
        load()
    }

    private fun load() {
        receipts.clear()
        if (!dataFile.exists()) return
        try {
            val arr = JSONArray(dataFile.readText())
            for (i in 0 until arr.length()) receipts.add(receiptFromJson(arr.getJSONObject(i)))
        } catch (e: Exception) {
            // Keep a copy of an unreadable file instead of overwriting it.
            dataFile.copyTo(File(root, "receipts-unreadable-${System.currentTimeMillis()}.json"), overwrite = true)
        }
    }

    private fun persist() {
        val arr = JSONArray().apply { receipts.forEach { put(it.toJson()) } }
        val tmp = File(root, "receipts.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(dataFile)) {
            dataFile.writeText(arr.toString())
            tmp.delete()
        }
    }

    fun upsert(r: Receipt) {
        val ix = receipts.indexOfFirst { it.id == r.id }
        if (ix >= 0) receipts[ix] = r else receipts.add(r)
        persist()
    }

    fun delete(r: Receipt) {
        receipts.removeAll { it.id == r.id }
        persist()
        r.photo?.let { File(photosDir, it).delete() }
    }

    fun photoFile(name: String?): File? = name?.let { File(photosDir, it) }?.takeIf { it.exists() }

    fun rawJson(): String = if (dataFile.exists()) dataFile.readText() else "[]"
}
