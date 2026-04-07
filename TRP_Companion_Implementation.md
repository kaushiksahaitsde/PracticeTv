# TRP Companion App — Full Implementation Guide
> This document is written for Cursor / Claude Code.
> Follow every step exactly in order. Do not skip any step.
> All code is copy-paste ready.

---

## CONTEXT — What we are building

The TRP app runs on a FireStick TV and tracks media sessions (Spotify, YouTube etc.)
To do this, it needs a special Android permission called NotificationListenerService permission.

Problem: On Android TV / FireStick, the settings screen to grant this permission is hidden.

Solution: Build a companion Android phone app that:
1. Takes FireStick IP + Wireless Debugging pairing details from the user
2. Connects to the FireStick over WiFi using ADB
3. Grants the permission automatically
4. Shows a success message to the user

---

## PROJECT STRUCTURE — What we are creating

```
<your_project_root>/
├── app/                          ← Your existing TV app (DO NOT TOUCH)
├── core/                         ← NEW: Shared ADB logic library
│   ├── build.gradle.kts
│   └── src/main/java/com/example/mytvxml/core/
│       └── AdbManager.kt
├── companion/                    ← NEW: Phone app module
│   ├── build.gradle.kts
│   ├── src/main/AndroidManifest.xml
│   └── src/main/java/com/example/mytvxml/companion/
│       └── SetupActivity.kt
│   └── src/main/res/layout/
│       └── activity_setup.xml
└── settings.gradle.kts           ← MODIFY: Add new modules
```

---

## IMPORTANT NOTES before starting

- Package name used in this guide: `com.example.mytvxml`
- Replace this with your actual package name everywhere
- The NotificationListener service name used:
  `com.example.mytvxml/com.example.mytvxml.service.TrpNotificationListenerService`
- Replace this with your actual service class path
- Build script style: Kotlin DSL (.gradle.kts)
- No Dependency Injection used
- Minimum SDK: 26

---

## STEP 1 — Add new modules to settings.gradle.kts

Open the ROOT `settings.gradle.kts` file and add the two new modules.

Find this section (it will already have your existing app module):
```kotlin
include(":app")
```

Add the new modules below it:
```kotlin
include(":app")
include(":core")       // ADD THIS
include(":companion")  // ADD THIS
```

Also in the same file, find `dependencyResolutionManagement` block and add JitPack:
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // ADD THIS LINE
    }
}
```

---

## STEP 2 — Create the `core` module folder structure

Create these folders manually or via Android Studio (File → New → New Module → Android Library):

```
core/
└── src/
    └── main/
        └── java/
            └── com/
                └── example/
                    └── mytvxml/
                        └── core/
```

---

## STEP 3 — Create core/build.gradle.kts

Create a new file at path: `core/build.gradle.kts`

Paste this exact content:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.mytvxml.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // ADB client library — this lets our app speak ADB language natively
    api("com.github.mobile-dev-inc:dadb:1.2.7")

    // Coroutines — for running ADB commands in background (not on main thread)
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
}
```

---

## STEP 4 — Create AdbManager.kt inside core module

Create a new file at path:
`core/src/main/java/com/example/mytvxml/core/AdbManager.kt`

Paste this exact content:

```kotlin
package com.example.mytvxml.core

import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AdbManager handles all ADB communication with the FireStick.
 *
 * Think of this like a remote control that can:
 * 1. Pair with the FireStick (like connecting a new remote)
 * 2. Send commands to the FireStick (like pressing buttons)
 */
object AdbManager {

    // This is the full path of the service we want to grant permission to
    // IMPORTANT: Replace this with your actual package + service class name
    private const val NOTIFICATION_LISTENER =
        "com.example.mytvxml/com.example.mytvxml.service.TrpNotificationListenerService"

    /**
     * Step 1 — Pair with the FireStick using the pairing code
     * User gets the pairing code from their phone's Wireless Debugging screen
     *
     * @param ip           FireStick IP address (e.g. "172.16.6.153")
     * @param pairingPort  Port shown in Wireless Debugging pairing screen (e.g. 37249)
     * @param pairingCode  6-digit code shown in Wireless Debugging pairing screen
     */
    suspend fun pairDevice(
        ip: String,
        pairingPort: Int,
        pairingCode: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val keyPair = AdbKeyPair.generate()
            Dadb.pair(ip, pairingPort, pairingCode, keyPair)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Pairing failed: ${e.message}"))
        }
    }

    /**
     * Step 2 — Connect to FireStick and grant notification permission
     * This runs the ADB command that grants our app the permission
     *
     * @param ip    FireStick IP address (e.g. "172.16.6.153")
     * @param port  ADB port — default is 5555
     */
    suspend fun grantNotificationPermission(
        ip: String,
        port: Int = 5555
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val keyPair = AdbKeyPair.generate()
            val dadb = Dadb.create(ip, port, keyPair)

            // Run the permission grant command
            dadb.shell("cmd notification allow_listener $NOTIFICATION_LISTENER")

            // Verify by reading the system setting
            val verification = dadb.shell(
                "settings get secure enabled_notification_listeners"
            )

            dadb.close()

            // Check if our app appears in the permitted list
            if (verification.output.contains("com.example.mytvxml")) {
                Result.success("Permission granted successfully!")
            } else {
                Result.failure(
                    Exception("Permission not found after granting. Please try again.")
                )
            }

        } catch (e: Exception) {
            Result.failure(Exception("Connection failed: ${e.message}"))
        }
    }
}
```

---

## STEP 5 — Create the `companion` module folder structure

Create these folders manually or via Android Studio (File → New → New Module → Phone & Tablet):

```
companion/
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── example/
        │           └── mytvxml/
        │               └── companion/
        ├── res/
        │   └── layout/
        └── AndroidManifest.xml
```

---

## STEP 6 — Create companion/build.gradle.kts

Create a new file at path: `companion/build.gradle.kts`

Paste this exact content:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.mytvxml.companion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mytvxml.companion"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Our shared core module with AdbManager
    implementation(project(":core"))

    // Standard Android dependencies
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
```

---

## STEP 7 — Create companion/src/main/AndroidManifest.xml

Create a new file at path: `companion/src/main/AndroidManifest.xml`

Paste this exact content:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Permissions needed to communicate over WiFi with FireStick -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="TRP Setup"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.AppCompat.Light.DarkActionBar">

        <!-- This is the only screen in the companion app -->
        <activity
            android:name=".SetupActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
```

---

## STEP 8 — Create the Setup Screen Layout

Create a new file at path:
`companion/src/main/res/layout/activity_setup.xml`

Paste this exact content:

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#F5F5F5">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="24dp">

        <!-- App Title -->
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="TRP Setup"
            android:textSize="28sp"
            android:textStyle="bold"
            android:textColor="#1A1A1A"
            android:layout_marginBottom="8dp"/>

        <!-- Subtitle -->
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Connect your phone to FireStick to enable notification tracking"
            android:textSize="14sp"
            android:textColor="#666666"
            android:layout_marginBottom="32dp"/>

        <!-- Instruction Card — tells user what to do on the TV -->
        <androidx.cardview.widget.CardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="24dp"
            app:cardCornerRadius="12dp"
            app:cardElevation="2dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="📺  On your FireStick TV:"
                    android:textSize="14sp"
                    android:textStyle="bold"
                    android:textColor="#1A1A1A"
                    android:layout_marginBottom="8dp"/>

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="1. Settings → My Fire TV → Developer Options\n2. Turn ON  ADB Debugging\n3. Turn ON  Network Debugging\n4. Note the IP Address shown on screen"
                    android:textSize="13sp"
                    android:textColor="#444444"
                    android:lineSpacingExtra="4dp"/>

            </LinearLayout>
        </androidx.cardview.widget.CardView>

        <!-- FireStick IP Input -->
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="FireStick IP Address"
            android:textSize="13sp"
            android:textColor="#444444"
            android:layout_marginBottom="6dp"/>

        <com.google.android.material.textfield.TextInputLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
            style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox">

            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/etFirestickIp"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="e.g. 172.16.6.153"
                android:inputType="text"
                android:imeOptions="actionNext"/>

        </com.google.android.material.textfield.TextInputLayout>

        <!-- Pairing Code Input -->
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Pairing Code  (from Wireless Debugging on your phone)"
            android:textSize="13sp"
            android:textColor="#444444"
            android:layout_marginBottom="6dp"/>

        <com.google.android.material.textfield.TextInputLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
            style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox">

            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/etPairingCode"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="e.g. 123456"
                android:inputType="number"
                android:imeOptions="actionNext"/>

        </com.google.android.material.textfield.TextInputLayout>

        <!-- Pairing Port Input -->
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Pairing Port  (shown next to pairing code)"
            android:textSize="13sp"
            android:textColor="#444444"
            android:layout_marginBottom="6dp"/>

        <com.google.android.material.textfield.TextInputLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="32dp"
            style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox">

            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/etPairingPort"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="e.g. 37249"
                android:inputType="number"
                android:imeOptions="actionDone"/>

        </com.google.android.material.textfield.TextInputLayout>

        <!-- Main Action Button -->
        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnEnable"
            android:layout_width="match_parent"
            android:layout_height="56dp"
            android:text="Enable Notification Permission"
            android:textSize="16sp"
            android:layout_marginBottom="16dp"/>

        <!-- Status Message shown during and after process -->
        <TextView
            android:id="@+id/tvStatus"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text=""
            android:textSize="14sp"
            android:gravity="center"
            android:layout_marginBottom="16dp"/>

        <!-- Loading spinner shown while processing -->
        <ProgressBar
            android:id="@+id/progressBar"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:visibility="gone"/>

    </LinearLayout>
</ScrollView>
```

---

## STEP 9 — Create SetupActivity.kt

Create a new file at path:
`companion/src/main/java/com/example/mytvxml/companion/SetupActivity.kt`

Paste this exact content:

```kotlin
package com.example.mytvxml.companion

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.mytvxml.core.AdbManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

/**
 * SetupActivity — The one and only screen in the companion app.
 *
 * What it does:
 * 1. Shows input fields for FireStick IP, pairing code, pairing port
 * 2. On button tap, calls AdbManager to pair + grant permission
 * 3. Shows success or error message to user
 */
class SetupActivity : AppCompatActivity() {

    // UI elements
    private lateinit var etFirestickIp: TextInputEditText
    private lateinit var etPairingCode: TextInputEditText
    private lateinit var etPairingPort: TextInputEditText
    private lateinit var btnEnable: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        // Connect XML views to Kotlin variables
        etFirestickIp = findViewById(R.id.etFirestickIp)
        etPairingCode = findViewById(R.id.etPairingCode)
        etPairingPort = findViewById(R.id.etPairingPort)
        btnEnable     = findViewById(R.id.btnEnable)
        tvStatus      = findViewById(R.id.tvStatus)
        progressBar   = findViewById(R.id.progressBar)

        // When user taps the button
        btnEnable.setOnClickListener {
            val ip          = etFirestickIp.text.toString().trim()
            val pairingCode = etPairingCode.text.toString().trim()
            val pairingPort = etPairingPort.text.toString().trim()

            // Validate — all fields must be filled
            if (ip.isEmpty() || pairingCode.isEmpty() || pairingPort.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Start the permission granting process
            enablePermission(ip, pairingCode, pairingPort.toInt())
        }
    }

    /**
     * Main function — runs the 2-step ADB process:
     * Step 1: Pair with the device
     * Step 2: Grant the notification permission
     */
    private fun enablePermission(ip: String, pairingCode: String, pairingPort: Int) {

        // Show loading state
        setLoading(true)
        updateStatus("🔗 Pairing with your FireStick...", "#FF8C00")

        lifecycleScope.launch {

            // --- STEP 1: PAIR ---
            val pairResult = AdbManager.pairDevice(ip, pairingPort, pairingCode)

            if (pairResult.isFailure) {
                setLoading(false)
                updateStatus(
                    "❌ Pairing failed: ${pairResult.exceptionOrNull()?.message}",
                    "#D32F2F"
                )
                return@launch
            }

            // --- STEP 2: GRANT PERMISSION ---
            updateStatus("⚙️ Granting notification permission...", "#FF8C00")

            val grantResult = AdbManager.grantNotificationPermission(ip)

            setLoading(false)

            if (grantResult.isSuccess) {
                // SUCCESS
                updateStatus(
                    "✅ Permission granted! You can now start tracking.",
                    "#2E7D32"
                )
                Toast.makeText(
                    this@SetupActivity,
                    "Done! TRP is ready to track media.",
                    Toast.LENGTH_LONG
                ).show()

                // Disable button so user doesn't run it twice
                btnEnable.isEnabled = false
                btnEnable.text = "Permission Enabled ✓"

                // Save success state so we don't show this screen again
                getSharedPreferences("trp_prefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("permission_granted", true)
                    .apply()

            } else {
                // FAILURE
                updateStatus(
                    "❌ Failed: ${grantResult.exceptionOrNull()?.message}",
                    "#D32F2F"
                )
            }
        }
    }

    // Shows or hides the loading spinner and enables/disables button
    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnEnable.isEnabled = !isLoading
    }

    // Updates the status text with a color
    private fun updateStatus(message: String, colorHex: String) {
        tvStatus.text = message
        tvStatus.setTextColor(android.graphics.Color.parseColor(colorHex))
    }
}
```

---

## STEP 10 — Check permission on TV app startup

In your existing TV app's main Activity or Fragment, add this check at startup.

If permission is not yet granted, show a message telling user to run the companion app.

Add this helper function wherever your tracking starts:

```kotlin
/**
 * Returns true if TRP already has notification listener permission.
 * Call this before starting media tracking.
 */
fun isNotificationPermissionGranted(context: Context): Boolean {
    val result = android.provider.Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    return result?.contains("com.example.mytvxml") == true
}
```

Then in your tracking start logic:

```kotlin
if (!isNotificationPermissionGranted(this)) {
    // Show a message on TV screen
    Toast.makeText(
        this,
        "Please run the TRP Setup app on your phone first",
        Toast.LENGTH_LONG
    ).show()
    return
}
// Else — start tracking normally
```

---

## STEP 11 — Sync and Build

1. In Android Studio, click **File → Sync Project with Gradle Files**
2. Wait for sync to complete
3. To run the companion app on your phone:
   - In the top run configuration dropdown, select **companion**
   - Connect your phone via USB or WiFi
   - Click Run ▶️
4. To run the TV app on FireStick (unchanged):
   - In the top run configuration dropdown, select **app**
   - Click Run ▶️

---

## HOW TO USE — User flow

### What the user does:

```
1. Install companion APK on their Android phone

2. On their phone:
   Settings → Developer Options → Wireless Debugging → ON
   Tap "Pair device with pairing code"
   Note: IP Address, Port, 6-digit Pairing Code

3. Open TRP Setup companion app on phone

4. Enter:
   - FireStick IP address  (shown in FireStick Network Settings)
   - Pairing Code          (from phone Wireless Debugging screen)
   - Pairing Port          (from phone Wireless Debugging screen)

5. Tap "Enable Notification Permission"

6. Wait 5-10 seconds

7. See "✅ Permission granted!"

8. Open TRP app on FireStick — tracking works!
```

---

## TROUBLESHOOTING

| Error | Fix |
|-------|-----|
| Pairing failed | Make sure Wireless Debugging is ON on phone. Re-generate pairing code — it expires quickly |
| Connection failed | Make sure phone and FireStick are on SAME WiFi network |
| Permission not found after granting | Make sure ADB Debugging + Network Debugging is ON on FireStick |
| Build error: dadb not found | Make sure JitPack is added to settings.gradle.kts |
| Build error: alias not found | Check if your libs.versions.toml has android.library and kotlin.android plugins |

---

## FILES SUMMARY — Everything you need to create

| File Path | Action |
|-----------|--------|
| `settings.gradle.kts` | MODIFY — add :core and :companion includes + JitPack |
| `core/build.gradle.kts` | CREATE |
| `core/src/main/java/com/example/mytvxml/core/AdbManager.kt` | CREATE |
| `companion/build.gradle.kts` | CREATE |
| `companion/src/main/AndroidManifest.xml` | CREATE |
| `companion/src/main/res/layout/activity_setup.xml` | CREATE |
| `companion/src/main/java/com/example/mytvxml/companion/SetupActivity.kt` | CREATE |
| Your existing TV app main Activity | MODIFY — add permission check |

---

*End of implementation guide*
