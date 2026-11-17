package ca.home.salonreceipts

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import ca.home.salonreceipts.ui.AppTheme
import ca.home.salonreceipts.ui.EditScreen
import ca.home.salonreceipts.ui.EditState
import ca.home.salonreceipts.ui.HomeScreen
import ca.home.salonreceipts.ui.ItemDraft
import ca.home.salonreceipts.ui.SettingsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReceiptStore.init(applicationContext)
        setContent { AppTheme { App() } }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Edit(val state: EditState) : Screen
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var year by rememberSaveable { mutableStateOf(LocalDate.now().year.toString()) }
    var hasKey by remember { mutableStateOf(Settings.apiKey(ctx).isNotBlank()) }
    // Where the camera writes the full-size photo before it's shrunk and kept.
    var pendingCameraFile by rememberSaveable { mutableStateOf<String?>(null) }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    /** Shrinks and keeps the photo, opens the review screen, and asks Claude to read it. */
    fun startFromPhoto(uri: Uri, cleanup: File?) {
        val st = EditState(null)
        st.busy = true
        st.say("Saving the photo…")
        screen = Screen.Edit(st)
        scope.launch {
            val name = "${st.id}.jpg"
            val ok = withContext(Dispatchers.IO) {
                try {
                    Images.saveUprightJpeg(ctx, uri, File(ReceiptStore.photosDir, name))
                    true
                } catch (e: Exception) {
                    false
                } finally {
                    cleanup?.delete()
                }
            }
            if (!ok) {
                st.busy = false
                st.items.add(ItemDraft())
                st.say("Couldn't open that photo. Try again, or fill in the details by hand.", error = true)
                return@launch
            }
            st.photo = name
            readWithClaude(scope, st, ctx)
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val path = pendingCameraFile
        pendingCameraFile = null
        val file = path?.let { File(it) }
        if (saved && file != null && file.exists() && file.length() > 0) {
            startFromPhoto(Uri.fromFile(file), cleanup = file)
        } else {
            file?.delete()
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) startFromPhoto(uri, cleanup = null)
    }

    fun snap() {
        val dir = File(ctx.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "camera-${System.currentTimeMillis()}.jpg")
        pendingCameraFile = file.absolutePath
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        try {
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            toast("No camera app found. Use \"Choose a photo\" instead.")
        }
    }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            year = year,
            onYear = { year = it },
            hasApiKey = hasKey,
            onSnap = { snap() },
            onPick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onManual = {
                val st = EditState(null)
                st.items.add(ItemDraft())
                screen = Screen.Edit(st)
            },
            onOpen = { screen = Screen.Edit(EditState(it)) },
            onExport = {
                try {
                    Export.shareReports(ctx, year)
                } catch (e: Exception) {
                    toast("Couldn't build the reports: ${e.message}")
                }
            },
            onSettings = { screen = Screen.Settings },
        )

        Screen.Settings -> SettingsScreen(
            onDone = {
                hasKey = Settings.apiKey(ctx).isNotBlank()
                screen = Screen.Home
            },
            onBackup = {
                try {
                    Export.shareBackup(ctx)
                } catch (e: Exception) {
                    toast("Couldn't build the backup: ${e.message}")
                }
            },
        )

        is Screen.Edit -> {
            val st = s.state
            EditScreen(
                st = st,
                onSave = {
                    val problem = st.validate()
                    if (problem != null) {
                        st.say(problem, error = true)
                    } else {
                        val r = st.toReceipt()
                        ReceiptStore.upsert(r)
                        year = r.year
                        toast("Receipt saved")
                        screen = Screen.Home
                    }
                },
                onCancel = {
                    // A new receipt that's thrown away shouldn't leave its photo behind.
                    if (st.isNew) ReceiptStore.photoFile(st.photo)?.delete()
                    screen = Screen.Home
                },
                onDelete = {
                    st.existing?.let { ReceiptStore.delete(it) }
                    toast("Receipt deleted")
                    screen = Screen.Home
                },
                onRetryRead = { readWithClaude(scope, st, ctx) },
            )
        }
    }
}

/** Sends the saved photo to Claude and fills the review screen with what comes back. */
private fun readWithClaude(scope: CoroutineScope, st: EditState, ctx: android.content.Context) {
    val photo = ReceiptStore.photoFile(st.photo) ?: return
    val key = Settings.apiKey(ctx)
    if (key.isBlank()) {
        st.busy = false
        if (st.items.isEmpty()) st.items.add(ItemDraft())
        st.say("No API key yet, so fill in the details from the photo. Add a key in Settings to have Claude read receipts.")
        return
    }
    st.busy = true
    st.say("Claude is reading the receipt… this usually takes 10 to 30 seconds.")
    scope.launch {
        try {
            val parsed = withContext(Dispatchers.IO) { ClaudeReader.read(key, Settings.model(ctx), photo) }
            st.applyParsed(parsed)
            st.say("Check the store, date and prices against the photo, set what it was for, then Save.")
        } catch (e: ReadException) {
            if (st.items.isEmpty()) st.items.add(ItemDraft())
            st.say(e.message ?: "Couldn't read the receipt. Fill it in by hand.", error = true)
        } catch (e: Exception) {
            if (st.items.isEmpty()) st.items.add(ItemDraft())
            st.say("Couldn't reach Claude (${e.javaClass.simpleName}). Check your connection, then tap Try again.", error = true)
        } finally {
            st.busy = false
        }
    }
}
