package com.example.greetingcard.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.greetingcard.R
import com.example.greetingcard.data.Backup
import com.example.greetingcard.ui.theme.ruby
import com.example.greetingcard.ui.theme.rubyText

/** Opens Android's file picker to create a backup file; [onPick] gets where to write it. */
@Composable
fun rememberBackupSaver(onPick: (Uri) -> Unit): (fileName: String) -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Backup.MIME_TYPE)) { uri ->
        uri?.let(onPick)
    }
    return { fileName -> launcher.launch(fileName) }
}

/** Opens Android's file picker to choose a backup to restore. */
@Composable
fun rememberBackupPicker(onPick: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onPick)
    }
    return { launcher.launch(Backup.OPEN_TYPES) }
}

/** The ruby "Save a backup" button, a quieter restore button next to it, and when the last backup was. */
@Composable
fun BackupButtons(lastBackup: String, onSave: () -> Unit, onRestore: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.ruby,
                    contentColor = Color.White,
                ),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(painterResource(R.drawable.ic_gem), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save a backup")
            }
            TextButton(onClick = onRestore) {
                Text("Restore", color = MaterialTheme.colorScheme.rubyText)
            }
        }
        SecondaryText(lastBackup)
    }
}

/** Says what a backup holds and that restoring replaces everything, before doing it. */
@Composable
fun RestoreDialog(
    backup: Backup.Result.Ok,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val made = backupDate(backup.createdAt)?.let { "Saved $it · " }.orEmpty()
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painterResource(R.drawable.ic_gem),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.rubyText,
            )
        },
        title = { Text("Restore this backup?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(made + backupSummary(backup.data), style = MaterialTheme.typography.bodyLarge)
                Text(
                    "This replaces everything in the app on this phone. You can undo it right after.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Restore", color = MaterialTheme.colorScheme.rubyText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
