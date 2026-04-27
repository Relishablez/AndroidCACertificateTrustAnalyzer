package dev.cacertanalyzer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cacertanalyzer.data.model.CertificateInfo
import dev.cacertanalyzer.data.model.StorageLocation
import dev.cacertanalyzer.ui.theme.RiskCritical
import dev.cacertanalyzer.ui.theme.RiskHigh
import dev.cacertanalyzer.ui.theme.RiskLow
import dev.cacertanalyzer.ui.theme.RiskMedium
import dev.cacertanalyzer.viewmodel.RiskLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Detail screen showing comprehensive information about a single certificate.
 */
@Composable
fun CertificateDetailScreen(
    certificate: CertificateInfo,
    riskLevel: RiskLevel,
    trustDescription: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showPemDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Trust Status Header
        TrustStatusHeader(
            certificate = certificate,
            riskLevel = riskLevel,
            trustDescription = trustDescription
        )

        // Subject Information
        InfoSection(title = "Subject") {
            InfoRow("Common Name", certificate.subjectCommonName)
            InfoRow("Distinguished Name", certificate.subjectDN)
        }

        // Issuer Information
        InfoSection(title = "Issuer") {
            InfoRow("Common Name", certificate.issuerCommonName)
            InfoRow("Distinguished Name", certificate.issuerDN)
        }

        // Validity Period
        InfoSection(title = "Validity Period") {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            InfoRow("Valid From", dateFormat.format(certificate.validFrom))
            InfoRow("Valid To", dateFormat.format(certificate.validTo))

            if (!certificate.isValid()) {
                val now = Date()
                val daysUntil = ((certificate.validTo.time - now.time) / (1000 * 60 * 60 * 24)).toInt()
                val expiryText = if (daysUntil < 0) {
                    "Expired ${-daysUntil} days ago"
                } else {
                    "Expires in $daysUntil days"
                }
                InfoRow("Status", expiryText, isWarning = true)
            }
        }

        // Technical Details
        InfoSection(title = "Technical Details") {
            InfoRow("Serial Number", certificate.serialNumber.uppercase())
            InfoRow("SHA-256 Fingerprint", certificate.sha256Fingerprint)
            InfoRow("SHA-1 Fingerprint", certificate.sha1Fingerprint)
            InfoRow("Signature Algorithm", certificate.signatureAlgorithm)
            InfoRow("Key Algorithm", "${certificate.keyAlgorithm} (${certificate.keySize} bits)")
            InfoRow("Self-Signed", if (certificate.isSelfSigned()) "Yes" else "No")
            InfoRow("Storage Location", certificate.storageLocation.name)
        }

        // Root Programs (if verified)
        if (certificate.rootPrograms.isNotEmpty()) {
            InfoSection(title = "Trusted By") {
                certificate.rootPrograms.forEach { program ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = RiskLow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${program.programName} - " +
                                    if (program.serverAuthTrusted) "Websites" else "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Actions
        Spacer(modifier = Modifier.height(8.dp))

        // Copy PEM Button
        OutlinedButton(
            onClick = { showPemDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("View PEM Data")
        }

        // Open System Settings (only for user certificates or if removal might be needed)
        if (certificate.storageLocation == StorageLocation.USER) {
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = RiskHigh
                )
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Trusted Credentials Settings")
            }

            Text(
                text = "You can remove this certificate from the system Trusted Credentials settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            FilledTonalButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Trusted Credentials")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // PEM Dialog
    if (showPemDialog) {
        PemDialog(
            pemContent = certificate.pemEncoded,
            onDismiss = { showPemDialog = false },
            onCopy = {
                copyToClipboard(context, certificate.pemEncoded, "Certificate PEM")
            }
        )
    }
}

@Composable
private fun TrustStatusHeader(
    certificate: CertificateInfo,
    riskLevel: RiskLevel,
    trustDescription: String
) {
    val (backgroundColor, icon, title) = when (riskLevel) {
        RiskLevel.LOW -> Triple(
            RiskLow.copy(alpha = 0.1f),
            Icons.Default.CheckCircle,
            "Verified Public Root"
        )
        RiskLevel.MEDIUM -> Triple(
            RiskMedium.copy(alpha = 0.1f),
            Icons.Default.Warning,
            when (certificate.trustStatus) {
                dev.cacertanalyzer.data.model.TrustStatus.EXPIRING_SOON -> "Expiring Soon"
                else -> "Unverified System Root"
            }
        )
        RiskLevel.HIGH -> Triple(
            RiskHigh.copy(alpha = 0.1f),
            Icons.Default.Person,
            "User Installed Certificate"
        )
        RiskLevel.CRITICAL -> Triple(
            RiskCritical.copy(alpha = 0.1f),
            Icons.Default.Error,
            "Expired Certificate"
        )
    }

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = backgroundColor
        ),
        modifier = Modifier.fillMaxWidth()
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
                tint = when (riskLevel) {
                    RiskLevel.LOW -> RiskLow
                    RiskLevel.MEDIUM -> RiskMedium
                    RiskLevel.HIGH -> RiskHigh
                    RiskLevel.CRITICAL -> RiskCritical
                },
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = trustDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun InfoSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            content()
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    isWarning: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isWarning) RiskHigh else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isWarning) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PemDialog(
    pemContent: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Certificate PEM") },
        text = {
            Column {
                Text(
                    text = "Copy this PEM data to share or analyze externally:",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = pemContent,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private fun copyToClipboard(context: Context, content: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, content)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
