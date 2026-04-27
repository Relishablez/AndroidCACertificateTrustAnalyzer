package dev.cacertanalyzer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cacertanalyzer.data.model.CertificateStatistics
import dev.cacertanalyzer.ui.theme.RiskCritical
import dev.cacertanalyzer.ui.theme.RiskHigh
import dev.cacertanalyzer.ui.theme.RiskLow
import dev.cacertanalyzer.ui.theme.RiskMedium
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dashboard screen showing summary statistics of certificate analysis.
 */
@Composable
fun DashboardScreen(
    statistics: CertificateStatistics,
    onViewAllClick: () -> Unit,
    onViewSystemClick: () -> Unit,
    onViewUserClick: () -> Unit,
    onViewFlaggedClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "Certificate Trust Summary",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Overview of CA certificates installed on your device",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Summary Stats Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(
                title = "System CAs",
                value = statistics.totalSystemCerts.toString(),
                icon = Icons.Default.Security,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                onClick = onViewSystemClick
            )

            StatCard(
                title = "User Installed",
                value = statistics.userInstalledRoots.toString(),
                icon = Icons.Default.Warning,
                color = if (statistics.userInstalledRoots > 0) RiskHigh else RiskLow,
                modifier = Modifier.weight(1f),
                onClick = onViewUserClick
            )
        }

        // Risk Assessment Card
        RiskAssessmentCard(
            unverifiedCount = statistics.unverifiedSystemRoots,
            userCount = statistics.userInstalledRoots,
            expiredCount = statistics.expiredCerts,
            onViewFlagged = onViewFlaggedClick
        )

        // Breakdown Card
        BreakdownCard(statistics = statistics)

        // Actions
        FilledTonalButton(
            onClick = onViewAllClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("View All Certificates")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RiskAssessmentCard(
    unverifiedCount: Int,
    userCount: Int,
    expiredCount: Int,
    onViewFlagged: () -> Unit
) {
    val riskLevel = when {
        userCount > 0 || expiredCount > 0 -> "High Risk"
        unverifiedCount > 10 -> "Medium Risk"
        else -> "Low Risk"
    }

    val riskColor = when (riskLevel) {
        "High Risk" -> RiskCritical
        "Medium Risk" -> RiskMedium
        else -> RiskLow
    }

    val riskIcon = when (riskLevel) {
        "High Risk" -> Icons.Default.Error
        "Medium Risk" -> Icons.Default.Warning
        else -> Icons.Default.CheckCircle
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = riskColor.copy(alpha = 0.1f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = riskIcon,
                    contentDescription = null,
                    tint = riskColor
                )
                Text(
                    text = riskLevel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = riskColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val description = buildString {
                if (userCount > 0) {
                    append("⚠️ $userCount user-installed certificate(s) detected. ")
                }
                if (expiredCount > 0) {
                    append("❌ $expiredCount expired certificate(s). ")
                }
                if (unverifiedCount > 0) {
                    append("⚡ $unverifiedCount unverified system certificate(s). ")
                }
                if (userCount == 0 && expiredCount == 0 && unverifiedCount == 0) {
                    append("✅ All certificates are from verified public root programs.")
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (userCount > 0 || expiredCount > 0 || unverifiedCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onViewFlagged,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Flagged Certificates")
                }
            }
        }
    }
}

@Composable
private fun BreakdownCard(statistics: CertificateStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Detailed Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            BreakdownItem(
                label = "Verified Public Roots",
                value = statistics.verifiedPublicRoots,
                color = RiskLow
            )

            BreakdownItem(
                label = "Unverified System Roots",
                value = statistics.unverifiedSystemRoots,
                color = if (statistics.unverifiedSystemRoots > 0) RiskMedium else RiskLow
            )

            BreakdownItem(
                label = "User Installed",
                value = statistics.userInstalledRoots,
                color = if (statistics.userInstalledRoots > 0) RiskHigh else RiskLow
            )

            BreakdownItem(
                label = "Expired",
                value = statistics.expiredCerts,
                color = if (statistics.expiredCerts > 0) RiskCritical else RiskLow
            )

            BreakdownItem(
                label = "Expiring Soon (< 30 days)",
                value = statistics.expiringSoonCerts,
                color = if (statistics.expiringSoonCerts > 0) RiskMedium else RiskLow
            )
        }
    }
}

@Composable
private fun BreakdownItem(
    label: String,
    value: Int,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
