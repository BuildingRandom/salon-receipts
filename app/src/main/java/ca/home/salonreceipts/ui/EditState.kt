package ca.home.salonreceipts.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ca.home.salonreceipts.Item
import ca.home.salonreceipts.ParsedReceipt
import ca.home.salonreceipts.Receipt
import ca.home.salonreceipts.Use
import ca.home.salonreceipts.money
import ca.home.salonreceipts.parseMoney
import ca.home.salonreceipts.plain
import ca.home.salonreceipts.round2
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

/** One item row on the review screen, held as the text the person sees. */
class ItemDraft(
    description: String = "",
    qty: String = "1",
    each: String = "",
    line: String = "",
    category: String = "Gloves & disposables",
) {
    var description by mutableStateOf(description)
    var qty by mutableStateOf(qty)
    var each by mutableStateOf(each)
    var line by mutableStateOf(line)
    var category by mutableStateOf(category)

    /** When qty or price each changes, work out the line total. */
    fun recalc() {
        val q = qty.toDoubleOrNull() ?: return
        val e = parseMoney(each) ?: return
        line = plain(round2(q * e))
    }

    fun lineValue(): Double? = parseMoney(line)

    fun isEmpty() = description.isBlank() && line.isBlank() && each.isBlank()

    fun toItem() = Item(
        description = description.trim(),
        qty = qty.toDoubleOrNull() ?: 1.0,
        unitPrice = parseMoney(each),
        lineTotal = parseMoney(line),
        category = category,
    )

    companion object {
        fun from(i: Item) = ItemDraft(
            description = i.description,
            qty = if (i.qty == Math.floor(i.qty)) i.qty.toLong().toString() else i.qty.toString(),
            each = plain(i.unitPrice),
            line = plain(i.lineTotal),
            category = i.category,
        )
    }
}

/** Everything on the review / edit screen. */
class EditState(val existing: Receipt?) {
    val id: String = existing?.id ?: UUID.randomUUID().toString()
    val isNew get() = existing == null

    var photo by mutableStateOf(existing?.photo)
    var vendor by mutableStateOf(existing?.vendor ?: "")
    var date by mutableStateOf(existing?.date ?: LocalDate.now().toString())
    var paymentMethod by mutableStateOf(existing?.paymentMethod ?: "")
    var use by mutableStateOf(existing?.use ?: Use.SALON)
    var reimbursed by mutableStateOf(existing?.reimbursed ?: false)
    val items = mutableStateListOf<ItemDraft>().apply {
        if (existing != null) addAll(existing.items.map { ItemDraft.from(it) })
    }
    var subtotal by mutableStateOf(plain(existing?.subtotal))
    var gstHst by mutableStateOf(plain(existing?.gstHst))
    var pst by mutableStateOf(plain(existing?.pst))
    var total by mutableStateOf(plain(existing?.total))
    var notes by mutableStateOf(existing?.notes ?: "")

    var busy by mutableStateOf(false)          // saving photo or waiting for Claude
    var message by mutableStateOf<String?>(null)
    var messageIsError by mutableStateOf(false)

    fun say(text: String?, error: Boolean = false) {
        message = text
        messageIsError = error
    }

    fun itemsTotal(): Double = items.sumOf { it.lineValue() ?: 0.0 }

    /** Plain-language warnings when the numbers don't add up. */
    fun mathWarnings(): List<String> {
        val out = mutableListOf<String>()
        val sub = parseMoney(subtotal)
        val itemsSum = round2(itemsTotal())
        if (sub != null && items.isNotEmpty() && abs(sub - itemsSum) > 0.02) {
            out += "Items add up to ${money(itemsSum)} but the subtotal says ${money(sub)}. Check for a missed item or discount."
        }
        val tot = parseMoney(total)
        if (sub != null && tot != null) {
            val calc = round2(sub + (parseMoney(gstHst) ?: 0.0) + (parseMoney(pst) ?: 0.0))
            if (abs(calc - tot) > 0.02) {
                out += "Subtotal + tax is ${money(calc)} but the total says ${money(tot)}."
            }
        }
        return out
    }

    fun applyParsed(p: ParsedReceipt) {
        p.vendor?.let { vendor = it }
        p.date?.let { date = it }
        p.paymentMethod?.let { paymentMethod = it }
        items.clear()
        items.addAll(p.items.map { ItemDraft.from(it) })
        if (items.isEmpty()) items.add(ItemDraft())
        subtotal = plain(p.subtotal)
        gstHst = plain(p.gstHst)
        pst = plain(p.pst)
        total = plain(p.total)
        p.notes?.let { notes = it }
    }

    /** Returns a problem to show, or null when it's fine to save. */
    fun validate(): String? {
        if (vendor.isBlank()) return "Add the store name before saving."
        if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(date.trim()) ||
            runCatching { LocalDate.parse(date.trim()) }.isFailure
        ) return "Enter the date as year-month-day, like 2026-11-02."
        return null
    }

    fun toReceipt(): Receipt = Receipt(
        id = id,
        vendor = vendor.trim(),
        date = date.trim(),
        paymentMethod = paymentMethod.trim(),
        use = use,
        reimbursed = use == Use.HOME && reimbursed,
        items = items.filterNot { it.isEmpty() }.map { it.toItem() },
        subtotal = parseMoney(subtotal),
        gstHst = parseMoney(gstHst),
        pst = parseMoney(pst),
        total = parseMoney(total),
        notes = notes.trim(),
        photo = photo,
        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
    )
}
