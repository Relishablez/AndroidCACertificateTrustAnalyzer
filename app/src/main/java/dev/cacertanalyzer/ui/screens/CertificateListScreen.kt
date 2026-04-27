package dev.cacertanalyzer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cacertanalyzer.data.model.CertificateInfo
import dev.cacertanalyzer.data.model.StorageLocation
import dev.cacertanalyzer.data.model.TrustStatus
import dev.cacertanalyzer.ui.theme.RiskCritical
import dev.cacertanalyzer.ui.theme.RiskHigh
import dev.cacertanalyzer.ui.theme.RiskLow
import dev.cacertanalyzer.ui.theme.RiskMedium
import dev.cacertanalyzer.viewmodel.CertificateTab
import dev.cacertanalyzer.viewmodel.RiskLevel

/**
 * Screen displaying a searchable, filterable list of certificates.
 */
@Composable
fun CertificateListScreen(
    certificates: List<CertificateInfo>,
    selectedTab: CertificateTab,
    onTabChange: (CertificateTab) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onCertificateClick: (CertificateInfo) -> Unit,
    getRiskLevel: (CertificateInfo) -> RiskLevel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Search certificates...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            singleLine = true
        )

        // Tab Filters
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            edgePadding = 16.dp
        ) {
            CertificateTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onTabChange(tab) },
                    text = { Text(tab.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Certificate List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Text(
                    text = "${certificates.size} certificate(s) found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(certificates, key = { it.alias }) { certificate ->
                CertificateListItem(
                    certificate = certificate,
                    riskLevel = getRiskLevel(certificate),
                    onClick = { onCertificateClick(certificate) }
                )
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CertificateListItem(
    certificate: CertificateInfo,
    riskLevel: RiskLevel,
    onClick: () -> Unit
) {
    val riskColor = when (riskLevel) {
        RiskLevel.LOW -> RiskLow
        RiskLevel.MEDIUM -> RiskMedium
        RiskLevel.HIGH -> RiskHigh
        RiskLevel.CRITICAL -> RiskCritical
    }

    val animatedColor by animateColorAsState(
        targetValue = riskColor,
        label = "riskColor"
    )

    val (statusIcon, statusText) = when (certificate.trustStatus) {
        TrustStatus.VERIFIED_PUBLIC_ROOT ->
            Pair(Icons.Default.CheckCircle, "Verified")
        TrustStatus.UNVERIFIED_SYSTEM_ROOT ->
            Pair(Icons.Default.Warning, "Unverified")
        TrustStatus.USER_INSTALLED_ROOT ->
            Pair(Icons.Default.Person, "User")
        TrustStatus.EXPIRED ->
            Pair(Icons.Default.Error, "Expired")
        TrustStatus.EXPIRING_SOON ->
            Pair(Icons.Default.Warning, "Expiring")
        TrustStatus.NOT_YET_VALID ->
            Pair(Icons.Default.Warning, "Future")
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shield/Status Icon
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = animatedColor,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Certificate Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = certificate.subjectCommonName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "Issuer: ${certificate.issuerCommonName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    // Storage location badge
                    FilterChip(
                        selected = false,
                        onClick = null,
                        label = {
                            Text(
                                if (certificate.storageLocation == StorageLocation.SYSTEM)
                                    "System" else "User"
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (certificate.storageLocation == StorageLocation.SYSTEM)
                                    Icons.Default.Security else Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = if (certificate.storageLocation == StorageLocation.SYSTEM)
                                MaterialTheme.colorScheme.surfaceVariant
                            else
                                RiskHigh.copy(alpha = 0.1f),
                            labelColor = if (certificate.storageLocation == StorageLocation.SYSTEM)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                RiskHigh
                        )
                    )

                    // Status badge
                    FilterChip(
                        selected = false,
                        onClick = null,
                        label = { Text(statusText) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = animatedColor.copy(alpha = 0.1f),
                            labelColor = animatedColor
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Fingerprint preview
            Text(
                text = certificate.sha256Fingerprint.take(8) + "...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Top)
            )
        }
    }
}
