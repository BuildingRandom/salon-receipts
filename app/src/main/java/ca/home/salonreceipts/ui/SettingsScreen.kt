package ca.home.salonreceipts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ca.home.salonreceipts.Settings

@Composable
fun SettingsScreen(onDone: () -> Unit, onBackup: () -> Unit) {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf(Settings.apiKey(ctx)) }
    var model by remember { mutableStateOf(Settings.model(ctx)) }
    var showKey by remember { mutableStateOf(false) }

    val saveAndClose = {
        Settings.save(ctx, key, model)
        onDone()
    }
    BackHandler(onBack = saveAndClose)

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Claude (reads the receipts)", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Create a key at console.anthropic.com → API keys, and paste it here. " +
                            "Each receipt costs roughly a cent or two. Set a monthly spending limit in the console.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = key, onValueChange = { key = it.trim() },
                        label = { Text("Anthropic API key") }, singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide key" else "Show key") }
                    OutlinedTextField(
                        value = model, onValueChange = { model = it },
                        label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { model = Settings.DEFAULT_MODEL }) { Text("Reset model to ${Settings.DEFAULT_MODEL}") }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Backup", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Receipts live only on this phone. Every so often, send yourself a backup: one zip with all " +
                            "receipts, photos and an item list. Save it to Drive or email it to yourself.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth()) { Text("Send a backup (zip)") }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tax notes for a commissioned salon employee", fontWeight = FontWeight.SemiBold)
                    listOf(
                        "Supplies her job requires her to buy go on Form T777, line 22900 of her return.",
                        "The salon must sign Form T2200 saying she has to pay for them herself. She keeps it and only shows it if the CRA asks.",
                        "Anything the salon pays her back for can't be claimed. Mark it Personal.",
                        "Basement chair: products a friend paid for aren't her expense. Tick \"Client paid me back\".",
                        "She may get the GST/HST back on claimed expenses with Form GST370.",
                        "Keep receipts for 6 years. Confirm the final claim with a tax preparer.",
                    ).forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Button(onClick = saveAndClose, modifier = Modifier.fillMaxWidth()) { Text("Save and go back") }
        }
    }
}
