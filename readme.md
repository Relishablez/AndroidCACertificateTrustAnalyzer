# Project Specification: Android CA Certificate Trust Analyzer

## 1. Context & Intention
The goal of this project is to build an open-source Android application (using Kotlin and Jetpack Compose) that acts as a "CA Certificate Trust Analyzer." 

Mobile operating systems ship with dozens of pre-installed root certificates, and users or third-party apps can install additional ones. This creates a risk of supply chain attacks, privacy-invasive corporate interception (MITM), and malicious user-store certificates. 

This app will scan the device's installed Certificate Authorities (CAs)—both system-level and user-installed—and cross-reference them against public root trust stores (specifically the Common CA Database / CCADB). It aims to empower the user to identify unfamiliar, untrusted, or potentially invasive certificates and make an informed decision on whether to disable or remove them.

## 2. Core Objectives
*   **Device Enumeration:** Programmatically retrieve all X.509 CA certificates installed on the Android device. The app must correctly differentiate between the System store (`/system/etc/security/cacerts` or the APEX Conscrypt module in Android 14+) and the User store (`/data/misc/user/0/cacerts-added/`).
*   **External Data Mapping:** Fetch or locally bundle the CCADB public dataset (via CCADB's CSV/JSON data mapping) to map device CAs to publicly recognized root programs (Mozilla, Google, Microsoft, Apple).
*   **Analysis & Flagging Engine:** Compare the local CAs against the CCADB dataset using SHA-256 fingerprints. 
*   **Actionable Insights:** Flag certificates that are high risk (User store) or unusual (System CAs that do not exist in the public CCADB, indicating OEM, carrier, or enterprise supply-chain roots).

## 3. Technical Requirements

### 3.1 Data Models & State Management
*   Create an X.509 wrapper data class that extracts and formats: `Subject Distinguished Name`, `Issuer`, `Valid From/To` (handling expiry states), `SHA-256 Fingerprint`, and `Storage Location` (System vs. User).
*   Implement a repository pattern with Kotlin Coroutines and StateFlow to handle the asynchronous loading and parsing of on-device certificates.

### 3.2 Certificate Retrieval Logic
*   Implement Java/Kotlin code using `KeyStore.getInstance("AndroidCAStore")` to securely retrieve the list of installed certificates.
*   Ensure the retrieval logic rigorously differentiates between system and user certificates (e.g., checking if the alias contains "user" or by verifying file paths if KeyStore aliases are insufficient).

### 3.3 CCADB Integration & Cross-Referencing
*   Write a parser for the CCADB CSV format (e.g., "All Included Root Certificate Trust Bit Settings").
*   Map the on-device certificate SHA-256 hashes against the CCADB SHA-256 hashes.
*   Categorize the UI output into three distinct buckets:
    1.  **Verified Public Roots:** System CAs found in CCADB.
    2.  **Unverified System Roots:** System CAs NOT in CCADB (OEM/Carrier specific).
    3.  **User Installed Roots:** Certificates manually added to the user store (Interception risk).

### 3.4 UI/UX (Jetpack Compose)
*   **Dashboard Screen:** A summary card showing counts of System CAs, User CAs, and "Flagged/Unverified" CAs.
*   **List View:** A searchable, filterable list (Tabs: All, System, User, Unverified). Each list item should show a shield icon indicating its trust status.
*   **Detail View:** A comprehensive screen showing the full PEM data, ASN.1 parsed fields, and a clear, human-readable explanation of why the certificate is flagged. Include a button to copy the PEM to the clipboard.

### 3.5 System Settings & Intent Hooking
*   *Constraint:* Non-root apps cannot directly delete system or user certificates.
*   *Solution:* Provide an actionable button in the Detail View that launches an `Intent` to the Android native **Trusted Credentials** settings screen (`android.settings.SECURITY_SETTINGS` or `ACTION_TRUSTED_CREDENTIALS_USER`) so the user can easily disable or remove the flagged certificate manually.

### 3.6 Logging, Caching & Error Handling
*   Implement a logging framework (e.g., Timber) for debugging X.509 parsing errors.
*   Add offline support: Cache the CCADB CSV locally (using Room or DataStore for metadata and local files) and update it via a background worker (WorkManager) when network connectivity is available.

---

## 4. Initial AI Assistant Tasks / Deliverables Requested
Please act as the lead Android Engineer for this project. To begin, provide the following:
1.  The `build.gradle.kts` dependencies required (Compose, Coroutines, WorkManager, CSV parser, Timber).
2.  The `KeyStore` retrieval utility object/class tailored for X.509 extraction.
3.  The CCADB parsing and SHA-256 cross-referencing logic.
4.  The main Jetpack Compose UI scaffold for the Dashboard, List, and Detail views.
5.  The `AndroidManifest.xml` configuration, detailing any necessary intents or permissions.

Please ensure all Kotlin code is clean, well-architected (MVVM), and includes comments explaining how the trust validation logic securely handles the X.509 data.