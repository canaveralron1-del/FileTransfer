package com.example.filetransfer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.filetransfer.data.model.ActiveTransfer
import com.example.filetransfer.ui.theme.LocalStatusColors
import com.example.filetransfer.ui.theme.TabularNumbers

private fun formatEta(seconds: Long): String {
    if (seconds < 0) return "--:--"
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

@Composable
fun TransferCard(
    transfer: ActiveTransfer?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lastTransfer = remember { mutableStateOf<ActiveTransfer?>(null) }
    if (transfer != null) lastTransfer.value = transfer

    AnimatedVisibility(
        visible = transfer != null,
        enter = slideInVertically(animationSpec = tween(300)) { it / 2 } + fadeIn(tween(300)),
        exit = slideOutVertically(animationSpec = tween(300)) { it / 2 } + fadeOut(tween(300)),
        modifier = modifier
    ) {
        val t = lastTransfer.value ?: return@AnimatedVisibility

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = t.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cancel transfer",
                            tint = LocalStatusColors.current.failed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                val target = if (t.totalBytes > 0) {
                    (t.transferredBytes.toFloat() / t.totalBytes).coerceIn(0f, 1f)
                } else 0f
                val animated by animateFloatAsState(
                    targetValue = target,
                    animationSpec = tween(250),
                    label = "transferProgress"
                )
                LinearProgressIndicator(
                    progress = { animated },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${formatBytes(t.transferredBytes)} / ${formatBytes(t.totalBytes)}",
                        style = MaterialTheme.typography.labelMedium + TabularNumbers,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (t.speedBytesPerSec > 0) "${formatBytes(t.speedBytesPerSec)}/s" else "--",
                        style = MaterialTheme.typography.labelMedium + TabularNumbers,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "ETA ${formatEta(t.etaSeconds)}",
                        style = MaterialTheme.typography.labelMedium + TabularNumbers,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
