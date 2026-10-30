package nl.spellenweekend.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Melding bij het opstarten als er een nieuwere app-versie op GitHub staat. */
@Composable
fun UpdateDialog(vm: AppViewModel) {
    val update by vm.update.collectAsStateWithLifecycle()
    val progress by vm.updateProgress.collectAsStateWithLifecycle()
    val u = update ?: return
    val downloading = progress != null

    AlertDialog(
        onDismissRequest = { if (!downloading) vm.dismissUpdate() },
        title = { Text("Nieuwe versie ${u.versionName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Je gebruikt versie ${vm.currentVersionName}. Werk de app bij om alle nieuwe functies en oplossingen te krijgen. " +
                        "Je gegevens blijven bewaard.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (u.notes.isNotBlank()) {
                    Text(
                        u.notes,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
                progress?.let { p ->
                    if (p < 0f) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    Text("Bezig met downloaden…", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = { vm.installUpdate() }, enabled = !downloading) { Text("Bijwerken") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { vm.downloadUpdateInBrowser() }, enabled = !downloading) { Text("Downloaden") }
                TextButton(onClick = { vm.dismissUpdate() }, enabled = !downloading) { Text("Later") }
            }
        },
    )
}
