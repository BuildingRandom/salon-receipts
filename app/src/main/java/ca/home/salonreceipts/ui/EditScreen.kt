@file:OptIn(ExperimentalMaterial3Api::class)

package ca.home.salonreceipts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ca.home.salonreceipts.CATEGORIES
import ca.home.salonreceipts.Images
import ca.home.salonreceipts.ReceiptStore
import ca.home.salonreceipts.Use
import ca.home.salonreceipts.money

private val Money = KeyboardOptions(keyboardType = KeyboardType.Decimal)
private val Words = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

@Composable
fun EditScreen(
    st: EditState,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onRetryRead: (() -> Unit)?,
) {
    var askDelete by remember { mutableStateOf(false) }
    var bigPhoto by remember { mutableStateOf(false) }
    BackHandler(onBack = onCancel)

    val photoBmp = remember(st.photo) { ReceiptStore.photoFile(st.photo)?.let { Images.loadPreview(it) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = onSave, enabled = !st.busy, modifier = Modifier.weight(1f)) { Text("Save receipt") }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (st.isNew) "Check the receipt" else "Edit receipt",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            st.message?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (st.messageIsError) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (st.busy) {
                            CircularProgressIndicator(Modifier.padding(end = 12.dp).size(20.dp), strokeWidth = 2.dp)
                        }
                        Text(msg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (onRetryRead != null && st.messageIsError && !st.busy && st.photo != null) {
                            TextButton(onClick = onRetryRead) { Text("Try again") }
                        }
                    }
                }
            }

            if (photoBmp != null) {
                Image(
                    bitmap = photoBmp.asImageBitmap(),
                    contentDescription = "Receipt photo. Tap to enlarge.",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(max = if (bigPhoto) 900.dp else 300.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { bigPhoto = !bigPhoto },
                )
            }

            Section {
                OutlinedTextField(
                    value = st.vendor, onValueChange = { st.vendor = it },
                    label = { Text("Store") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = st.date, onValueChange = { st.date = it },
                        label = { Text("Date (yyyy-mm-dd)") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = st.paymentMethod, onValueChange = { st.paymentMethod = it },
                        label = { Text("Paid with") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                Text("What was it for?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Use.entries.forEach { u ->
                        if (st.use == u) {
                            Button(onClick = { st.use = u }, modifier = Modifier.weight(1f)) { Text(u.label, maxLines = 1) }
                        } else {
                            OutlinedButton(onClick = { st.use = u }, modifier = Modifier.weight(1f)) { Text(u.label, maxLines = 1) }
                        }
                    }
                }
                if (st.use == Use.HOME) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { st.reimbursed = !st.reimbursed }) {
                        Checkbox(checked = st.reimbursed, onCheckedChange = { st.reimbursed = it })
                        Text("Client paid me back for these supplies")
                    }
                }
            }

            Section {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Items", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        st.items.add(ItemDraft(category = st.items.lastOrNull()?.category ?: "Other"))
                    }) { Text("Add item") }
                }
                if (st.items.isEmpty()) {
                    Text(
                        if (st.busy) "Items will appear here once Claude has read the receipt." else "No items yet. Tap Add item.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                st.items.forEachIndexed { ix, item ->
                    if (ix > 0) HorizontalDivider()
                    ItemEditor(item, onRemove = { st.items.remove(item) })
                }
                if (st.items.isNotEmpty()) {
                    Text(
                        "Items total: ${money(st.itemsTotal())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Section {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MoneyField("Subtotal", st.subtotal, { st.subtotal = it }, Modifier.weight(1f))
                    MoneyField("GST/HST", st.gstHst, { st.gstHst = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MoneyField("PST/other tax", st.pst, { st.pst = it }, Modifier.weight(1f))
                    MoneyField("Total paid", st.total, { st.total = it }, Modifier.weight(1f))
                }
                st.mathWarnings().forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Warn)
                }
                OutlinedTextField(
                    value = st.notes, onValueChange = { st.notes = it },
                    label = { Text("Notes") },
                    placeholder = { Text("Anything the receipt doesn't say, e.g. which client it was for") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, keyboardOptions = Words,
                )
            }

            if (!st.isNew) {
                TextButton(onClick = { askDelete = true }) {
                    Text("Delete this receipt", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Delete this receipt?") },
            text = { Text("The details and the photo are removed from the phone for good.") },
            confirmButton = {
                TextButton(onClick = { askDelete = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun MoneyField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, keyboardOptions = Money, modifier = modifier,
    )
}

@Composable
private fun ItemEditor(item: ItemDraft, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = item.description, onValueChange = { item.description = it },
            label = { Text("Item") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = Words,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = item.qty, onValueChange = { item.qty = it; item.recalc() },
                label = { Text("Qty") }, singleLine = true, keyboardOptions = Money, modifier = Modifier.weight(0.7f),
            )
            OutlinedTextField(
                value = item.each, onValueChange = { item.each = it; item.recalc() },
                label = { Text("Each") }, singleLine = true, keyboardOptions = Money, modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = item.line, onValueChange = { item.line = it },
                label = { Text("Line total") }, singleLine = true, keyboardOptions = Money, modifier = Modifier.weight(1.1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(item.category, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(" ▾")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    CATEGORIES.forEach { c ->
                        DropdownMenuItem(text = { Text(c) }, onClick = { item.category = c; menuOpen = false })
                    }
                }
            }
            TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
        }
    }
}
