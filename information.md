# Android CA Certificate Trust Analyzer - Developer Documentation

## Project Overview

This Android application analyzes the device's installed CA certificates and cross-references them against the Common CA Database (CCADB) to identify potentially untrusted, OEM-specific, or malicious user-installed certificates.

---

## Project Structure

```
107_AndroidCACertificateTrustAnalyzer/
├── app/
│   ├── build.gradle.kts              # Module dependencies
│   └── src/main/
│       ├── AndroidManifest.xml         # App manifest with permissions
│       ├── java/dev/cacertanalyzer/
│       │   ├── MainActivity.kt         # Entry point with Compose setup
│       │   ├── CertAnalyzerApplication.kt  # Application + WorkManager init
│       │   ├── data/
│       │   │   ├── model/             # Data classes
│       │   │   │   ├── CertificateInfo.kt
│       │   │   │   ├── CCADBRecord.kt
│       │   │   │   └── AnalysisResult.kt
│       │   │   ├── local/             # Local data sources
│       │   │   │   ├── CertificateRetriever.kt
│       │   │   │   └── CCADBCache.kt
│       │   │   ├── remote/            # Network operations
│       │   │   │   ├── CCADBService.kt
│       │   │   │   └── CCADBParser.kt
│       │   │   └── repository/        # Repository pattern
│       │   │       └── CertificateRepository.kt
│       │   ├── ui/
│       │   │   ├── CACertAnalyzerApp.kt
│       │   │   ├── screens/
│       │   │   │   ├── DashboardScreen.kt
│       │   │   │   ├── CertificateListScreen.kt
│       │   │   │   ├── CertificateDetailScreen.kt
│       │   │   │   └── ErrorScreen.kt
│       │   │   └── theme/
│       │   │       ├── Color.kt
│       │   │       ├── Theme.kt
│       │   │       └── Type.kt
│       │   ├── viewmodel/
│       │   │   └── CertificateViewModel.kt
│       │   └── worker/
│       │       └── CCADBSyncWorker.kt
│       └── res/                       # Android resources
├── build.gradle.kts                   # Project-level gradle
├── settings.gradle.kts
└── gradle.properties
```

---

## Architecture

### MVVM (Model-View-ViewModel)

```
UI Layer (Compose Screens)
    ↕
ViewModel (CertificateViewModel) - StateFlow for reactive state
    ↕
Repository (CertificateRepository) - Single source of truth
    ↕
Data Sources:
    ├─ Local: CertificateRetriever, CCADBCache (dev.cacertanalyzer.data.local)
    ├─ Remote: CCADBService, CCADBParser (dev.cacertanalyzer.data.remote)
    └─ CrossRef: CCADBCrossReferenceService (dev.cacertanalyzer.data.remote)
```

---

## Key Classes and Functions

### 1. CertificateRetriever (`data/local/CertificateRetriever.kt`)

**Purpose**: Enumerates X.509 certificates from the Android system.

**Critical Function**: `retrieveAllCertificates()`
```kotlin
// Uses AndroidCAStore KeyStore to list all certificates
val keyStore = KeyStore.getInstance("AndroidCAStore")
keyStore.load(null, null)
```

**Important**: Differentiates system vs user certificates using:
- Alias patterns (contains "user")
- File system checks (requires root for accurate detection)
- Self-signed detection heuristic

**Security Note**: Only reads certificate metadata and public keys. Does NOT access private keys.

---

### 2. CCADBParser (`data/remote/CCADBParser.kt`)

**Purpose**: Parses CCADB CSV format with dynamic column mapping.

**Key Function**: `parse(csvContent: String): List<CCADBRecord>`
- Handles variable CSV headers
- Extracts trust bits (Websites, Email, Code Signing)
- Normalizes SHA-256 fingerprints

**CCADB URL**: `https://ccadb-public.secure.force.com/ccadb/AllIncludedRootCertificateTrustBitSettingsPEMCSV`

---

### 3. CertificateRepository (`data/repository/CertificateRepository.kt`)

**Purpose**: Central data management with offline support.

**Critical Functions**:

| Function | Description |
|----------|-------------|
| `analyzeCertificates()` | Full analysis workflow: fetch device certs → load/fetch CCADB → cross-reference → categorize |
| `categorizeCertificates()` | Separates into Verified/Unverified/User/Expired buckets |
| `fetchAndCacheCCADBData()` | Downloads from network, caches to local storage |
| `loadCachedCCADBData()` | Reads from cache for offline use |
| `isCacheStale()` | 7-day cache expiration check |

**StateFlow Properties**: `analysisState`, `analysisResult`, `isLoading`, `lastSyncTimestamp`

---

### 4. CertificateViewModel (`viewmodel/CertificateViewModel.kt`)

**Purpose**: UI state management and business logic.

**Key Functions**:

| Function | Description |
|----------|-------------|
| `loadCertificates(forceRefresh)` | Trigger certificate analysis |
| `setTab(tab)` | Filter by category (All, System, User, Unverified, Flagged) |
| `setSearchQuery(query)` | Search by name, fingerprint, or serial |
| `openTrustedCredentialsSettings()` | Creates Intent to `android.settings.SECURITY_SETTINGS` |
| `getRiskLevel(cert)` | Maps TrustStatus to RiskLevel for UI coloring |
| `getTrustStatusDescription(cert)` | Human-readable risk explanations |

**Flows**:
- `filteredCertificates` - combines search + tab filter
- `statistics` - aggregated counts for dashboard

---

### 5. CertificateInfo (`data/model/CertificateInfo.kt`)

**Purpose**: X.509 wrapper data class with computed properties.

**Key Properties**:
- `alias` - KeyStore identifier
- `sha256Fingerprint` - Primary matching key for CCADB
- `storageLocation` - System vs User
- `trustStatus` - Computed during analysis
- `rootPrograms` - List of trusting root programs
- `pemEncoded` - Full PEM format for export

**TrustStatus Enum**:
- `VERIFIED_PUBLIC_ROOT` - Found in CCADB
- `UNVERIFIED_SYSTEM_ROOT` - System cert not in CCADB (OEM/Carrier)
- `USER_INSTALLED_ROOT` - User store (high risk)
- `EXPIRED` / `EXPIRING_SOON` / `NOT_YET_VALID`

---

## Certificate Trust Validation Flow

```
1. CertificateRetriever.getAllCertificates()
   └─→ Load from AndroidCAStore

2. For each certificate:
   a. Determine StorageLocation (System vs User)
   b. Compute SHA-256 fingerprint
   
3. CCADB Cross-Reference:
   a. Load cached CCADB or fetch from network
   b. Build lookup map: fingerprint → CCADBRecord
   c. Match device certs against map

4. Categorization:
   ├─ System + CCADB match → VERIFIED_PUBLIC_ROOT
   ├─ System + no match → UNVERIFIED_SYSTEM_ROOT (OEM/Carrier)
   ├─ User store → USER_INSTALLED_ROOT
   └─ Expiry check → EXPIRED / EXPIRING_SOON

5. UI Display:
   ├─ Dashboard: Risk assessment + statistics
   ├─ List: Filterable by category
   └─ Detail: Full PEM + trust explanation + settings link
```

---

## Important Implementation Details

### CCADB Fingerprint Matching

Fingerprints are **normalized** before comparison:
```kotlin
fun normalizeFingerprint(fingerprint: String): String {
    return fingerprint.replace(":", "")
        .replace(" ", "")
        .replace("-", "")
        .uppercase()
}
```

This ensures matches regardless of formatting (colon-separated vs raw hex).

### Certificate Storage Location Detection

Android doesn't provide a direct API to distinguish system vs user certificates. The app uses heuristics:

1. **Alias pattern**: `"user"` in alias indicates user store
2. **File system**: Checks `/data/misc/user/0/cacerts-added/` (requires root)
3. **Default**: Non-user aliases assumed system

**Limitation**: Without root, some system certs may be misclassified. The user store detection is reliable.

### Offline Support

CCADB data is cached using:
- **DataStore**: Metadata (last sync timestamp)
- **File**: Raw CSV in `context.filesDir/ccadb_cache/`

Cache refresh: 7 days (configurable in `isCacheStale()`)

Background sync: WorkManager runs weekly when charging + network available.

---

## Security Considerations

### Safe X.509 Handling
- Uses standard Java `java.security.cert.X509Certificate` APIs
- No custom certificate validation (avoids bypassing Android's validation)
- Only reads public certificate data

### Network Security
- CCADB download uses HTTPS with certificate pinning (implicit via OkHttp)
- No sensitive data transmitted (only downloads public root list)

### User Data Protection
- Cache excluded from cloud backup (`data_extraction_rules.xml`)
- No analytics or telemetry

---

## How to Build and Install

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34
- Android device/emulator (API 26+ / Android 8.0+)

### Build APK

**Option 1: Android Studio (Recommended)**
1. Open project in Android Studio
2. Sync project with Gradle files (File → Sync Project with Gradle Files)
3. Build → Generate Signed Bundle / APK → APK
4. Create or select keystore
5. Output: `app/release/app-release.apk`

**Option 2: Command Line**
```bash
# Navigate to project root
cd 107_AndroidCACertificateTrustAnalyzer

# Build release APK
./gradlew :app:assembleRelease

# APK location:
# app/build/outputs/apk/release/app-release-unsigned.apk

# To sign (create keystore first):
keytool -genkey -v -keystore my.keystore -keyalg RSA -keysize 2048 -validity 10000 -alias app

# Sign APK
apksigner sign --ks my.keystore --out app-signed.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

### Install on Android Device

**Option 1: ADB (Developer Mode)**
```bash
# Enable developer mode on device:
# Settings → About Phone → Tap "Build Number" 7 times
# Settings → Developer Options → Enable USB Debugging

# Connect device via USB
adb devices  # Verify connection

# Install APK
adb install app/build/outputs/apk/release/app-release.apk

# Or for debug version:
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Option 2: Transfer and Install**
1. Transfer APK to device (USB, email, cloud storage)
2. On device: Enable "Install from Unknown Sources"
   - Settings → Security → Unknown Sources (or Install Unknown Apps per app)
3. Open APK file with file manager
4. Tap Install

**Option 3: Android Studio Direct Run**
1. Connect device via USB (enable USB debugging)
2. Select device in Android Studio toolbar
3. Click Run (green triangle)
4. APK installs and launches automatically

---

## APK Locations After Build

| Build Type | Path |
|------------|------|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` |
| Release (unsigned) | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| Release (signed) | `app/build/outputs/apk/release/app-release.apk` |

---

## Troubleshooting

### "App not installed" error
- Uninstall previous version first (signatures may differ)
- Check `minSdk` (26) vs device API level

### No certificates showing
- Check `AndroidManifest.xml` permissions (INTERNET, NETWORK_STATE)
- Verify CCADB download succeeded (check logs with `adb logcat | grep CCADB`)

### Certificate storage detection incorrect
- Without root, some system certs may show as unverified
- This is expected behavior due to Android security restrictions

---

## Maintenance Guidelines

### Adding New Trust Programs
Edit `CCADBRecord.kt`:
1. Add new status column constant (e.g., `COLUMN_CUSTOM_STATUS`)
2. Update `mapColumns()` to recognize the header
3. Update `toRootPrograms()` to include new program

### Modifying CCADB URL
Edit `CCADBService.kt`:
```kotlin
const val CCADB_CSV_URL = "https://your-new-url.com/data.csv"
```

### Cache Duration
Edit `CertificateRepository.kt`:
```kotlin
private fun isCacheStale(): Boolean {
    val customDuration = TimeUnit.DAYS.toMillis(1) // Change to 1 day
    return (System.currentTimeMillis() - lastSync) > customDuration
}
```

### Adding New Certificate Fields
Edit `CertificateInfo.kt`:
1. Add property with lazy computation
2. Update UI screens to display

---

## Dependencies Summary

| Dependency | Purpose |
|------------|---------|
| Jetpack Compose (Material3) | Modern declarative UI |
| Kotlin Coroutines | Async operations |
| WorkManager | Background CCADB sync |
| Room | Local database (reserved for future use) |
| DataStore | Key-value cache for sync metadata |
| OpenCSV | CCADB CSV parsing |
| Timber | Structured logging |
| OkHttp | Network operations |

---

## License & Attribution

This project uses:
- **CCADB Data**: Common CA Database by Mozilla, CC-BY 4.0
- **Android CAStore**: Android Open Source Project

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2024 | Initial release with CCADB integration, certificate analysis |
