@file:OptIn(ExperimentalMaterial3Api::class)

package ca.home.salonreceipts.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.home.salonreceipts.Receipt
import ca.home.salonreceipts.ReceiptStore
import ca.home.salonreceipts.Use
import ca.home.salonreceipts.Images
import ca.home.salonreceipts.money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    year: String,
    onYear: (String) -> Unit,
    hasApiKey: Boolean,
    onSnap: () -> Unit,
    onPick: () -> Unit,
    onManual: () -> Unit,
    onOpen: (Receipt) -> Unit,
    onExport: () -> Unit,
    onSettings: () -> Unit,
) {
    val all = ReceiptStore.receipts
    val years = (all.map { it.year }.filter { it.length == 4 } +
        LocalDate.now().year.toString() + (LocalDate.now().year - 1).toString())
        .distinct().sortedDescending()
    val rs = all.filter { it.year == year }.sortedByDescending { it.date }

    val salon = rs.filter { it.use == Use.SALON }.sumOf { it.itemsTotal }
    val home = rs.filter { it.use == Use.HOME && !it.reimbursed }.sumOf { it.itemsTotal }
    val tax = rs.filter { it.claimable }.sumOf { it.gstHst ?: 0.0 }
    val byCat = rs.filter { it.claimable }.flatMap { it.items }
        .groupBy { it.category }
        .mapValues { (_, v) -> v.sumOf { it.lineTotal ?: 0.0 } }
        .entries.sortedByDescending { it.value }
        .filter { it.value != 0.0 }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Salon Receipts", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Supplies paid out of pocket, by store, item and price",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onSettings) { Text("Settings") }
                }
            }
            if (!hasApiKey) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onSettings),
                    ) {
                        Text(
                            "Add your Anthropic API key in Settings so Claude can read receipts for you. " +
                                "Until then you can still save photos and type the details in.",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                Button(onClick = onSnap, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                    Text("Snap a receipt", style = MaterialTheme.typography.titleMedium)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onPick, modifier = Modifier.weight(1f)) { Text("Choose a photo") }
                    OutlinedButton(onClick = onManual, modifier = Modifier.weight(1f)) { Text("Enter by hand") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tax year", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    years.take(3).forEach { y ->
                        FilterChip(selected = y == year, onClick = { onYear(y) }, label = { Text(y) })
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Year so far", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row {
                            Tile("Salon job supplies", money(salon), Modifier.weight(1f))
                            Tile("Home chair, not paid back", money(home), Modifier.weight(1f))
                        }
                        Row {
                            Tile("GST/HST paid (work items)", money(tax), Modifier.weight(1f))
                            Tile("Receipts", rs.size.toString(), Modifier.weight(1f))
                        }
                        if (byCat.isEmpty()) {
                            Text(
                                "Spending by category shows up here once you save receipts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            val max = byCat.maxOf { it.value }.coerceAtLeast(0.01)
                            byCat.forEach { (cat, v) -> CategoryBar(cat, v, (v / max).toFloat()) }
                        }
                        Button(onClick = onExport, modifier = Modifier.fillMaxWidth(), enabled = rs.isNotEmpty()) {
                            Text("Send $year reports (CSV)")
                        }
                    }
                }
            }
            item {
                Text("Receipts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (rs.isEmpty()) {
                item {
                    Text(
                        "No receipts for $year yet. Tap \"Snap a receipt\" to add the first one.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                }
            }
            var lastMonth = ""
            rs.forEach { r ->
                val month = monthLabel(r.date)
                if (month != lastMonth) {
                    lastMonth = month
                    item(key = "m-$month") {
                        Text(month, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                item(key = r.id) { ReceiptRow(r) { onOpen(r) } }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun monthLabel(date: String): String = try {
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.CANADA))
} catch (e: Exception) {
    "No date"
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CategoryBar(label: String, value: Double, fraction: Float) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(money(value), style = MaterialTheme.typography.bodyMedium)
        }
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun ReceiptRow(r: Receipt, onClick: () -> Unit) {
    val thumb = remember(r.photo) { ReceiptStore.photoFile(r.photo)?.let { Images.loadPreview(it, 200) } }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val shape = RoundedCornerShape(8.dp)
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp).clip(shape),
                )
            } else {
                Box(Modifier.size(52.dp).clip(shape).background(MaterialTheme.colorScheme.primaryContainer))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.vendor.ifBlank { "Unknown store" }, fontWeight = FontWeight.SemiBold)
                val n = r.items.size
                Text(
                    "${r.date} · $n item${if (n == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val tag = when {
                    r.use == Use.HOME && r.reimbursed -> "Home chair · paid back"
                    else -> r.use.label
                }
                Text(
                    tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (r.use) {
                        Use.SALON -> MaterialTheme.colorScheme.primary
                        Use.HOME -> if (r.reimbursed) Good else Warn
                        Use.PERSONAL -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(money(r.total ?: r.itemsTotal), fontWeight = FontWeight.SemiBold)
        }
    }
}
