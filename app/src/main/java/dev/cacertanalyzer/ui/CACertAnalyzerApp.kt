package dev.cacertanalyzer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import dev.cacertanalyzer.data.model.CertificateInfo
import dev.cacertanalyzer.data.repository.AnalysisState
import dev.cacertanalyzer.ui.screens.CertificateDetailScreen
import dev.cacertanalyzer.ui.screens.CertificateListScreen
import dev.cacertanalyzer.ui.screens.DashboardScreen
import dev.cacertanalyzer.ui.screens.ErrorScreen
import dev.cacertanalyzer.viewmodel.CertificateTab
import dev.cacertanalyzer.viewmodel.CertificateViewModel
import dev.cacertanalyzer.viewmodel.RiskLevel
import kotlinx.coroutines.launch

/**
 * Main app scaffold for the CA Certificate Trust Analyzer.
 *
 * Manages navigation between Dashboard, List, and Detail screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CACertAnalyzerApp(viewModel: CertificateViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Collect state from ViewModel
    val analysisState by viewModel.analysisState.collectAsState()
    val selectedCertificate by viewModel.selectedCertificate.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statistics by viewModel.statistics.collectAsState()
    val filteredCertificates by viewModel.filteredCertificates.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // Handle errors
    LaunchedEffect(analysisState) {
        if (analysisState is AnalysisState.Error) {
            val error = (analysisState as AnalysisState.Error).message
            scope.launch {
                snackbarHostState.showSnackbar("Error: $error")
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                selectedCertificate = selectedCertificate,
                onNavigateBack = { viewModel.clearSelection() },
                onRefresh = { viewModel.loadCertificates(forceRefresh = true) },
                isLoading = isLoading,
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                // Show detail screen if a certificate is selected
                selectedCertificate != null -> {
                    CertificateDetailScreen(
                        certificate = selectedCertificate!!,
                        riskLevel = viewModel.getRiskLevel(selectedCertificate!!),
                        trustDescription = viewModel.getTrustStatusDescription(selectedCertificate!!),
                        onOpenSettings = {
                            val intent = viewModel.openTrustedCredentialsSettings()
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Show loading indicator on initial load
                isLoading && analysisState is AnalysisState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Show error screen
                analysisState is AnalysisState.Error -> {
                    ErrorScreen(
                        message = (analysisState as AnalysisState.Error).message,
                        onRetry = { viewModel.loadCertificates(forceRefresh = true) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Show dashboard when no search/tab selection (initial view)
                statistics != null && searchQuery.isBlank() && selectedTab == CertificateTab.ALL -> {
                    DashboardScreen(
                        statistics = statistics!!,
                        onViewAllClick = { viewModel.setTab(CertificateTab.ALL) },
                        onViewSystemClick = { viewModel.setTab(CertificateTab.SYSTEM) },
                        onViewUserClick = { viewModel.setTab(CertificateTab.USER) },
                        onViewFlaggedClick = { viewModel.setTab(CertificateTab.FLAGGED) },
                        isLoading = isLoading,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Show list view
                else -> {
                    CertificateListScreen(
                        certificates = filteredCertificates,
                        selectedTab = selectedTab,
                        onTabChange = { viewModel.setTab(it) },
                        searchQuery = searchQuery,
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        onCertificateClick = { viewModel.selectCertificate(it) },
                        getRiskLevel = { viewModel.getRiskLevel(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    selectedCertificate: CertificateInfo?,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior
) {
    TopAppBar(
        title = {
            Text(
                text = when {
                    selectedCertificate != null -> selectedCertificate.subjectCommonName
                    else -> "CA Certificate Analyzer"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (selectedCertificate != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            if (selectedCertificate == null) {
                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        },
        scrollBehavior = scrollBehavior
    )
}
