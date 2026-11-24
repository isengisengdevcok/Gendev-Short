package com.gendev.shorts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gendev.shorts.R
import com.gendev.shorts.domain.model.EndpointNode
import com.gendev.shorts.domain.model.EndpointStatus
import com.gendev.shorts.ui.theme.AmberDegraded
import com.gendev.shorts.ui.theme.CoralError
import com.gendev.shorts.ui.theme.ElectricViolet
import com.gendev.shorts.ui.theme.ElectricVioletBright
import com.gendev.shorts.ui.theme.EmeraldHealthy
import com.gendev.shorts.ui.theme.GlassBorder
import com.gendev.shorts.ui.theme.ObsidianCard
import com.gendev.shorts.ui.theme.ObsidianElevated
import com.gendev.shorts.ui.theme.ObsidianSurface
import com.gendev.shorts.ui.theme.PureWhite
import com.gendev.shorts.ui.theme.SlateDim
import com.gendev.shorts.ui.theme.SlateMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyAndHealthSheet(
    cacheSizeBytes: Long,
    endpoints: List<EndpointNode>,
    isCheckingEndpoints: Boolean,
    onClearCache: () -> Unit,
    onRecheckEndpoints: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        contentColor = PureWhite,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.privacy_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                color = PureWhite,
            )

            // Privacy Guarantee Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(ObsidianCard)
                    .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = ElectricVioletBright,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.privacy_guarantee_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = PureWhite,
                    )
                }
                Text(
                    text = stringResource(R.string.privacy_guarantee_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateMuted,
                )
                Text(
                    text = stringResource(R.string.privacy_provider_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateDim,
                )
            }

            // Temporary Cache Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(ObsidianCard)
                    .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.cache_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhite,
                )
                Text(
                    text = stringResource(R.string.cache_size_label, formatByteCount(cacheSizeBytes)),
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricVioletBright,
                )
                Button(
                    onClick = onClearCache,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricViolet,
                        contentColor = PureWhite,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("clear_cache_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_clear_cache))
                }
            }

            // Multi-Source Endpoint Health Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(ObsidianCard)
                    .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.HealthAndSafety,
                            contentDescription = null,
                            tint = EmeraldHealthy,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.endpoints_section_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = PureWhite,
                        )
                    }
                }

                endpoints.forEach { node ->
                    EndpointRow(node = node)
                }

                OutlinedButton(
                    onClick = onRecheckEndpoints,
                    enabled = !isCheckingEndpoints,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("recheck_endpoints_button"),
                ) {
                    if (isCheckingEndpoints) {
                        CircularProgressIndicator(
                            color = ElectricVioletBright,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = PureWhite,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_recheck_endpoints),
                        color = PureWhite,
                    )
                }
            }
        }
    }
}

@Composable
private fun EndpointRow(node: EndpointNode) {
    val (dotColor, statusText) = when (node.status) {
        EndpointStatus.HEALTHY -> EmeraldHealthy to stringResource(R.string.status_healthy)
        EndpointStatus.DEGRADED -> AmberDegraded to stringResource(R.string.status_degraded)
        EndpointStatus.UNREACHABLE -> CoralError to stringResource(R.string.status_unreachable)
        EndpointStatus.UNKNOWN -> SlateDim to stringResource(R.string.status_unknown)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianElevated)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                color = PureWhite,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            node.lastLatencyMs?.let { ms ->
                Text(
                    text = stringResource(R.string.latency_ms, ms),
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateMuted,
                )
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = dotColor,
            )
        }
    }
}

private fun formatByteCount(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.2f MB", mb)
}
