package com.quio.ytm.ui.screens.dialogs

import com.quio.ytm.data.local.entity.TrackEntity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.quio.ytm.ui.theme.BgMain
import com.quio.ytm.ui.theme.StatusError
import com.quio.ytm.ui.theme.TextMuted
import com.quio.ytm.ui.theme.TextPrimary
import com.quio.ytm.ui.theme.TextSecondary

@Composable
fun UnlikeConfirmationDialog(
    track: TrackEntity,
    onConfirmUnlike: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgMain,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Remove from Liked Songs?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "Are you sure you want to remove \"${track.title}\" by ${track.artist} from your YouTube Music Liked Songs?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "🛡️ Safe Guard: Songs cannot be removed in bulk. This action applies only to this single track.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmUnlike,
                colors = ButtonDefaults.buttonColors(containerColor = StatusError)
            ) {
                Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
