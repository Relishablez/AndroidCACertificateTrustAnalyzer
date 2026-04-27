package dev.cacertanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.cacertanalyzer.ui.CACertAnalyzerApp
import dev.cacertanalyzer.ui.theme.CACertAnalyzerTheme
import dev.cacertanalyzer.viewmodel.CertificateViewModel
import timber.log.Timber

/**
 * Main entry point for the CA Certificate Trust Analyzer application.
 *
 * Sets up the Compose UI and initializes logging.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: CertificateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.d("MainActivity created")

        setContent {
            CACertAnalyzerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CACertAnalyzerApp(viewModel = viewModel)
                }
            }
        }
    }
}
